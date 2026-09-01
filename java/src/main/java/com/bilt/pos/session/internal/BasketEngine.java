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
import com.bilt.pos.session.basket.BasketDiscount;
import com.bilt.pos.session.basket.BasketItem;
import com.bilt.pos.session.basket.BasketItemType;
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
 *   <li>else a line {@code taxRate} yields the post-discount subtotal × rate;</li>
 *   <li>else the line contributes zero tax;</li>
 *   <li>{@code taxTotal} is the sum of line amounts unless a basket-level
 *       override was set via {@link #setTaxTotal}.</li>
 * </ol>
 *
 * <p>Unreferenced lines are keyed by SKU and type: returns and credits
 * never upsert into a sale line of the same SKU. Referenced lines are keyed by
 * their register reference and remain distinct even when several lines share a
 * SKU. Tax values are magnitudes; return and credit totals and tax are negated
 * at snapshot time.</p>
 *
 * <p>Callers must guard access with the session lock; this class performs no
 * synchronization. Money is computed at scale 2, {@code HALF_UP}.</p>
 */
public final class BasketEngine implements BasketMutation {

    private static final int MONEY_SCALE = 2;
    private static final RoundingMode ROUNDING = RoundingMode.HALF_UP;

    private static final class Line {
        final String itemId;
        final String reference;
        final String sku;
        final String description;
        final String category;
        final BigDecimal unitPrice;
        final List<BasketDiscount> discounts;
        final BasketItemType type;
        final Map<String, String> metadata;
        int quantity;
        BigDecimal taxRate;
        BigDecimal taxAmount;

        Line(String itemId, BasketItem item) {
            this.itemId = itemId;
            this.reference = item.getReference();
            this.sku = item.getSku();
            this.description = item.getDescription();
            this.category = item.getCategory();
            this.unitPrice = item.getUnitPrice();
            this.discounts = new ArrayList<>(item.getDiscounts());
            this.type = item.getType();
            this.metadata = item.getMetadata();
            this.quantity = item.getQuantity();
            this.taxRate = item.getTaxRate();
            this.taxAmount = item.getTaxAmount();
        }

        Line(Line other) {
            this.itemId = other.itemId;
            this.reference = other.reference;
            this.sku = other.sku;
            this.description = other.description;
            this.category = other.category;
            this.unitPrice = other.unitPrice;
            this.discounts = new ArrayList<>(other.discounts);
            this.type = other.type;
            this.metadata = other.metadata;
            this.quantity = other.quantity;
            this.taxRate = other.taxRate;
            this.taxAmount = other.taxAmount;
        }
    }

    private final String cartId = UUID.randomUUID().toString();
    // keyed by commercial identity — see key(); iteration order is insertion order
    private final Map<String, Line> lines = new LinkedHashMap<>();
    private int nextItemId = 1;
    private BigDecimal taxTotalOverride;

    public BasketEngine() {
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
        String key = key(item);
        Line existing = lines.get(key);
        requireUniqueReference(item.getReference(), existing);
        if (existing != null) {
            if (item.getReference() != null) {
                throw new IllegalArgumentException("basket reference "
                        + item.getReference() + " is already in the basket");
            }
            if (explicitItemId != null && !explicitItemId.equals(existing.itemId)) {
                throw new IllegalArgumentException("SKU " + item.getSku()
                        + " already exists with itemId " + existing.itemId);
            }
            if (existing.unitPrice.compareTo(item.getUnitPrice()) != 0) {
                throw new IllegalArgumentException("SKU " + item.getSku()
                        + " already in basket with unitPrice " + existing.unitPrice
                        + "; cannot upsert with unitPrice " + item.getUnitPrice());
            }
            int newQuantity = existing.quantity + item.getQuantity();
            List<BasketDiscount> newDiscounts = new ArrayList<>(existing.discounts);
            newDiscounts.addAll(item.getDiscounts());
            if (discountTotal(newDiscounts).compareTo(existing.unitPrice
                    .multiply(BigDecimal.valueOf(newQuantity))) > 0) {
                throw new IllegalArgumentException("discounts exceed the upserted line value for "
                        + item.getSku());
            }
            existing.quantity = newQuantity;
            existing.discounts.addAll(item.getDiscounts());
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
        lines.put(key, new Line(itemId, item));
        // keep generated ids ahead of any explicit numeric id
        nextItemId = Math.max(nextItemId, Integer.parseInt(itemId)) + 1;
        return this;
    }

    @Override
    public BasketEngine removeItem(String itemId) {
        Line line = requireByItemId(itemId);
        lines.remove(key(line));
        return this;
    }

    @Override
    public BasketEngine removeItemBySku(String sku) {
        Line line = requireBySku(sku);
        lines.remove(key(line));
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

    @Override
    public BasketEngine setDiscounts(String itemId, List<BasketDiscount> discounts) {
        return applyDiscounts(requireByItemId(itemId), discounts);
    }

    @Override
    public BasketEngine setDiscountsBySku(String sku, List<BasketDiscount> discounts) {
        return applyDiscounts(requireBySku(sku), discounts);
    }

    private BasketEngine applyDiscounts(Line line, List<BasketDiscount> discounts) {
        Objects.requireNonNull(discounts, "discounts");
        List<BasketDiscount> copy = new ArrayList<>(discounts.size());
        for (BasketDiscount discount : discounts) {
            copy.add(Objects.requireNonNull(discount, "discount"));
        }
        BigDecimal gross = line.unitPrice.multiply(BigDecimal.valueOf(line.quantity));
        if (discountTotal(copy).compareTo(gross) > 0) {
            throw new IllegalArgumentException("discounts exceed the line value for " + line.sku);
        }
        line.discounts.clear();
        line.discounts.addAll(copy);
        return this;
    }

    private BasketEngine setQuantity(Line line, int quantity) {
        if (quantity < 0) {
            throw new IllegalArgumentException("quantity must not be negative");
        }
        if (quantity == 0) {
            lines.remove(key(line));
        } else {
            if (discountTotal(line.discounts).compareTo(line.unitPrice
                    .multiply(BigDecimal.valueOf(quantity))) > 0) {
                throw new IllegalArgumentException("quantity would reduce the line below its "
                        + "register-applied discounts");
            }
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
        Line line = requireByItemId(itemId);
        line.taxAmount = requireNonNegative(amount, "taxAmount");
        return this;
    }

    @Override
    public BasketEngine setTaxAmountBySku(String sku, BigDecimal amount) {
        Line line = requireBySku(sku);
        line.taxAmount = requireNonNegative(amount, "taxAmount");
        return this;
    }

    @Override
    public BasketEngine setTaxTotal(BigDecimal amount) {
        // a negative override could push the grand total to zero or below,
        // letting a checkout complete without collecting any tender
        this.taxTotalOverride = requireNonNegative(amount, "taxTotal");
        return this;
    }

    /** Empties the basket. */
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
        BigDecimal discountTotal = BigDecimal.ZERO;
        BigDecimal subtotal = BigDecimal.ZERO;
        BigDecimal taxSum = BigDecimal.ZERO;
        for (Line line : lines.values()) {
            BigDecimal lineTotal = line.unitPrice
                    .multiply(BigDecimal.valueOf(line.quantity))
                    .setScale(MONEY_SCALE, ROUNDING);
            BigDecimal lineDiscount = discountTotal(line.discounts)
                    .setScale(MONEY_SCALE, ROUNDING);
            BigDecimal lineSubtotal = lineTotal.subtract(lineDiscount);
            BigDecimal lineTax = lineTax(line, lineSubtotal);
            if (line.type != BasketItemType.SALE) {
                lineTotal = lineTotal.negate();
                lineDiscount = lineDiscount.negate();
                lineSubtotal = lineSubtotal.negate();
                lineTax = lineTax.negate();
            }
            originalTotal = originalTotal.add(lineTotal);
            discountTotal = discountTotal.add(lineDiscount);
            subtotal = subtotal.add(lineSubtotal);
            taxSum = taxSum.add(lineTax);
            items.add(BasketLineItem.builder()
                    .itemId(line.itemId)
                    .reference(line.reference)
                    .sku(line.sku)
                    .description(line.description)
                    .category(line.category)
                    .quantity(line.quantity)
                    .unitPrice(line.unitPrice)
                    .discounts(line.discounts)
                    .discountTotal(lineDiscount)
                    .subtotal(lineSubtotal)
                    .type(line.type)
                    .originalTotal(lineTotal)
                    .adjustedTotal(lineSubtotal)
                    .taxRate(line.taxRate)
                    .taxAmount(lineTax)
                    .metadata(line.metadata)
                    .build());
        }
        BigDecimal taxTotal;
        if (taxTotalOverride != null) {
            taxTotal = taxTotalOverride.setScale(MONEY_SCALE, ROUNDING);
            if (!lines.isEmpty() && lines.values().stream()
                    .noneMatch(line -> line.type == BasketItemType.SALE)) {
                // Tax inputs are magnitudes. An all-return basket carries
                // that override in the same negative direction as its lines.
                taxTotal = taxTotal.negate();
            }
        } else {
            taxTotal = taxSum;
        }
        return Basket.builder()
                .cartId(cartId)
                .items(items)
                .originalTotal(originalTotal)
                .discountTotal(discountTotal)
                .subtotal(subtotal)
                .taxTotal(taxTotal)
                .grandTotal(subtotal.add(taxTotal))
                .build();
    }

    // ─── Internals ───

    private static String key(BasketItem item) {
        if (item.getReference() != null) {
            return "reference\0" + item.getReference();
        }
        return key(item.getSku(), item.getType());
    }

    private static String key(Line line) {
        if (line.reference != null) {
            return "reference\0" + line.reference;
        }
        return key(line.sku, line.type);
    }

    private static String key(String sku, BasketItemType type) {
        switch (type) {
            case SALE:
                return "sale\0" + sku;
            case RETURN:
                return "return\0" + sku;
            case CREDIT:
                return "credit\0" + sku;
            default:
                throw new IllegalArgumentException("unsupported basket item type " + type);
        }
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
     * A unique sale line wins when a SKU spans item types. Multiple sale
     * lines, or multiple non-sale lines without a sale, require itemId.
     */
    private Line requireBySku(String sku) {
        Objects.requireNonNull(sku, "sku");
        Line sale = null;
        Line nonSale = null;
        boolean multipleNonSales = false;
        for (Line candidate : lines.values()) {
            if (!candidate.sku.equals(sku)) {
                continue;
            }
            if (candidate.type == BasketItemType.SALE) {
                if (sale != null) {
                    throw ambiguousSku(sku);
                }
                sale = candidate;
            } else if (nonSale == null) {
                nonSale = candidate;
            } else {
                multipleNonSales = true;
            }
        }
        if (sale != null) {
            return sale;
        }
        if (multipleNonSales) {
            throw ambiguousSku(sku);
        }
        if (nonSale == null) {
            throw new IllegalArgumentException("no basket item with SKU " + sku);
        }
        return nonSale;
    }

    private static IllegalArgumentException ambiguousSku(String sku) {
        return new IllegalArgumentException("more than one basket item has SKU " + sku
                + "; use itemId to address a specific line");
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

    private static BigDecimal discountTotal(List<BasketDiscount> discounts) {
        BigDecimal total = BigDecimal.ZERO;
        for (BasketDiscount discount : discounts) {
            total = total.add(discount.getAmount());
        }
        return total;
    }

    private void requireUniqueReference(String reference, Line sameLine) {
        if (reference == null) {
            return;
        }
        for (Line line : lines.values()) {
            if (line != sameLine && reference.equals(line.reference)) {
                throw new IllegalArgumentException("basket reference " + reference
                        + " is already in use");
            }
        }
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
