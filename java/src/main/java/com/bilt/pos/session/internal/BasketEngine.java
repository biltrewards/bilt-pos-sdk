/*
 *    ____  _ _ _
 *   | __ )(_) | |_
 *   |  _ \| | | __|
 *   | |_) | | | |_
 *   |____/|_|_|\__|
 *
 *   Bilt POS SDK
 *
 *   Internal API — subject to change without notice.
 */
package com.bilt.pos.session.internal;

import com.bilt.pos.session.basket.Basket;
import com.bilt.pos.session.basket.BasketItem;
import com.bilt.pos.session.basket.BasketLineItem;
import com.bilt.pos.session.basket.BasketMutation;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.UUID;

/**
 * Mutable basket state and tax computation. Produces immutable
 * {@link Basket} snapshots.
 *
 * <p>Tax rules (highest specificity wins):</p>
 * <ol>
 *   <li>an explicit line {@code taxAmount} is used as-is;</li>
 *   <li>else a line {@code taxRate} yields {@code originalTotal × rate};</li>
 *   <li>else the line contributes zero tax;</li>
 *   <li>{@code taxTotal} is the sum of line amounts unless a basket-level
 *       override was set via {@link #setTaxTotal}.</li>
 * </ol>
 *
 * <p>Lines are keyed by SKU <em>and</em> direction: a credit line (return,
 * trade-in) never upserts into a sale line of the same SKU, so a basket may
 * carry both — like two lines on a paper receipt. Tax values are magnitudes;
 * a credit line's totals and tax are negated at snapshot time. A
 * {@code creditCart} engine (a refund cart) puts every added item on the
 * credit side regardless of the item's own flag.</p>
 *
 * <p>Callers must guard access with the session lock; this class performs no
 * synchronization. Money is computed at scale 2, {@code HALF_UP}.</p>
 */
public final class BasketEngine implements BasketMutation {

    private static final int MONEY_SCALE = 2;
    private static final RoundingMode ROUNDING = RoundingMode.HALF_UP;

    private static final class Line {
        final String itemId;
        final String sku;
        final String description;
        final String category;
        final BigDecimal unitPrice;
        final boolean credit;
        final Map<String, String> metadata;
        int quantity;
        BigDecimal taxRate;
        BigDecimal taxAmount;

        Line(String itemId, BasketItem item, boolean credit) {
            this.itemId = itemId;
            this.sku = item.getSku();
            this.description = item.getDescription();
            this.category = item.getCategory();
            this.unitPrice = item.getUnitPrice();
            this.credit = credit;
            this.metadata = item.getMetadata();
            this.quantity = item.getQuantity();
            this.taxRate = item.getTaxRate();
            this.taxAmount = item.getTaxAmount();
        }

        Line(Line other) {
            this.itemId = other.itemId;
            this.sku = other.sku;
            this.description = other.description;
            this.category = other.category;
            this.unitPrice = other.unitPrice;
            this.credit = other.credit;
            this.metadata = other.metadata;
            this.quantity = other.quantity;
            this.taxRate = other.taxRate;
            this.taxAmount = other.taxAmount;
        }
    }

    private final String cartId = UUID.randomUUID().toString();
    // keyed by (SKU, direction) — see key(); iteration order is insertion order
    private final Map<String, Line> lines = new LinkedHashMap<>();
    private final boolean creditCart;
    private int nextItemId = 1;
    private BigDecimal taxTotalOverride;

    public BasketEngine() {
        this(false);
    }

    /**
     * @param creditCart when {@code true}, every added item lands on the
     *        credit side — the mode of a refund cart, whose lines all
     *        subtract
     */
    public BasketEngine(boolean creditCart) {
        this.creditCart = creditCart;
    }

    /**
     * Applies a batch of mutations atomically: if any of them throws, the
     * pre-batch state is restored and the exception rethrown — the basket
     * is never left partially updated.
     */
    public void mutateAtomically(Consumer<BasketMutation> mutation) {
        Map<String, Line> savedLines = new LinkedHashMap<>();
        for (Map.Entry<String, Line> entry : lines.entrySet()) {
            savedLines.put(entry.getKey(), new Line(entry.getValue()));
        }
        int savedNextItemId = nextItemId;
        BigDecimal savedTaxTotalOverride = taxTotalOverride;
        try {
            mutation.accept(this);
        } catch (RuntimeException e) {
            lines.clear();
            lines.putAll(savedLines);
            nextItemId = savedNextItemId;
            taxTotalOverride = savedTaxTotalOverride;
            throw e;
        }
    }

    // ─── Mutations ───

    @Override
    public BasketEngine addItem(BasketItem item) {
        return addItem(item, null);
    }

    @Override
    public BasketEngine addItem(BasketItem item, String explicitItemId) {
        Objects.requireNonNull(item, "item");
        boolean credit = creditCart || item.isCredit();
        Line existing = lines.get(key(item.getSku(), credit));
        if (existing != null) {
            if (explicitItemId != null && !explicitItemId.equals(existing.itemId)) {
                throw new IllegalArgumentException("SKU " + item.getSku()
                        + " already exists with itemId " + existing.itemId);
            }
            if (existing.unitPrice.compareTo(item.getUnitPrice()) != 0) {
                throw new IllegalArgumentException("SKU " + item.getSku()
                        + " already in basket with unitPrice " + existing.unitPrice
                        + "; cannot upsert with unitPrice " + item.getUnitPrice());
            }
            existing.quantity += item.getQuantity();
            if (item.getTaxRate() != null) {
                existing.taxRate = item.getTaxRate();
                if (item.getTaxAmount() == null) {
                    // same last-write-wins rule as setTaxRate: a rate-only
                    // upsert makes the line rate-based again — a stale fixed
                    // amount would otherwise outrank the new rate forever
                    existing.taxAmount = null;
                }
            }
            if (item.getTaxAmount() != null) {
                existing.taxAmount = item.getTaxAmount();
            }
            return this;
        }
        String itemId = explicitItemId != null ? explicitItemId : String.valueOf(nextItemId);
        validateItemId(itemId);
        for (Line line : lines.values()) {
            if (line.itemId.equals(itemId)) {
                throw new IllegalArgumentException("itemId " + itemId + " is already in use");
            }
        }
        lines.put(key(item.getSku(), credit), new Line(itemId, item, credit));
        // keep generated ids ahead of any explicit numeric id
        nextItemId = Math.max(nextItemId, Integer.parseInt(itemId)) + 1;
        return this;
    }

    @Override
    public BasketEngine removeItem(String itemId) {
        Line line = requireByItemId(itemId);
        lines.remove(key(line.sku, line.credit));
        return this;
    }

    @Override
    public BasketEngine removeItemBySku(String sku) {
        Line line = requireBySku(sku);
        lines.remove(key(line.sku, line.credit));
        return this;
    }

    @Override
    public BasketEngine updateItemQuantity(String itemId, int quantity) {
        return setQuantity(requireByItemId(itemId), quantity);
    }

    @Override
    public BasketEngine updateItemQuantityBySku(String sku, int quantity) {
        return setQuantity(requireBySku(sku), quantity);
    }

    private BasketEngine setQuantity(Line line, int quantity) {
        if (quantity < 0) {
            throw new IllegalArgumentException("quantity must not be negative");
        }
        if (quantity == 0) {
            lines.remove(key(line.sku, line.credit));
        } else {
            line.quantity = quantity;
        }
        return this;
    }

    @Override
    public BasketEngine setTaxRate(String itemId, BigDecimal rate) {
        return applyTaxRate(requireByItemId(itemId), rate);
    }

    @Override
    public BasketEngine setTaxRateBySku(String sku, BigDecimal rate) {
        return applyTaxRate(requireBySku(sku), rate);
    }

    private BasketEngine applyTaxRate(Line line, BigDecimal rate) {
        line.taxRate = requireNonNegative(rate, "taxRate");
        // last write wins: an explicit fixed amount would otherwise take
        // precedence forever, with no way back to rate-based tax
        line.taxAmount = null;
        return this;
    }

    @Override
    public BasketEngine setTaxAmount(String itemId, BigDecimal amount) {
        requireByItemId(itemId).taxAmount = requireNonNegative(amount, "taxAmount");
        return this;
    }

    @Override
    public BasketEngine setTaxAmountBySku(String sku, BigDecimal amount) {
        requireBySku(sku).taxAmount = requireNonNegative(amount, "taxAmount");
        return this;
    }

    @Override
    public BasketEngine setTaxTotal(BigDecimal amount) {
        // a negative override could push the grand total to zero or below,
        // letting a checkout complete without collecting any tender
        this.taxTotalOverride = requireNonNegative(amount, "taxTotal");
        return this;
    }

    /** Empties the basket (a refund cart is consumed by its refund). */
    public void clear() {
        lines.clear();
        taxTotalOverride = null;
    }

    /** {@code null} clears the value; a present value must not be negative. */
    private static BigDecimal requireNonNegative(BigDecimal value, String what) {
        if (value != null && value.signum() < 0) {
            throw new IllegalArgumentException(what + " must not be negative");
        }
        return value;
    }

    // ─── Queries ───

    public boolean isEmpty() {
        return lines.isEmpty();
    }

    /** Produces an immutable snapshot of the current basket. */
    public Basket snapshot() {
        List<BasketLineItem> items = new ArrayList<>(lines.size());
        BigDecimal originalTotal = BigDecimal.ZERO;
        BigDecimal taxSum = BigDecimal.ZERO;
        for (Line line : lines.values()) {
            BigDecimal lineTotal = line.unitPrice
                    .multiply(BigDecimal.valueOf(line.quantity))
                    .setScale(MONEY_SCALE, ROUNDING);
            BigDecimal lineTax = lineTax(line, lineTotal);
            if (line.credit) {
                lineTotal = lineTotal.negate();
                lineTax = lineTax.negate();
            }
            originalTotal = originalTotal.add(lineTotal);
            taxSum = taxSum.add(lineTax);
            items.add(BasketLineItem.builder()
                    .itemId(line.itemId)
                    .sku(line.sku)
                    .description(line.description)
                    .category(line.category)
                    .quantity(line.quantity)
                    .unitPrice(line.unitPrice)
                    .credit(line.credit)
                    .originalTotal(lineTotal)
                    .adjustedTotal(lineTotal)
                    .taxRate(line.taxRate)
                    .taxAmount(lineTax)
                    .metadata(line.metadata)
                    .build());
        }
        BigDecimal taxTotal;
        if (taxTotalOverride != null) {
            taxTotal = taxTotalOverride.setScale(MONEY_SCALE, ROUNDING);
            if (creditCart) {
                // tax values are magnitudes everywhere (see setTaxAmount);
                // on a credit cart the override follows the cart's
                // direction like every line amount does
                taxTotal = taxTotal.negate();
            }
        } else {
            taxTotal = taxSum;
        }
        return Basket.builder()
                .cartId(cartId)
                .items(items)
                .originalTotal(originalTotal)
                .taxTotal(taxTotal)
                .grandTotal(originalTotal.add(taxTotal))
                .build();
    }

    // ─── Internals ───

    private static String key(String sku, boolean credit) {
        // NUL cannot appear in a real SKU, so the credit-side key can
        // never collide with a sale-side SKU
        return credit ? sku + "\0" : sku;
    }

    private Line requireByItemId(String itemId) {
        Objects.requireNonNull(itemId, "itemId");
        for (Line line : lines.values()) {
            if (line.itemId.equals(itemId)) {
                return line;
            }
        }
        throw new IllegalArgumentException("no basket item with itemId " + itemId);
    }

    /**
     * When a SKU is present in both directions the sale line wins — credit
     * lines are rarer and always deliberate, so they are addressed by
     * itemId. With one direction present (all lines of a refund cart are
     * credits) the SKU alone is unambiguous.
     */
    private Line requireBySku(String sku) {
        Objects.requireNonNull(sku, "sku");
        Line line = lines.get(key(sku, false));
        if (line == null) {
            line = lines.get(key(sku, true));
        }
        if (line == null) {
            throw new IllegalArgumentException("no basket item with SKU " + sku);
        }
        return line;
    }

    private static BigDecimal lineTax(Line line, BigDecimal lineTotal) {
        if (line.taxAmount != null) {
            return line.taxAmount.setScale(MONEY_SCALE, ROUNDING);
        }
        if (line.taxRate != null) {
            return lineTotal.multiply(line.taxRate).setScale(MONEY_SCALE, ROUNDING);
        }
        return BigDecimal.ZERO.setScale(MONEY_SCALE, ROUNDING);
    }

    private static void validateItemId(String itemId) {
        try {
            if (Integer.parseInt(itemId) < 1) {
                throw new IllegalArgumentException("itemId must be a positive integer string");
            }
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(
                    "itemId must be a positive integer string, got: " + itemId, e);
        }
    }
}
