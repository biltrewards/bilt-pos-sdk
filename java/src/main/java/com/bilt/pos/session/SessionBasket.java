/*
 *    ____  _ _ _
 *   | __ )(_) | |_
 *   |  _ \| | | __|
 *   | |_) | | | |_
 *   |____/|_|_|\__|
 *
 *   Bilt POS SDK
 */
package com.bilt.pos.session;

import com.bilt.pos.session.basket.Basket;
import com.bilt.pos.session.basket.BasketItem;
import com.bilt.pos.session.basket.BasketMutation;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * The basket surface of a checkout session: item and tax mutations, batch
 * edits, and immutable snapshots. Credit items represent returns in the
 * same basket as sale items.
 *
 * <p>Every mutation is applied atomically, returning the updated snapshot
 * synchronously; when the session has automatic display enabled it also
 * enqueues an asynchronous, conflated customer display refresh showing
 * the itemised basket. The owning session decides when mutations are
 * allowed: a basket frozen by in-flight money movement, already consumed by
 * settlement, or owned by an ended session rejects them with
 * {@code IllegalStateException}. Call {@link #clear()} after settlement to
 * start another basket in the same session.</p>
 *
 * <pre>{@code
 * session.basket().addItem(
 *         BasketItem.of("KRK-CNDL-LRG-VAN", "Large Vanilla Candle", 2, "24.99"));
 * session.basket().setTaxRateBySku("KRK-CNDL-LRG-VAN", new BigDecimal("0.08875"));
 * Basket basket = session.basket().snapshot();
 * }</pre>
 *
 * <p>Like the session that owns it, the basket is intended for use from a
 * single register thread.</p>
 */
public final class SessionBasket {

    /**
     * The owning session's side of the basket: it gates and applies
     * mutations under its own safety rules (in-flight checks, atomicity,
     * display refresh) and produces snapshots under its lock.
     */
    interface Host {

        /** Applies the mutation batch and returns the updated snapshot. */
        Basket mutate(Consumer<BasketMutation> mutation);

        /** An immutable snapshot of the current basket. */
        Basket snapshot();

        /** Clears the current basket and starts a fresh one. */
        Basket clear();
    }

    private final Host host;

    SessionBasket(Host host) {
        this.host = host;
    }

    /** An immutable snapshot of the current basket. */
    public Basket snapshot() {
        return host.snapshot();
    }

    /**
     * Clears all items and tax, starts a fresh basket with a new cart ID,
     * and clears the stored-value card selected for split tender.
     *
     * <p>This is the explicit transaction boundary for a session that runs
     * more than one settlement. A settled basket cannot be charged again;
     * clear it before ringing the next one.</p>
     *
     * @return the new empty basket snapshot
     * @throws IllegalStateException if money movement, settlement recovery,
     *         or a partially completed same-session void is still in
     *         progress, or the session has ended
     */
    public Basket clear() {
        return host.clear();
    }

    // ─── Items ───

    /**
     * Adds an item. When the SKU is already in the basket, its quantity is
     * incremented (upsert).
     *
     * @return the updated basket snapshot
     * @throws IllegalStateException if the basket is frozen (payment in
     *         progress) or the session has ended
     */
    public Basket addItem(BasketItem item) {
        Objects.requireNonNull(item, "item");
        return host.mutate(basket -> basket.addItem(item));
    }

    /** Adds an item with an explicit item ID (numeric string, new SKUs only). */
    public Basket addItem(BasketItem item, String itemId) {
        Objects.requireNonNull(item, "item");
        Objects.requireNonNull(itemId, "itemId");
        return host.mutate(basket -> basket.addItem(item, itemId));
    }

    /** Removes the line with the given session-assigned item ID. */
    public Basket removeItem(String itemId) {
        Objects.requireNonNull(itemId, "itemId");
        return host.mutate(basket -> basket.removeItem(itemId));
    }

    /** Removes the line with the given SKU. */
    public Basket removeItemBySku(String sku) {
        Objects.requireNonNull(sku, "sku");
        return host.mutate(basket -> basket.removeItemBySku(sku));
    }

    /** Sets an absolute quantity; {@code 0} removes the line. */
    public Basket updateItemQuantity(String itemId, int quantity) {
        Objects.requireNonNull(itemId, "itemId");
        return host.mutate(basket -> basket.updateItemQuantity(itemId, quantity));
    }

    /** Sets an absolute quantity by SKU; {@code 0} removes the line. */
    public Basket updateItemQuantityBySku(String sku, int quantity) {
        Objects.requireNonNull(sku, "sku");
        return host.mutate(basket -> basket.updateItemQuantityBySku(sku, quantity));
    }

    /**
     * Applies a batch of basket mutations atomically, with a single
     * display update for the whole batch.
     */
    public Basket mutate(Consumer<BasketMutation> mutation) {
        Objects.requireNonNull(mutation, "mutation");
        return host.mutate(mutation);
    }

    // ─── Tax ───

    /** Sets the tax rate on a line ({@code taxAmount = adjustedTotal × rate});
     * clears any explicit fixed tax amount previously set on it. */
    public Basket setTaxRate(String itemId, BigDecimal rate) {
        Objects.requireNonNull(itemId, "itemId");
        Objects.requireNonNull(rate, "rate");
        return host.mutate(basket -> basket.setTaxRate(itemId, rate));
    }

    /** Sets the tax rate on a line by SKU. */
    public Basket setTaxRateBySku(String sku, BigDecimal rate) {
        Objects.requireNonNull(sku, "sku");
        Objects.requireNonNull(rate, "rate");
        return host.mutate(basket -> basket.setTaxRateBySku(sku, rate));
    }

    /** Sets a fixed tax amount on a line, overriding any rate. */
    public Basket setTaxAmount(String itemId, BigDecimal amount) {
        Objects.requireNonNull(itemId, "itemId");
        Objects.requireNonNull(amount, "amount");
        return host.mutate(basket -> basket.setTaxAmount(itemId, amount));
    }

    /** Sets a fixed tax amount on a line by SKU. */
    public Basket setTaxAmountBySku(String sku, BigDecimal amount) {
        Objects.requireNonNull(sku, "sku");
        Objects.requireNonNull(amount, "amount");
        return host.mutate(basket -> basket.setTaxAmountBySku(sku, amount));
    }

    /**
     * Overrides the basket's total tax; passing {@code null} restores
     * item-level computation. Like every tax value the override is a
     * magnitude; credit-side basket engines subtract it with their returned
     * lines.
     */
    public Basket setTaxTotal(BigDecimal amount) {
        return host.mutate(basket -> basket.setTaxTotal(amount));
    }
}
