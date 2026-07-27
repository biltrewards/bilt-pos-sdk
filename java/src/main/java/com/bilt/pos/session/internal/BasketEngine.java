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
        final Map<String, String> metadata;
        int quantity;
        BigDecimal taxRate;
        BigDecimal taxAmount;

        Line(String itemId, BasketItem item) {
            this.itemId = itemId;
            this.sku = item.getSku();
            this.description = item.getDescription();
            this.category = item.getCategory();
            this.unitPrice = item.getUnitPrice();
            this.metadata = item.getMetadata();
            this.quantity = item.getQuantity();
            this.taxRate = item.getTaxRate();
            this.taxAmount = item.getTaxAmount();
        }
    }

    private final String cartId = UUID.randomUUID().toString();
    private final Map<String, Line> linesBySku = new LinkedHashMap<>();
    private int nextItemId = 1;
    private BigDecimal taxTotalOverride;

    // ─── Mutations ───

    @Override
    public BasketEngine addItem(BasketItem item) {
        return addItem(item, null);
    }

    @Override
    public BasketEngine addItem(BasketItem item, String explicitItemId) {
        Objects.requireNonNull(item, "item");
        Line existing = linesBySku.get(item.getSku());
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
        for (Line line : linesBySku.values()) {
            if (line.itemId.equals(itemId)) {
                throw new IllegalArgumentException("itemId " + itemId + " is already in use");
            }
        }
        linesBySku.put(item.getSku(), new Line(itemId, item));
        // keep generated ids ahead of any explicit numeric id
        nextItemId = Math.max(nextItemId, Integer.parseInt(itemId)) + 1;
        return this;
    }

    @Override
    public BasketEngine removeItem(String itemId) {
        linesBySku.remove(requireByItemId(itemId).sku);
        return this;
    }

    @Override
    public BasketEngine removeItemBySku(String sku) {
        requireBySku(sku);
        linesBySku.remove(sku);
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
            linesBySku.remove(line.sku);
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
        line.taxRate = rate;
        // last write wins: an explicit fixed amount would otherwise take
        // precedence forever, with no way back to rate-based tax
        line.taxAmount = null;
        return this;
    }

    @Override
    public BasketEngine setTaxAmount(String itemId, BigDecimal amount) {
        requireByItemId(itemId).taxAmount = amount;
        return this;
    }

    @Override
    public BasketEngine setTaxAmountBySku(String sku, BigDecimal amount) {
        requireBySku(sku).taxAmount = amount;
        return this;
    }

    @Override
    public BasketEngine setTaxTotal(BigDecimal amount) {
        this.taxTotalOverride = amount;
        return this;
    }

    // ─── Queries ───

    public boolean isEmpty() {
        return linesBySku.isEmpty();
    }

    /** Produces an immutable snapshot of the current basket. */
    public Basket snapshot() {
        List<BasketLineItem> items = new ArrayList<>(linesBySku.size());
        BigDecimal originalTotal = BigDecimal.ZERO;
        BigDecimal taxSum = BigDecimal.ZERO;
        for (Line line : linesBySku.values()) {
            BigDecimal lineTotal = line.unitPrice
                    .multiply(BigDecimal.valueOf(line.quantity))
                    .setScale(MONEY_SCALE, ROUNDING);
            BigDecimal lineTax = lineTax(line, lineTotal);
            originalTotal = originalTotal.add(lineTotal);
            taxSum = taxSum.add(lineTax);
            items.add(BasketLineItem.builder()
                    .itemId(line.itemId)
                    .sku(line.sku)
                    .description(line.description)
                    .category(line.category)
                    .quantity(line.quantity)
                    .unitPrice(line.unitPrice)
                    .originalTotal(lineTotal)
                    .adjustedTotal(lineTotal)
                    .taxRate(line.taxRate)
                    .taxAmount(lineTax)
                    .metadata(line.metadata)
                    .build());
        }
        BigDecimal taxTotal = taxTotalOverride != null
                ? taxTotalOverride.setScale(MONEY_SCALE, ROUNDING)
                : taxSum;
        return Basket.builder()
                .cartId(cartId)
                .items(items)
                .originalTotal(originalTotal)
                .taxTotal(taxTotal)
                .grandTotal(originalTotal.add(taxTotal))
                .build();
    }

    // ─── Internals ───

    private Line requireByItemId(String itemId) {
        Objects.requireNonNull(itemId, "itemId");
        for (Line line : linesBySku.values()) {
            if (line.itemId.equals(itemId)) {
                return line;
            }
        }
        throw new IllegalArgumentException("no basket item with itemId " + itemId);
    }

    private Line requireBySku(String sku) {
        Objects.requireNonNull(sku, "sku");
        Line line = linesBySku.get(sku);
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
