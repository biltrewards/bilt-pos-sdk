package com.bilt.pos.session;

import static org.junit.jupiter.api.Assertions.*;

import com.bilt.pos.nexo.client.BiltNexoTerminalClient;
import com.bilt.pos.session.basket.BasketItem;
import com.bilt.pos.session.settlement.SettlementResult;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** The {@link CheckoutPhase} transitions a terminal session drives itself around settlement. */
class TerminalShopperSessionContextTest {

  private static final String PAYMENT_OK =
      "{\"SaleToPOIResponse\":{\"PaymentResponse\":{"
          + "\"Response\":{\"Result\":\"Success\"},"
          + "\"POIData\":{\"POITransactionID\":{\"TransactionID\":\"POI-PAY-1\","
          + "\"TimeStamp\":\"2026-07-20T10:00:03Z\"}},"
          + "\"PaymentResult\":{"
          + "\"AmountsResp\":{\"Currency\":\"USD\",\"AuthorizedAmount\":100.0},"
          + "\"PaymentAcquirerData\":{\"ApprovalCode\":\"APPR7\","
          + "\"AcquirerTransactionID\":{\"TransactionID\":\"ACQ-1\"}},"
          + "\"PaymentInstrumentData\":{\"CardData\":{\"PaymentBrand\":\"Visa\"}}}}}}";

  private static final String PAYMENT_DECLINED =
      "{\"SaleToPOIResponse\":{\"PaymentResponse\":{"
          + "\"Response\":{\"Result\":\"Failure\",\"ErrorCondition\":\"Refusal\"}}}}";

  private MockWebServer server;
  private TerminalShopperSession session;

  /** The phase the session reported, from the server thread, as each request arrived. */
  private final List<CheckoutPhase> phaseAtRequest = new ArrayList<>();

  @BeforeEach
  void setUp() throws Exception {
    server = new MockWebServer();
    server.start();
    server.enqueue(new MockResponse().setBody(TerminalShopperSessionTest.ADMIN_OK));
    session = sessionBuilder().start().get();
    server.takeRequest(5, TimeUnit.SECONDS); // drain the session-start Admin request
  }

  private TerminalShopperSession.Builder sessionBuilder() {
    return TerminalShopperSession.builder()
        .client(
            BiltNexoTerminalClient.builder()
                .endpoint(server.url("/nexo").toString())
                .disableRecoveryOnNetworkError()
                .build())
        .saleId("POS-LANE-3")
        .poiId("VictaLane-275839164")
        .currency("USD")
        .autoDisplay(false);
  }

  @AfterEach
  void tearDown() throws Exception {
    server.shutdown();
  }

  /** Answers every request with {@code body}, recording the session's phase as it arrives. */
  private void answerWith(String body) {
    server.setDispatcher(
        new Dispatcher() {
          @Override
          public MockResponse dispatch(RecordedRequest request) {
            phaseAtRequest.add(session.context().phase());
            return new MockResponse().setBody(body);
          }
        });
  }

  private void addHundredDollarItem() {
    session.basket().addItem(BasketItem.sale("SKU-1", "Item", 1, new BigDecimal("100.00")));
  }

  @Test
  void terminalSessionContextCarriesThePoiIdAndBuilderSeeds() throws Exception {
    server.enqueue(new MockResponse().setBody(TerminalShopperSessionTest.ADMIN_OK));
    TerminalShopperSession seeded =
        sessionBuilder()
            .storeLocation("store-7")
            .phase(CheckoutPhase.MEMBER_IDENTIFIED)
            .attribute("lane-type", "pharmacy")
            .start()
            .get();

    SessionContextSnapshot snapshot = seeded.context().snapshot();
    assertEquals("VictaLane-275839164", snapshot.poiId());
    assertEquals("POS-LANE-3", snapshot.saleId());
    assertEquals("USD", snapshot.currency());
    assertEquals("store-7", snapshot.storeLocation());
    assertEquals(CheckoutPhase.MEMBER_IDENTIFIED, snapshot.phase());
    assertEquals(Map.of("lane-type", "pharmacy"), snapshot.attributes());
    assertEquals(CheckoutPhase.SCANNING, session.context().phase(), "default phase");
  }

  @Test
  void settlementMovesTenderingThenCompleteAndClearReturnsToScanning() throws Exception {
    addHundredDollarItem();
    session.context().attribute("lane-type", "pharmacy");
    answerWith(PAYMENT_OK);

    SettlementResult result = session.settle().get();

    assertTrue(result.isSuccess());
    assertEquals(
        List.of(CheckoutPhase.TENDERING),
        phaseAtRequest,
        "the card payment is sent while the checkout is tendering");
    assertEquals(CheckoutPhase.COMPLETE, session.context().phase());
    assertEquals(
        Map.of("lane-type", "pharmacy"),
        session.context().attributes(),
        "settlement leaves the attributes alone");

    session.basket().clear();

    assertEquals(CheckoutPhase.SCANNING, session.context().phase());
  }

  @Test
  void failedSettlementRestoresThePhaseTheCheckoutHadWhenItBegan() throws Exception {
    addHundredDollarItem();
    session.context().phase(CheckoutPhase.MEMBER_IDENTIFIED);
    answerWith(PAYMENT_DECLINED);

    assertThrows(SessionException.class, () -> session.settle().get());

    assertEquals(List.of(CheckoutPhase.TENDERING), phaseAtRequest);
    assertEquals(
        CheckoutPhase.MEMBER_IDENTIFIED,
        session.context().phase(),
        "a declined tender hands the checkout back to the phase it was in");
    assertEquals(1, session.basket().snapshot().getItemCount(), "the basket is kept for a retry");
  }

  @Test
  void aSettlementRefusedBeforeItStartsLeavesThePhaseAlone() {
    session.context().phase(CheckoutPhase.MEMBER_IDENTIFIED);

    SessionException failure = assertThrows(SessionException.class, () -> session.settle().get());

    assertEquals(SessionErrorCode.INVALID_STATE, failure.getError().getCode());
    assertEquals(CheckoutPhase.MEMBER_IDENTIFIED, session.context().phase());
    assertEquals(0, server.getRequestCount() - 1, "nothing beyond the session start was sent");
  }

  @Test
  void thePosMaySetThePhaseWhileTheSessionIsOpenAndNotAfterEnd() throws Exception {
    session.context().phase(CheckoutPhase.TENDERING);
    assertEquals(CheckoutPhase.TENDERING, session.context().phase());

    server.enqueue(new MockResponse().setBody(TerminalShopperSessionTest.ADMIN_OK));
    session.end().executeSync();

    assertThrows(
        IllegalStateException.class, () -> session.context().phase(CheckoutPhase.SCANNING));
    assertEquals(CheckoutPhase.TENDERING, session.context().phase());
  }
}
