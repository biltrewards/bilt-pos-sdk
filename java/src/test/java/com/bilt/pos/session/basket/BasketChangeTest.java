package com.bilt.pos.session.basket;

import static org.junit.jupiter.api.Assertions.*;

import com.bilt.pos.session.basket.BasketChange.Source;
import com.bilt.pos.session.internal.BasketEngine;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Diff computation between two basket snapshots. */
class BasketChangeTest {

  private static BasketItem candle(int quantity) {
    return BasketItem.sale("CNDL", "Candle", quantity, new BigDecimal("24.99"));
  }

  private static BasketItem frame() {
    return BasketItem.sale("FRAME", "Frame", 1, new BigDecimal("14.99"));
  }

  private static Basket basket(BasketItem... items) {
    BasketEngine engine = new BasketEngine();
    for (BasketItem item : items) {
      engine.addItem(item);
    }
    return engine.snapshot();
  }

  /** A register-built snapshot with the given lines, the way a POS would assemble one. */
  private static Basket registerBasket(String cartId, BasketLineItem... lines) {
    return Basket.builder().cartId(cartId).items(Arrays.asList(lines)).build();
  }

  private static BasketLineItem line(String itemId, String reference, String sku, int quantity) {
    return BasketLineItem.builder()
        .itemId(itemId)
        .reference(reference)
        .sku(sku)
        .description(sku)
        .quantity(quantity)
        .unitPrice(new BigDecimal("10.00"))
        .originalTotal(new BigDecimal("10.00").multiply(BigDecimal.valueOf(quantity)))
        .type(BasketItemType.SALE)
        .build();
  }

  @Test
  void identicalSnapshotsYieldAnEmptyDiff() {
    BasketEngine engine = new BasketEngine();
    engine.addItem(candle(2));
    engine.setTaxRateBySku("CNDL", new BigDecimal("0.08875"));
    Basket previous = engine.snapshot();
    Basket current = engine.snapshot();

    BasketChange change = BasketChange.between(previous, current, Source.REPLACE);

    assertTrue(change.isEmpty());
    assertSame(previous, change.previous());
    assertSame(current, change.current());
    assertEquals(Source.REPLACE, change.source());
    assertTrue(change.added().isEmpty());
    assertTrue(change.removed().isEmpty());
    assertFalse(change.taxTotalChanged());
  }

  @Test
  void addedLineIsReportedAsAddedOnly() {
    BasketEngine engine = new BasketEngine();
    engine.addItem(candle(2));
    Basket previous = engine.snapshot();
    engine.addItem(frame());
    Basket current = engine.snapshot();

    BasketChange change = BasketChange.between(previous, current, Source.INCREMENTAL);

    assertFalse(change.isEmpty());
    assertEquals(1, change.added().size());
    assertEquals("FRAME", change.added().get(0).getSku());
    assertTrue(change.removed().isEmpty());
    assertTrue(change.quantityChanged().isEmpty());
    assertTrue(change.priceChanged().isEmpty());
    assertTrue(change.discountsChanged().isEmpty());
    assertTrue(change.taxChanged().isEmpty());
  }

  @Test
  void removedLineIsReportedAsRemovedOnly() {
    BasketEngine engine = new BasketEngine();
    engine.addItem(candle(2));
    engine.addItem(frame());
    Basket previous = engine.snapshot();
    engine.removeItemBySku("FRAME");
    Basket current = engine.snapshot();

    BasketChange change = BasketChange.between(previous, current, Source.INCREMENTAL);

    assertEquals(1, change.removed().size());
    assertEquals("FRAME", change.removed().get(0).getSku());
    assertEquals("2", change.removed().get(0).getItemId());
    assertTrue(change.added().isEmpty());
    assertTrue(change.quantityChanged().isEmpty());
  }

  @Test
  void quantityOnlyChangeCarriesBeforeAndAfter() {
    BasketEngine engine = new BasketEngine();
    engine.addItem(candle(2));
    Basket previous = engine.snapshot();
    engine.updateItemQuantityBySku("CNDL", 5);
    Basket current = engine.snapshot();

    BasketChange change = BasketChange.between(previous, current, Source.INCREMENTAL);

    assertEquals(1, change.quantityChanged().size());
    BasketChange.LineChange lineChange = change.quantityChanged().get(0);
    assertEquals(2, lineChange.before().getQuantity());
    assertEquals(5, lineChange.after().getQuantity());
    assertEquals("1", lineChange.after().getItemId());
    assertTrue(change.added().isEmpty());
    assertTrue(change.removed().isEmpty());
    assertTrue(change.priceChanged().isEmpty());
    assertTrue(change.taxChanged().isEmpty(), "an untaxed line's tax does not move");
  }

  @Test
  void priceChangeIsReportedWithoutAQuantityChange() {
    Basket previous = registerBasket("cart", line("1", null, "CNDL", 2));
    Basket current =
        registerBasket(
            "cart",
            BasketLineItem.builder()
                .itemId("1")
                .sku("CNDL")
                .description("CNDL")
                .quantity(2)
                .unitPrice(new BigDecimal("19.99"))
                .originalTotal(new BigDecimal("39.98"))
                .type(BasketItemType.SALE)
                .build());

    BasketChange change = BasketChange.between(previous, current, Source.REPLACE);

    assertEquals(1, change.priceChanged().size());
    assertEquals(new BigDecimal("10.00"), change.priceChanged().get(0).before().getUnitPrice());
    assertEquals(new BigDecimal("19.99"), change.priceChanged().get(0).after().getUnitPrice());
    assertTrue(change.quantityChanged().isEmpty());
  }

  @Test
  void discountChangeIsReported() {
    BasketEngine engine = new BasketEngine();
    engine.addItem(candle(2));
    Basket previous = engine.snapshot();
    engine.setDiscountsBySku(
        "CNDL", Collections.singletonList(BasketDiscount.manual("Loyalty", "5.00")));
    Basket current = engine.snapshot();

    BasketChange change = BasketChange.between(previous, current, Source.INCREMENTAL);

    assertEquals(1, change.discountsChanged().size());
    assertTrue(change.discountsChanged().get(0).before().getDiscounts().isEmpty());
    assertEquals(1, change.discountsChanged().get(0).after().getDiscounts().size());
    assertTrue(change.quantityChanged().isEmpty());
  }

  @Test
  void lineTaxChangeAndTaxTotalChangeAreReported() {
    BasketEngine engine = new BasketEngine();
    engine.addItem(candle(2));
    Basket previous = engine.snapshot();
    engine.setTaxRateBySku("CNDL", new BigDecimal("0.08875"));
    Basket current = engine.snapshot();

    BasketChange change = BasketChange.between(previous, current, Source.INCREMENTAL);

    assertEquals(1, change.taxChanged().size());
    assertNull(change.taxChanged().get(0).before().getTaxRate());
    assertEquals(new BigDecimal("0.08875"), change.taxChanged().get(0).after().getTaxRate());
    assertTrue(change.taxTotalChanged());
    assertTrue(change.quantityChanged().isEmpty());
  }

  @Test
  void taxTotalOverrideAloneIsANonEmptyChange() {
    BasketEngine engine = new BasketEngine();
    engine.addItem(candle(2));
    Basket previous = engine.snapshot();
    engine.setTaxTotal(new BigDecimal("4.00"));
    Basket current = engine.snapshot();

    BasketChange change = BasketChange.between(previous, current, Source.INCREMENTAL);

    assertFalse(change.isEmpty());
    assertTrue(change.taxTotalChanged());
    assertTrue(change.taxChanged().isEmpty(), "no line's own tax moved");
  }

  @Test
  void quantityChangeOnARateTaxedLineAlsoReportsItsTax() {
    BasketEngine engine = new BasketEngine();
    engine.addItem(candle(2));
    engine.setTaxRateBySku("CNDL", new BigDecimal("0.08875"));
    Basket previous = engine.snapshot();
    engine.updateItemQuantityBySku("CNDL", 3);
    Basket current = engine.snapshot();

    BasketChange change = BasketChange.between(previous, current, Source.INCREMENTAL);

    assertEquals(1, change.quantityChanged().size());
    assertEquals(1, change.taxChanged().size());
    assertTrue(change.taxTotalChanged());
  }

  // ─── Identity resolution ───

  @Test
  void referencedLinesPairByReferenceEvenWhenSkusAreShared() {
    Basket previous =
        registerBasket("cart", line("1", "ref-a", "CNDL", 1), line("2", "ref-b", "CNDL", 1));
    Basket current =
        registerBasket("cart", line("1", "ref-a", "CNDL", 1), line("2", "ref-b", "CNDL", 4));

    BasketChange change = BasketChange.between(previous, current, Source.REPLACE);

    assertEquals(1, change.quantityChanged().size());
    assertEquals("ref-b", change.quantityChanged().get(0).after().getReference());
    assertTrue(change.added().isEmpty());
    assertTrue(change.removed().isEmpty());
  }

  @Test
  void referenceWinsOverSkuSoARenamedReferenceIsRemovedAndAdded() {
    Basket previous = registerBasket("cart", line("1", "ref-a", "CNDL", 1));
    Basket current = registerBasket("cart", line("7", "ref-z", "CNDL", 1));

    BasketChange change = BasketChange.between(previous, current, Source.REPLACE);

    assertEquals(1, change.removed().size());
    assertEquals(1, change.added().size());
    assertTrue(change.quantityChanged().isEmpty());
  }

  @Test
  void unreferencedLinesPairBySkuAndType() {
    BasketEngine engine = new BasketEngine();
    engine.addItem(candle(2));
    engine.addItem(BasketItem.returnItem("CNDL", "Candle", 1, new BigDecimal("24.99")));
    Basket previous = engine.snapshot();
    engine.updateItemQuantity("2", 3); // the return line
    Basket current = engine.snapshot();

    BasketChange change = BasketChange.between(previous, current, Source.INCREMENTAL);

    assertEquals(1, change.quantityChanged().size());
    assertTrue(change.quantityChanged().get(0).after().isReturn());
    assertTrue(change.added().isEmpty());
    assertTrue(change.removed().isEmpty());
  }

  @Test
  void unreferencedLineDoesNotPairWithAReferencedOne() {
    Basket previous = registerBasket("cart", line("1", null, "CNDL", 1));
    Basket current = registerBasket("cart", line("1", "ref-a", "CNDL", 1));

    BasketChange change = BasketChange.between(previous, current, Source.REPLACE);

    assertEquals(1, change.removed().size());
    assertEquals(1, change.added().size());
  }

  @Test
  void ambiguousUnreferencedSkuThrows() {
    Basket previous = registerBasket("cart", line("1", null, "CNDL", 1));
    Basket ambiguous =
        registerBasket("cart", line("1", null, "CNDL", 1), line("2", null, "CNDL", 2));

    IllegalArgumentException e =
        assertThrows(
            IllegalArgumentException.class,
            () -> BasketChange.between(previous, ambiguous, Source.REPLACE));
    assertTrue(e.getMessage().contains("CNDL"));
    assertThrows(
        IllegalArgumentException.class,
        () -> BasketChange.between(ambiguous, previous, Source.REPLACE),
        "an ambiguous previous snapshot is rejected too");
  }

  @Test
  void duplicateReferenceThrows() {
    Basket previous = registerBasket("cart", line("1", "ref-a", "CNDL", 1));
    Basket ambiguous =
        registerBasket("cart", line("1", "ref-a", "CNDL", 1), line("2", "ref-a", "FRAME", 1));

    assertThrows(
        IllegalArgumentException.class,
        () -> BasketChange.between(previous, ambiguous, Source.REPLACE));
  }

  @Test
  void differentCartIdsPairNothing() {
    Basket previous = basket(candle(2), frame());
    Basket current = basket(candle(2));

    BasketChange change = BasketChange.between(previous, current, Source.CLEAR);

    assertEquals(2, change.removed().size());
    assertEquals(1, change.added().size());
    assertTrue(change.quantityChanged().isEmpty());
  }

  @Test
  void clearToAnEmptyBasketRemovesEveryLine() {
    Basket previous = basket(candle(2), frame());
    Basket current = new BasketEngine().snapshot();

    BasketChange change = BasketChange.between(previous, current, Source.CLEAR);

    List<BasketLineItem> removed = change.removed();
    assertEquals(2, removed.size());
    assertEquals("CNDL", removed.get(0).getSku());
    assertEquals("FRAME", removed.get(1).getSku());
    assertTrue(change.added().isEmpty());
    assertEquals(Source.CLEAR, change.source());
  }

  @Test
  void diffListsAreUnmodifiable() {
    Basket previous = basket(candle(2));
    Basket current = basket(candle(2), frame());
    BasketChange change = BasketChange.between(previous, current, Source.REPLACE);

    assertThrows(UnsupportedOperationException.class, () -> change.added().clear());
  }
}
