package com.bilt.pos.session;

import static org.junit.jupiter.api.Assertions.*;

import com.bilt.pos.session.basket.Basket;
import com.bilt.pos.session.basket.BasketItem;
import java.math.BigDecimal;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

/** The local {@link ShopperSession}: a basket and a lifecycle with no terminal anywhere. */
class ShopperSessionTest {

  private static BasketItem candle() {
    return BasketItem.sale("KRK-CNDL-LRG-VAN", "Large Vanilla Candle", 2, new BigDecimal("24.99"));
  }

  @Test
  void localSessionRingsABasketAndEndsWithoutATerminal() {
    ShopperSession session =
        ShopperSession.builder()
            .saleId("POS-LANE-3")
            .currency("USD")
            .storeLocation("store-7")
            .start();

    assertNotNull(session.getSessionId());
    assertEquals("POS-LANE-3", session.getSaleId());
    assertEquals("USD", session.getCurrency());
    assertEquals("store-7", session.getStoreLocation());
    assertNull(session.getMember());
    assertTrue(session.basket().snapshot().isEmpty());

    Basket basket = session.basket().addItem(candle());
    assertEquals(1, basket.getItems().size());
    assertEquals(new BigDecimal("49.98"), basket.getGrandTotal());
    assertEquals(basket.getGrandTotal(), session.basket().snapshot().getGrandTotal());

    Basket cleared = session.basket().clear();
    assertTrue(cleared.isEmpty());
    assertNotEquals(basket.getCartId(), cleared.getCartId());

    session.end().executeSync();

    assertThrows(IllegalStateException.class, () -> session.basket().addItem(candle()));
    assertThrows(IllegalStateException.class, () -> session.basket().clear());
    SessionException again = assertThrows(SessionException.class, () -> session.end().get());
    assertEquals(SessionErrorCode.INVALID_STATE, again.getError().getCode());
  }

  @Test
  void endExecutesAsynchronouslyLikeEveryOperation() throws Exception {
    ShopperSession session = ShopperSession.builder().saleId("POS-LANE-3").currency("USD").start();
    CountDownLatch completed = new CountDownLatch(1);
    AtomicReference<SessionError> failure = new AtomicReference<>();

    session.end().onError(failure::set).onComplete(completed::countDown).execute();

    assertTrue(completed.await(5, TimeUnit.SECONDS));
    assertNull(failure.get());
    assertThrows(IllegalStateException.class, () -> session.basket().addItem(candle()));
  }

  @Test
  void closeEndsOnceAndLeavesAnEndedSessionAlone() {
    ShopperSession session = ShopperSession.builder().saleId("POS-LANE-3").currency("USD").start();
    session.close();
    session.close();
    assertThrows(IllegalStateException.class, () -> session.basket().addItem(candle()));
  }

  @Test
  void builderRequiresSaleIdAndCurrency() {
    assertThrows(
        IllegalStateException.class, () -> ShopperSession.builder().currency("USD").start());
    assertThrows(
        IllegalStateException.class, () -> ShopperSession.builder().saleId("POS-LANE-3").start());
  }
}
