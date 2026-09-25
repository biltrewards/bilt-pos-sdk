/*
 *    ____  _ _ _
 *   | __ )(_) | |_
 *   |  _ \| | | __|
 *   | |_) | | | |_
 *   |____/|_|_|\__|
 *
 *   Bilt POS SDK
 */
package com.bilt.pos.session.basket;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * One change to a session basket: the snapshot before, the snapshot after, which updater produced
 * it, and the line-level diff between the two.
 *
 * <p>Every updater on {@code SessionBasket} ends in exactly one change, whatever its style: an
 * incremental call such as {@code addItem} ({@link Source#INCREMENTAL}), a {@code mutate(..)} batch
 * ({@link Source#BATCH}), a {@code replace(..)} snapshot ({@link Source#REPLACE}), or {@code
 * clear()} ({@link Source#CLEAR}). A consumer that drives a customer display only needs {@link
 * #current()}; one that reacts to what the shopper did (a new item versus a quantity bump) reads
 * the diff.
 *
 * <p>Lines are paired across the two snapshots by identity, the way the basket itself keys them: by
 * {@code reference} when a line has one, otherwise by {@code sku} and item type among the
 * unreferenced lines. A paired line reports as {@link #quantityChanged()}, {@link #priceChanged()},
 * {@link #discountsChanged()} or {@link #taxChanged()}, and can appear in several of those at once;
 * an unpaired line is {@link #added()} or {@link #removed()}. Lines are only paired within one
 * cart: when the two snapshots carry different cart ids (a {@code clear()}, or a {@code replace()}
 * that started a fresh cart after settlement) every previous line is removed and every current line
 * added. Description, category and metadata are not part of the diff.
 *
 * <p>Instances are immutable.
 */
public final class BasketChange {

  /** Which updater produced a change. */
  public enum Source {
    /** A single incremental call: {@code addItem}, {@code removeItem}, {@code setTaxRate}, ... */
    INCREMENTAL,
    /** A {@code mutate(..)} batch, applied atomically as one change. */
    BATCH,
    /** A {@code replace(..)} of the whole basket by a snapshot or item list. */
    REPLACE,
    /** A {@code clear()} that started a fresh, empty basket. */
    CLEAR
  }

  /** A line paired across the two snapshots, before and after the change. */
  public static final class LineChange {

    private final BasketLineItem before;
    private final BasketLineItem after;

    LineChange(BasketLineItem before, BasketLineItem after) {
      this.before = before;
      this.after = after;
    }

    /** The line as it was in {@link BasketChange#previous()}. */
    public BasketLineItem before() {
      return before;
    }

    /** The line as it is in {@link BasketChange#current()}; it keeps the same item id. */
    public BasketLineItem after() {
      return after;
    }

    @Override
    public String toString() {
      return "LineChange{itemId=" + after.getItemId() + ", sku=" + after.getSku() + "}";
    }
  }

  private final Basket previous;
  private final Basket current;
  private final Source source;
  private final List<BasketLineItem> added;
  private final List<BasketLineItem> removed;
  private final List<LineChange> quantityChanged;
  private final List<LineChange> priceChanged;
  private final List<LineChange> discountsChanged;
  private final List<LineChange> taxChanged;
  private final boolean taxTotalChanged;

  private BasketChange(
      Basket previous,
      Basket current,
      Source source,
      List<BasketLineItem> added,
      List<BasketLineItem> removed,
      List<LineChange> quantityChanged,
      List<LineChange> priceChanged,
      List<LineChange> discountsChanged,
      List<LineChange> taxChanged,
      boolean taxTotalChanged) {
    this.previous = previous;
    this.current = current;
    this.source = source;
    this.added = Collections.unmodifiableList(added);
    this.removed = Collections.unmodifiableList(removed);
    this.quantityChanged = Collections.unmodifiableList(quantityChanged);
    this.priceChanged = Collections.unmodifiableList(priceChanged);
    this.discountsChanged = Collections.unmodifiableList(discountsChanged);
    this.taxChanged = Collections.unmodifiableList(taxChanged);
    this.taxTotalChanged = taxTotalChanged;
  }

  /**
   * Computes the diff between two snapshots.
   *
   * @throws IllegalArgumentException if either snapshot holds more than one unreferenced line with
   *     the same SKU and type, or more than one line with the same reference, so lines cannot be
   *     paired; a session basket never does, a register-built snapshot can
   */
  public static BasketChange between(Basket previous, Basket current, Source source) {
    Objects.requireNonNull(previous, "previous");
    Objects.requireNonNull(current, "current");
    Objects.requireNonNull(source, "source");
    // validate both sides up front, so an ambiguous line is reported
    // whether or not its SKU also occurs on the other side
    for (BasketLineItem line : previous.getItems()) {
      previous.getCounterpart(line);
    }
    for (BasketLineItem line : current.getItems()) {
      current.getCounterpart(line);
    }
    boolean sameCart = Objects.equals(previous.getCartId(), current.getCartId());

    List<BasketLineItem> added = new ArrayList<>();
    List<BasketLineItem> removed = new ArrayList<>();
    List<LineChange> quantityChanged = new ArrayList<>();
    List<LineChange> priceChanged = new ArrayList<>();
    List<LineChange> discountsChanged = new ArrayList<>();
    List<LineChange> taxChanged = new ArrayList<>();
    Set<String> pairedPreviousIds = new HashSet<>();
    for (BasketLineItem after : current.getItems()) {
      BasketLineItem before = sameCart ? previous.getCounterpart(after) : null;
      if (before == null) {
        added.add(after);
        continue;
      }
      pairedPreviousIds.add(before.getItemId());
      LineChange change = new LineChange(before, after);
      if (before.getQuantity() != after.getQuantity()) {
        quantityChanged.add(change);
      }
      if (before.getUnitPrice().compareTo(after.getUnitPrice()) != 0) {
        priceChanged.add(change);
      }
      if (!before.getDiscounts().equals(after.getDiscounts())) {
        discountsChanged.add(change);
      }
      if (compare(before.getTaxAmount(), after.getTaxAmount()) != 0
          || compare(before.getTaxRate(), after.getTaxRate()) != 0) {
        taxChanged.add(change);
      }
    }
    for (BasketLineItem before : previous.getItems()) {
      if (!pairedPreviousIds.contains(before.getItemId())) {
        removed.add(before);
      }
    }
    boolean taxTotalChanged = compare(previous.getTaxTotal(), current.getTaxTotal()) != 0;
    return new BasketChange(
        previous,
        current,
        source,
        added,
        removed,
        quantityChanged,
        priceChanged,
        discountsChanged,
        taxChanged,
        taxTotalChanged);
  }

  /** Null-tolerant numeric comparison; {@code null} only equals {@code null}. */
  private static int compare(BigDecimal a, BigDecimal b) {
    if (a == null || b == null) {
      return a == b ? 0 : 1;
    }
    return a.compareTo(b);
  }

  /** The basket before the change. */
  public Basket previous() {
    return previous;
  }

  /** The basket after the change; what a customer display shows. */
  public Basket current() {
    return current;
  }

  /** The updater that produced the change. */
  public Source source() {
    return source;
  }

  /** Lines in {@link #current()} with no counterpart in {@link #previous()}, in basket order. */
  public List<BasketLineItem> added() {
    return added;
  }

  /** Lines in {@link #previous()} with no counterpart in {@link #current()}, in basket order. */
  public List<BasketLineItem> removed() {
    return removed;
  }

  /** Paired lines whose quantity differs. */
  public List<LineChange> quantityChanged() {
    return quantityChanged;
  }

  /** Paired lines whose unit price differs. */
  public List<LineChange> priceChanged() {
    return priceChanged;
  }

  /** Paired lines whose register-applied discounts differ. */
  public List<LineChange> discountsChanged() {
    return discountsChanged;
  }

  /**
   * Paired lines whose tax rate or tax amount differs. A quantity change on a rate-taxed line moves
   * its tax amount too, so such a line is reported here as well as in {@link #quantityChanged()}.
   */
  public List<LineChange> taxChanged() {
    return taxChanged;
  }

  /** Whether the basket-level tax total differs between the two snapshots. */
  public boolean taxTotalChanged() {
    return taxTotalChanged;
  }

  /**
   * Whether the diff is empty: no line added, removed or changed, and the same tax total. A {@code
   * replace()} with a snapshot equal to the current basket produces an empty change, and the
   * session does not notify it.
   */
  public boolean isEmpty() {
    return added.isEmpty()
        && removed.isEmpty()
        && quantityChanged.isEmpty()
        && priceChanged.isEmpty()
        && discountsChanged.isEmpty()
        && taxChanged.isEmpty()
        && !taxTotalChanged;
  }

  @Override
  public String toString() {
    return "BasketChange{source="
        + source
        + ", added="
        + added.size()
        + ", removed="
        + removed.size()
        + ", quantityChanged="
        + quantityChanged.size()
        + ", priceChanged="
        + priceChanged.size()
        + ", discountsChanged="
        + discountsChanged.size()
        + ", taxChanged="
        + taxChanged.size()
        + ", taxTotalChanged="
        + taxTotalChanged
        + "}";
  }
}
