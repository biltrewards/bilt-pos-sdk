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
import com.bilt.pos.session.basket.BasketChange;
import com.bilt.pos.session.basket.BasketDiscount;
import com.bilt.pos.session.basket.BasketItem;
import com.bilt.pos.session.basket.BasketMutation;
import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * The basket surface of a shopper session: item and tax mutations, batch edits, whole-basket
 * replacement, and immutable snapshots. Sale, return, and register-credit items can coexist in one
 * basket.
 *
 * <p>There is one basket and three ways to update it. They are different updaters over the same
 * engine, not different concepts: every one of them applies atomically, returns the updated
 * snapshot synchronously, and ends in exactly one {@link BasketChange} notification (which, on a
 * terminal session with automatic display enabled, enqueues one asynchronous, conflated customer
 * display refresh).
 *
 * <p><b>Incremental</b> ({@link BasketChange.Source#INCREMENTAL}): one call per register action,
 * for a POS that rings items as the cashier scans them.
 *
 * <pre>{@code
 * session.basket().addItem(
 *     BasketItem.sale("KRK-CNDL-LRG-VAN", "Large Vanilla Candle", 2, new BigDecimal("24.99")));
 * session.basket().updateItemQuantityBySku("KRK-CNDL-LRG-VAN", 3);
 * session.basket().setTaxRateBySku("KRK-CNDL-LRG-VAN", new BigDecimal("0.08875"));
 * }</pre>
 *
 * <p><b>Batched</b> ({@link BasketChange.Source#BATCH}): several mutations applied as one atomic
 * change, for a register action that touches more than one line.
 *
 * <pre>{@code
 * session.basket().mutate(m -> m
 *     .addItem(BasketItem.sale("KRK-FRAME-5X7-BLK", "5x7 Black Frame", 1, new BigDecimal("14.99")))
 *     .removeItemBySku("KRK-CNDL-LRG-VAN")
 *     .setTaxTotal(new BigDecimal("1.33")));
 * }</pre>
 *
 * <p><b>Snapshot</b> ({@link BasketChange.Source#REPLACE}): the whole basket at once, for a POS
 * that owns its own cart and would rather push its current state after every change than translate
 * each edit into incremental calls. The session diffs the snapshot against the current basket, so
 * downstream consumers still learn what changed.
 *
 * <pre>{@code
 * List<BasketItem> items =
 *     pos.cart().lines().stream().map(Mapping::toBasketItem).collect(Collectors.toList());
 * session.basket().replace(items);
 * // or, with a Basket the register assembled or received earlier:
 * session.basket().replace(snapshot);
 * }</pre>
 *
 * <p>The owning session decides when updates are allowed: a basket frozen by in-flight money
 * movement, already consumed by settlement, or owned by an ended session rejects them with {@code
 * IllegalStateException}. Call {@link #clear()} after settlement to start another basket in the
 * same session; {@link #replace(Basket)} does that implicitly on a consumed basket.
 *
 * <p>Like the session that owns it, the basket is intended for use from a single register thread.
 */
public final class SessionBasket {

  /**
   * The owning session's side of the basket: it gates and applies mutations under its own safety
   * rules (in-flight checks, atomicity, change notification) and produces snapshots under its lock.
   */
  interface Host {

    /** Applies the mutation batch and returns the updated snapshot. */
    Basket mutate(Consumer<BasketMutation> mutation, BasketChange.Source source);

    /** Replaces the whole basket with the snapshot and returns the updated snapshot. */
    Basket replace(Basket snapshot);

    /** Replaces the whole basket with the items and returns the updated snapshot. */
    Basket replace(List<BasketItem> items);

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
   * Clears all items and tax, starts a fresh basket with a new cart ID, and clears the stored-value
   * card selected for split tender.
   *
   * <p>This is the explicit transaction boundary for a session that runs more than one settlement.
   * A settled basket cannot be charged again; clear it before ringing the next one. Clearing never
   * abandons financial recovery. When recovery cannot be completed, call {@link
   * TerminalShopperSession#forceEnd(String)} and start a new session rather than reusing this
   * basket.
   *
   * @return the new empty basket snapshot
   * @throws IllegalStateException if money movement, settlement recovery, or a partially completed
   *     same-session void is still in progress, or the session has ended
   */
  public Basket clear() {
    return host.clear();
  }

  // ─── Items ───

  /**
   * Adds an item. When the SKU is already in the basket, its quantity is incremented (upsert).
   *
   * @return the updated basket snapshot
   * @throws IllegalStateException if the basket is frozen (payment in progress) or the session has
   *     ended
   */
  public Basket addItem(BasketItem item) {
    Objects.requireNonNull(item, "item");
    return mutateOne(basket -> basket.addItem(item));
  }

  /** Adds an item with an explicit item ID (numeric string, new SKUs only). */
  public Basket addItem(BasketItem item, String itemId) {
    Objects.requireNonNull(item, "item");
    Objects.requireNonNull(itemId, "itemId");
    return mutateOne(basket -> basket.addItem(item, itemId));
  }

  /** Removes the line with the given session-assigned item ID. */
  public Basket removeItem(String itemId) {
    Objects.requireNonNull(itemId, "itemId");
    return mutateOne(basket -> basket.removeItem(itemId));
  }

  /** Removes the line with the given SKU. */
  public Basket removeItemBySku(String sku) {
    Objects.requireNonNull(sku, "sku");
    return mutateOne(basket -> basket.removeItemBySku(sku));
  }

  /** Sets an absolute quantity; {@code 0} removes the line. */
  public Basket updateItemQuantity(String itemId, int quantity) {
    Objects.requireNonNull(itemId, "itemId");
    return mutateOne(basket -> basket.updateItemQuantity(itemId, quantity));
  }

  /** Sets an absolute quantity by SKU; {@code 0} removes the line. */
  public Basket updateItemQuantityBySku(String sku, int quantity) {
    Objects.requireNonNull(sku, "sku");
    return mutateOne(basket -> basket.updateItemQuantityBySku(sku, quantity));
  }

  /** Replaces the register-applied discounts on a line; an empty list clears them. */
  public Basket setDiscounts(String itemId, List<BasketDiscount> discounts) {
    Objects.requireNonNull(itemId, "itemId");
    Objects.requireNonNull(discounts, "discounts");
    return mutateOne(basket -> basket.setDiscounts(itemId, discounts));
  }

  /** Replaces the register-applied discounts on a line by SKU. */
  public Basket setDiscountsBySku(String sku, List<BasketDiscount> discounts) {
    Objects.requireNonNull(sku, "sku");
    Objects.requireNonNull(discounts, "discounts");
    return mutateOne(basket -> basket.setDiscountsBySku(sku, discounts));
  }

  /**
   * Applies a batch of basket mutations atomically, with a single display update for the whole
   * batch.
   */
  public Basket mutate(Consumer<BasketMutation> mutation) {
    Objects.requireNonNull(mutation, "mutation");
    return host.mutate(mutation, BasketChange.Source.BATCH);
  }

  // ─── Replacement ───

  /**
   * Replaces the whole basket with a snapshot: the session basket becomes equal to the snapshot's
   * lines, discounts, taxes and tax total, and the change is reported as one {@link
   * BasketChange.Source#REPLACE} diff against the previous basket. A snapshot equal to the current
   * basket produces an empty diff and no notification, and so no display push.
   *
   * <p>Lines are paired with the current basket the way the basket keys them, by {@code reference}
   * when a line has one and otherwise by SKU and item type among the unreferenced lines; a paired
   * line keeps its item id, so register references into the basket stay stable, while new lines get
   * new ids. Each snapshot line needs a SKU, description, unit price and a quantity of at least
   * one, like a {@link BasketItem}. Line tax follows the snapshot line (a {@code taxRate} makes it
   * rate-based, a disagreeing or rate-less non-zero {@code taxAmount} fixes it), and the snapshot's
   * {@code taxTotal} becomes a basket-level override only when it differs from the sum of its
   * lines' tax amounts.
   *
   * <p>On a terminal session whose basket has been consumed by a successful settlement, {@code
   * replace} does not fail the way an incremental mutation does: it starts a fresh cart with a new
   * cart id exactly as {@link #clear()} would (same guards, and the split-tender stored-value
   * selection is dropped) and then installs the snapshot, reporting every line as added.
   *
   * @return the updated basket snapshot
   * @throws IllegalArgumentException if the snapshot holds two unreferenced lines with the same SKU
   *     and type or two lines with the same reference, or a line fails item validation; the basket
   *     is left untouched
   * @throws IllegalStateException if the basket is frozen (payment in progress) or the session has
   *     ended, or, for a consumed basket, under the {@link #clear()} guards
   */
  public Basket replace(Basket snapshot) {
    Objects.requireNonNull(snapshot, "snapshot");
    return host.replace(snapshot);
  }

  /**
   * Replaces the whole basket with register items, the common shape of a POS-owned cart: the basket
   * becomes exactly these lines, paired with the current ones and reported as in {@link
   * #replace(Basket)}. Tax comes from the items' own {@code taxRate}/{@code taxAmount}; a
   * basket-level {@link #setTaxTotal} override already in place is kept when no item carries tax
   * and dropped when any does, so a register that taxes the whole basket sets the total once and
   * keeps replacing items.
   *
   * @return the updated basket snapshot
   * @throws IllegalArgumentException if two items share a reference, or two unreferenced items
   *     share a SKU and type; the basket is left untouched
   * @throws IllegalStateException under the same rules as {@link #replace(Basket)}
   */
  public Basket replace(List<BasketItem> items) {
    Objects.requireNonNull(items, "items");
    return host.replace(items);
  }

  private Basket mutateOne(Consumer<BasketMutation> mutation) {
    return host.mutate(mutation, BasketChange.Source.INCREMENTAL);
  }

  // ─── Tax ───

  /**
   * Sets the tax rate on a line ({@code taxAmount = subtotal × rate}); clears any explicit fixed
   * tax amount previously set on it.
   */
  public Basket setTaxRate(String itemId, BigDecimal rate) {
    Objects.requireNonNull(itemId, "itemId");
    Objects.requireNonNull(rate, "rate");
    return mutateOne(basket -> basket.setTaxRate(itemId, rate));
  }

  /** Sets the tax rate on a line by SKU. */
  public Basket setTaxRateBySku(String sku, BigDecimal rate) {
    Objects.requireNonNull(sku, "sku");
    Objects.requireNonNull(rate, "rate");
    return mutateOne(basket -> basket.setTaxRateBySku(sku, rate));
  }

  /** Sets a fixed tax amount on a line, overriding any rate. */
  public Basket setTaxAmount(String itemId, BigDecimal amount) {
    Objects.requireNonNull(itemId, "itemId");
    Objects.requireNonNull(amount, "amount");
    return mutateOne(basket -> basket.setTaxAmount(itemId, amount));
  }

  /** Sets a fixed tax amount on a line by SKU. */
  public Basket setTaxAmountBySku(String sku, BigDecimal amount) {
    Objects.requireNonNull(sku, "sku");
    Objects.requireNonNull(amount, "amount");
    return mutateOne(basket -> basket.setTaxAmountBySku(sku, amount));
  }

  /**
   * Overrides the basket's total tax; passing {@code null} restores item-level computation. Like
   * every tax value the override is a magnitude; an all-refund basket carries it with a negative
   * sign.
   */
  public Basket setTaxTotal(BigDecimal amount) {
    return mutateOne(basket -> basket.setTaxTotal(amount));
  }
}
