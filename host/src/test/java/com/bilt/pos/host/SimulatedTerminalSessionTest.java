package com.bilt.pos.host;

import static com.bilt.pos.host.HostClient.json;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bilt.pos.nexo.client.BiltNexoClientException;
import com.bilt.pos.nexo.client.TerminalClient;
import com.bilt.pos.nexo.model.MessageCategoryType;
import com.bilt.pos.nexo.model.NexoTerminalAPI;
import com.bilt.pos.nexo.model.PaymentTypeEnum;
import com.bilt.pos.nexo.model.SaleToPOIRequest;
import com.bilt.pos.nexo.sim.SimulatedTerminalClient;
import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** A terminal session over HTTP using the stateful in-memory Nexo terminal. */
class SimulatedTerminalSessionTest {

  private SimulatedTerminalClient simulated;
  private RecordingTerminalClient terminal;
  private SessionHost host;
  private HostClient client;

  @BeforeEach
  void start() {
    simulated = SimulatedTerminalClient.builder().build();
    terminal = new RecordingTerminalClient(simulated);
    host =
        SessionHost.builder()
            .port(0)
            .terminal(
                TerminalClientProvider.of(
                    terminal,
                    TerminalInfo.of("Simulated terminal", "In-memory").withReachable(true)))
            .build();
    host.start();
    client = new HostClient(host.port());
  }

  @AfterEach
  void stop() {
    host.stop();
  }

  @Test
  void completesMemberSaleRefundVoidAndTransactionStatusFlows() throws Exception {
    String firstSession = createSession("LANE-1");
    JsonNode identified = complete(firstSession, json("{'type':'identifyMember'}"));
    assertEquals("MEMBER", identified.path("result").path("shopperContext").path("kind").asText());

    addItem(firstSession, "SALE-1", "100.00");
    JsonNode firstSale = complete(firstSession, json("{'type':'settle'}"));
    assertTrue(firstSale.path("result").path("success").asBoolean());
    assertEquals("95.00", firstSale.path("result").path("cardAmountCharged").asText());
    assertEquals(100, firstSale.path("result").path("totalPointsEarned").asInt());
    assertEquals(11600, simulated.points("mbr_sim_1"));
    String firstPaymentServiceId = terminal.firstPaymentServiceId();

    JsonNode refund = complete(firstSession, json("{'type':'refund','amount':'25.00'}"));
    assertEquals(
        0,
        new BigDecimal("25.00")
            .compareTo(new BigDecimal(refund.path("result").path("refundedAmount").asText())));

    String secondSession = createSession("LANE-2");
    addItem(secondSession, "SALE-2", "20.00");
    simulated.dropConnectionAfterNextApproval();
    JsonNode secondSale = complete(secondSession, json("{'type':'settle'}"));
    assertTrue(secondSale.path("result").path("success").asBoolean());
    assertEquals(
        0,
        new BigDecimal("20.00")
            .compareTo(
                new BigDecimal(secondSale.path("result").path("cardAmountCharged").asText())));

    JsonNode voided = complete(secondSession, json("{'type':'voidTransaction'}"));
    assertTrue(voided.path("result").isObject());

    JsonNode status =
        complete(
            firstSession,
            json(
                "{'type':'getTransactionStatus','originalServiceId':'"
                    + firstPaymentServiceId
                    + "'}"));
    assertTrue(status.path("result").path("found").asBoolean());
    assertEquals("Payment", status.path("result").path("messageCategory").asText());
    assertEquals(
        "Success",
        status.path("result").path("paymentResponse").path("Response").path("Result").asText());
  }

  private String createSession(String saleId) throws Exception {
    return client
        .post(
            "/v1/sessions",
            json(
                "{'kind':'terminal','saleId':'"
                    + saleId
                    + "','poiId':'SIM-POI','currency':'USD','autoDisplay':false}"))
        .expect(201)
        .text("id");
  }

  private void addItem(String sessionId, String sku, String price) throws Exception {
    client
        .post(
            "/v1/sessions/" + sessionId + "/basket/items",
            json(
                "{'sku':'" + sku + "','description':'Simulated item','unitPrice':'" + price + "'}"))
        .expect(200);
  }

  private JsonNode complete(String sessionId, String operation) throws Exception {
    String operationId =
        client.post("/v1/sessions/" + sessionId + "/operations", operation).expect(202).text("id");
    return client.awaitOperation(sessionId, operationId, "succeeded", Duration.ofSeconds(10));
  }

  private static final class RecordingTerminalClient implements TerminalClient {
    private final SimulatedTerminalClient delegate;
    private final List<String> paymentServiceIds = new CopyOnWriteArrayList<>();

    private RecordingTerminalClient(SimulatedTerminalClient delegate) {
      this.delegate = delegate;
    }

    @Override
    public NexoTerminalAPI request(NexoTerminalAPI request) throws BiltNexoClientException {
      record(request);
      return delegate.request(request);
    }

    @Override
    public NexoTerminalAPI request(NexoTerminalAPI request, Duration timeout)
        throws BiltNexoClientException {
      record(request);
      return delegate.request(request, timeout);
    }

    private void record(NexoTerminalAPI request) {
      SaleToPOIRequest saleRequest = request.getSaleToPOIRequest();
      if (saleRequest.getMessageHeader().getMessageCategory() != MessageCategoryType.PAYMENT) {
        return;
      }
      PaymentTypeEnum type =
          saleRequest.getPaymentRequest() == null
                  || saleRequest.getPaymentRequest().getPaymentData() == null
              ? null
              : saleRequest.getPaymentRequest().getPaymentData().getPaymentType();
      if (type != PaymentTypeEnum.REFUND) {
        paymentServiceIds.add(saleRequest.getMessageHeader().getServiceID());
      }
    }

    private String firstPaymentServiceId() {
      return paymentServiceIds.get(0);
    }
  }
}
