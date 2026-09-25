package com.bilt.pos.session;

import static org.junit.jupiter.api.Assertions.*;

import com.bilt.pos.session.basket.Basket;
import com.bilt.pos.session.basket.BasketChange;
import com.bilt.pos.session.basket.BasketChange.Source;
import com.bilt.pos.session.basket.BasketItem;
import com.bilt.pos.session.basket.BasketLineItem;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The three basket updaters end in the same {@link BasketChange} notification, observed through a
 * recording session that has no terminal and no lifecycle rules.
 */
class SessionBasketTest {

  /** Records every change the base class hands to {@link #basketChanged(BasketChange)}. */
  private static final class RecordingSession extends AbstractShopperSession {

    final List<BasketChange> changes = new ArrayList<>();
    boolean consumed;
    int cleared;

    RecordingSession() {
      super("POS-LANE-3", "USD", null, null, null);
    }

    @Override
    void requireBasketMutable() {
      if (consumed) {
        throw new IllegalStateException("consumed");
      }
    }

    @Override
    void requireBasketClearable() {}

    @Override
    boolean basketConsumed() {
      return consumed;
    }

    @Override
    void basketCleared() {
      consumed = false;
      cleared++;
    }

    @Override
    void basketChanged(BasketChange change) {
      changes.add(change);
    }

    @Override
    boolean ended() {
      return false;
    }

    @Override
    public SessionResult<Void> end() {
      return operation("end", () -> null);
    }
  }

  private static BasketItem candle(int quantity) {
    return BasketItem.sale("CNDL", "Candle", quantity, new BigDecimal("24.99"));
  }

  private static BasketItem frame() {
    return BasketItem.sale("FRAME", "Frame", 1, new BigDecimal("14.99"));
  }

  // ─── One change per updater ───

  @Test
  void incrementalCallsEachProduceOneChange() {
    RecordingSession session = new RecordingSession();

    session.basket().addItem(candle(2));
    session.basket().setTaxRateBySku("CNDL", new BigDecimal("0.08875"));

    assertEquals(2, session.changes.size());
    assertEquals(Source.INCREMENTAL, session.changes.get(0).source());
    assertEquals(1, session.changes.get(0).added().size());
    assertEquals(Source.INCREMENTAL, session.changes.get(1).source());
    assertEquals(1, session.changes.get(1).taxChanged().size());
  }

  @Test
  void batchMutateProducesExactlyOneChange() {
    RecordingSession session = new RecordingSession();
    session.basket().addItem(candle(2));
    session.changes.clear();

    session
        .basket()
        .mutate(
            m ->
                m.addItem(frame())
                    .updateItemQuantityBySku("CNDL", 5)
                    .setTaxTotal(new BigDecimal("3.00")));

    assertEquals(1, session.changes.size());
    BasketChange change = session.changes.get(0);
    assertEquals(Source.BATCH, change.source());
    assertEquals(1, change.added().size());
    assertEquals(1, change.quantityChanged().size());
    assertTrue(change.taxTotalChanged());
    assertEquals(1, change.previous().getItemCount());
    assertEquals(2, change.current().getItemCount());
  }

  @Test
  void failedBatchProducesNoChange() {
    RecordingSession session = new RecordingSession();
    session.basket().addItem(candle(2));
    session.changes.clear();

    assertThrows(
        IllegalArgumentException.class,
        () -> session.basket().mutate(m -> m.addItem(frame()).removeItemBySku("NO-SUCH")));

    assertTrue(session.changes.isEmpty());
  }

  @Test
  void clearProducesOneChangeWithSourceClear() {
    RecordingSession session = new RecordingSession();
    session.basket().addItem(candle(2));
    session.basket().addItem(frame());
    session.changes.clear();

    Basket cleared = session.basket().clear();

    assertTrue(cleared.isEmpty());
    assertEquals(1, session.changes.size());
    BasketChange change = session.changes.get(0);
    assertEquals(Source.CLEAR, change.source());
    assertEquals(2, change.removed().size());
    assertTrue(change.added().isEmpty());
    assertNotEquals(change.previous().getCartId(), change.current().getCartId());
  }

  @Test
  void clearingAnEmptyBasketIsNotNotified() {
    RecordingSession session = new RecordingSession();

    session.basket().clear();

    assertTrue(session.changes.isEmpty());
  }

  @Test
  void mutationThatChangesNothingIsNotNotified() {
    RecordingSession session = new RecordingSession();
    session.basket().addItem(candle(2));
    session.changes.clear();

    session.basket().setDiscountsBySku("CNDL", Collections.emptyList());

    assertTrue(session.changes.isEmpty());
  }

  // ─── replace(Basket) ───

  @Test
  void replacePreservesItemIdsOfPairedLinesAndAssignsNewOnesToNewLines() {
    RecordingSession session = new RecordingSession();
    session.basket().addItem(candle(2));
    session.basket().addItem(frame());
    session.basket().removeItemBySku("FRAME"); // id 2 is spent
    Basket current = session.basket().snapshot();
    session.changes.clear();

    Basket snapshot =
        Basket.builder()
            .items(
                Arrays.asList(
                    lineFor(candle(3)), // paired by SKU: keeps id 1
                    lineFor(frame()), // new line: gets a fresh id
                    lineFor(frame().withReference("gift-1")))) // referenced: distinct line
            .build();
    Basket replaced = session.basket().replace(snapshot);

    assertEquals(3, replaced.getItemCount());
    assertEquals("1", replaced.getItemBySku("CNDL").getItemId());
    assertEquals(3, replaced.getItem("1").getQuantity());
    assertEquals("3", replaced.getCounterpart(frame()).getItemId(), "ids are never reused");
    assertEquals("4", replaced.getItemByReference("gift-1").getItemId());
    assertEquals(current.getCartId(), replaced.getCartId(), "same cart, edited in place");

    assertEquals(1, session.changes.size());
    BasketChange change = session.changes.get(0);
    assertEquals(Source.REPLACE, change.source());
    assertEquals(1, change.quantityChanged().size());
    assertEquals(2, change.added().size());
    assertTrue(change.removed().isEmpty());
  }

  @Test
  void replaceWithAnIdenticalSnapshotIsNotNotified() {
    RecordingSession session = new RecordingSession();
    session.basket().addItem(candle(2));
    session.basket().setTaxRateBySku("CNDL", new BigDecimal("0.08875"));
    Basket before = session.basket().snapshot();
    session.changes.clear();

    Basket after = session.basket().replace(before);

    assertTrue(session.changes.isEmpty());
    assertEquals(before.getGrandTotal(), after.getGrandTotal());
    assertEquals("1", after.getItem("1").getItemId());
  }

  @Test
  void replaceRoundTripsRateBasedAndFixedTaxAndTheTaxTotalOverride() {
    RecordingSession session = new RecordingSession();
    session.basket().addItem(candle(2));
    session.basket().addItem(frame());
    session.basket().setTaxRateBySku("CNDL", new BigDecimal("0.08875"));
    session.basket().setTaxAmountBySku("FRAME", new BigDecimal("2.50"));
    Basket rateBased = session.basket().snapshot();

    Basket replaced = session.basket().replace(rateBased);
    // a rate-based line stays rate-based: a later quantity change re-prices its tax
    Basket bumped = session.basket().updateItemQuantityBySku("CNDL", 4);

    assertEquals(rateBased.getTaxTotal(), replaced.getTaxTotal());
    assertEquals(new BigDecimal("8.87"), bumped.getItemBySku("CNDL").getTaxAmount());
    assertEquals(new BigDecimal("2.50"), bumped.getItemBySku("FRAME").getTaxAmount());

    session.basket().setTaxTotal(new BigDecimal("5.00"));
    Basket overridden = session.basket().snapshot();
    Basket replacedAgain = session.basket().replace(overridden);
    Basket bumpedAgain = session.basket().updateItemQuantityBySku("CNDL", 5);

    assertEquals(new BigDecimal("5.00"), replacedAgain.getTaxTotal());
    assertEquals(new BigDecimal("5.00"), bumpedAgain.getTaxTotal(), "the override survives");
  }

  @Test
  void replaceWithARegisterBuiltSnapshotUsesItsTaxTotalAsTheOverride() {
    RecordingSession session = new RecordingSession();

    Basket replaced =
        session
            .basket()
            .replace(
                Basket.builder()
                    .items(Arrays.asList(lineFor(candle(2)), lineFor(frame())))
                    .taxTotal(new BigDecimal("5.76"))
                    .build());

    assertEquals(new BigDecimal("5.76"), replaced.getTaxTotal());
    assertEquals(new BigDecimal("70.73"), replaced.getGrandTotal());
  }

  @Test
  void replaceIsAtomicWhenTheSnapshotIsAmbiguous() {
    RecordingSession session = new RecordingSession();
    session.basket().addItem(candle(2));
    session.changes.clear();

    Basket ambiguous =
        Basket.builder().items(Arrays.asList(lineFor(candle(1)), lineFor(candle(3)))).build();

    assertThrows(IllegalArgumentException.class, () -> session.basket().replace(ambiguous));
    assertEquals(2, session.basket().snapshot().getItem("1").getQuantity());
    assertTrue(session.changes.isEmpty());
  }

  @Test
  void replaceAfterAConsumedBasketStartsAFreshCart() {
    RecordingSession session = new RecordingSession();
    session.basket().addItem(candle(2));
    Basket consumed = session.basket().snapshot();
    session.consumed = true;
    session.changes.clear();
    assertThrows(IllegalStateException.class, () -> session.basket().addItem(frame()));

    Basket next = session.basket().replace(Collections.singletonList(candle(1)));

    assertNotEquals(consumed.getCartId(), next.getCartId());
    assertEquals(1, session.cleared);
    assertFalse(session.consumed);
    assertEquals("1", next.getItemBySku("CNDL").getItemId(), "fresh cart, fresh ids");
    BasketChange change = session.changes.get(0);
    assertEquals(Source.REPLACE, change.source());
    assertEquals(1, change.removed().size(), "the consumed cart's lines are gone");
    assertEquals(1, change.added().size(), "everything in the next cart is new");
    assertTrue(change.quantityChanged().isEmpty());
  }

  @Test
  void replaceAfterAConsumedBasketLeavesItUntouchedWhenTheSnapshotIsInvalid() {
    RecordingSession session = new RecordingSession();
    session.basket().addItem(candle(2));
    session.consumed = true;

    assertThrows(
        IllegalArgumentException.class,
        () -> session.basket().replace(Arrays.asList(candle(1), candle(3))));

    assertTrue(session.consumed, "nothing was swapped in");
    assertEquals(0, session.cleared);
    assertEquals(2, session.basket().snapshot().getItem("1").getQuantity());
  }

  // ─── replace(List<BasketItem>) ───

  @Test
  void replaceWithItemsKeepsTheTaxTotalOverrideUnlessItemsCarryTax() {
    RecordingSession session = new RecordingSession();
    session.basket().addItem(candle(2));
    session.basket().setTaxTotal(new BigDecimal("4.00"));

    Basket untaxedItems = session.basket().replace(Arrays.asList(candle(2), frame()));
    assertEquals(new BigDecimal("4.00"), untaxedItems.getTaxTotal(), "override kept");

    Basket taxedItems =
        session
            .basket()
            .replace(
                Arrays.asList(
                    BasketItem.builder()
                        .sku("CNDL")
                        .description("Candle")
                        .quantity(2)
                        .unitPrice(new BigDecimal("24.99"))
                        .taxRate(new BigDecimal("0.10"))
                        .build(),
                    frame()));
    assertEquals(new BigDecimal("5.00"), taxedItems.getTaxTotal(), "item-level tax applies");
    assertEquals("1", taxedItems.getItemBySku("CNDL").getItemId());
    assertEquals("2", taxedItems.getItemBySku("FRAME").getItemId());
  }

  @Test
  void replaceWithAnEmptyListEmptiesTheBasketInPlace() {
    RecordingSession session = new RecordingSession();
    session.basket().addItem(candle(2));
    String cartId = session.basket().snapshot().getCartId();
    session.changes.clear();

    Basket emptied = session.basket().replace(Collections.emptyList());

    assertTrue(emptied.isEmpty());
    assertEquals(cartId, emptied.getCartId());
    assertEquals(1, session.changes.get(0).removed().size());
  }

  /** A register-built line for {@code item}, as a POS assembling a snapshot would produce. */
  private static BasketLineItem lineFor(BasketItem item) {
    return BasketLineItem.builder()
        .reference(item.getReference())
        .sku(item.getSku())
        .description(item.getDescription())
        .quantity(item.getQuantity())
        .unitPrice(item.getUnitPrice())
        .type(item.getType())
        .build();
  }
}
