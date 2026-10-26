package com.bilt.pos.host;

import static com.bilt.pos.host.HostClient.json;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bilt.pos.nexo.client.BiltNexoClientException;
import com.bilt.pos.nexo.client.TerminalClient;
import com.bilt.pos.nexo.model.MessageCategoryType;
import com.bilt.pos.nexo.model.NexoTerminalAPI;
import com.bilt.pos.nexo.model.SaleToPOIRequest;
import com.bilt.pos.nexo.sim.SimulatedTerminalClient;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The register learns every money request's {@code ServiceID} over the bridge before it is sent.
 */
class ServiceIdBridgeTest {

  private static final String PAYMENT_DECLINED =
      "{\"SaleToPOIResponse\":{\"PaymentResponse\":{"
          + "\"Response\":{\"Result\":\"Failure\",\"ErrorCondition\":\"Refusal\"}}}}";

  private final List<SaleToPOIRequest> sent = new CopyOnWriteArrayList<>();
  private volatile boolean declineNextPayment;
  private SessionHost host;
  private HostClient client;

  @BeforeEach
  void start() {
    SimulatedTerminalClient simulated = SimulatedTerminalClient.builder().build();
    TerminalClient recording =
        new TerminalClient() {
          @Override
          public NexoTerminalAPI request(NexoTerminalAPI request) throws BiltNexoClientException {
            return request(request, null);
          }

          @Override
          public NexoTerminalAPI request(NexoTerminalAPI request, Duration timeout)
              throws BiltNexoClientException {
            SaleToPOIRequest body = request.getSaleToPOIRequest();
            sent.add(body);
            if (declineNextPayment
                && body.getMessageHeader().getMessageCategory() == MessageCategoryType.PAYMENT) {
              declineNextPayment = false;
              try {
                return new ObjectMapper()
                    .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                    .readValue(PAYMENT_DECLINED, NexoTerminalAPI.class);
              } catch (java.io.IOException e) {
                throw new BiltNexoClientException("malformed decline", e);
              }
            }
            return timeout == null
                ? simulated.request(request)
                : simulated.request(request, timeout);
          }
        };
    host =
        SessionHost.builder()
            .port(0)
            .terminal(
                TerminalClientProvider.of(
                    recording,
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
  void aSettleBeforeStepCarriesTheServiceIdTheChargeIsThenSentWith() throws Exception {
    String id = sessionWithItem();
    String operationId = submit(id, "{'type':'settle','handledSteps':['BEFORE_STEP']}").text("id");

    JsonNode step = awaitStep(id, operationId);
    assertEquals("BEFORE_STEP", step.path("kind").asText());
    assertEquals("CARD_CHARGE", step.path("context").path("step").asText());
    String serviceId = step.path("context").path("serviceId").asText();
    assertFalse(serviceId.isEmpty());
    assertTrue(lastOf(MessageCategoryType.PAYMENT).isEmpty(), "nothing is sent before the reply");
    reply(id, operationId, step);

    JsonNode done = client.awaitOperation(id, operationId, "succeeded", Duration.ofSeconds(10));
    assertEquals(serviceId, lastOf(MessageCategoryType.PAYMENT));
    assertEquals(
        serviceId, done.path("result").path("movements").path(0).path("serviceId").asText());
  }

  @Test
  void aVoidBeforeStepCarriesTheServiceIdTheReversalIsThenSentWith() throws Exception {
    String id = sessionWithItem();
    String settleId = submit(id, "{'type':'settle'}").text("id");
    client.awaitOperation(id, settleId, "succeeded", Duration.ofSeconds(10));

    String operationId =
        submit(id, "{'type':'voidTransaction','handledSteps':['BEFORE_STEP']}").text("id");
    JsonNode step = awaitStep(id, operationId);
    assertEquals("BEFORE_STEP", step.path("kind").asText());
    assertEquals("CARD", step.path("context").path("step").asText());
    String serviceId = step.path("context").path("serviceId").asText();
    assertFalse(serviceId.isEmpty());
    assertTrue(lastOf(MessageCategoryType.REVERSAL).isEmpty(), "nothing is sent before the reply");
    reply(id, operationId, step);

    client.awaitOperation(id, operationId, "succeeded", Duration.ofSeconds(10));
    assertEquals(serviceId, lastOf(MessageCategoryType.REVERSAL));
  }

  @Test
  void anUnlinkedRefundIsAcceptedWithTheServiceIdItIsSentWith() throws Exception {
    String id = sessionWithItem();
    HostClient.Response accepted = submit(id, "{'type':'refundUnlinked','amount':'5.00'}");
    String serviceId = accepted.text("serviceId");
    assertFalse(serviceId.isEmpty());

    JsonNode done =
        client.awaitOperation(id, accepted.text("id"), "succeeded", Duration.ofSeconds(10));
    assertEquals(serviceId, lastOf(MessageCategoryType.PAYMENT));
    assertEquals(serviceId, done.path("serviceId").asText());
  }

  @Test
  void aRetriedUnlinkedRefundAsksBeforeStepWithTheFreshIdItIsThenSentWith() throws Exception {
    String id = sessionWithItem();
    declineNextPayment = true;
    HostClient.Response accepted =
        submit(
            id,
            "{'type':'refundUnlinked','amount':'5.00',"
                + "'handledSteps':['BEFORE_STEP','REVERSAL_DECISION_REQUIRED']}");
    String operationId = accepted.text("id");
    String acceptedId = accepted.text("serviceId");

    JsonNode decision = awaitStep(id, operationId, "REVERSAL_DECISION_REQUIRED");
    assertEquals(acceptedId, lastOf(MessageCategoryType.PAYMENT));
    client
        .post(
            "/v1/sessions/" + id + "/operations/" + operationId + "/reply",
            json("{'stepId':'" + decision.path("stepId").asText() + "','decision':'RETRY'}"))
        .expect(200);

    JsonNode step = awaitStep(id, operationId, "BEFORE_STEP");
    assertEquals("CARD", step.path("context").path("step").asText());
    String retryId = step.path("context").path("serviceId").asText();
    assertFalse(retryId.isEmpty());
    assertNotEquals(acceptedId, retryId);
    assertEquals(acceptedId, lastOf(MessageCategoryType.PAYMENT), "the retry waits for the reply");
    reply(id, operationId, step);

    client.awaitOperation(id, operationId, "succeeded", Duration.ofSeconds(10));
    assertEquals(retryId, lastOf(MessageCategoryType.PAYMENT));
  }

  @Test
  void aStoredValueLoadIsAcceptedWithTheServiceIdItIsSentWith() throws Exception {
    String id = sessionWithItem();
    HostClient.Response accepted =
        submit(
            id,
            "{'type':'storedValueLoad','amount':'25.00','card':{'storedValueId':'6006491234',"
                + "'identificationType':'PAN','entryMode':'KEYED'}}");
    String serviceId = accepted.text("serviceId");
    assertFalse(serviceId.isEmpty());

    client.awaitOperation(id, accepted.text("id"), "succeeded", Duration.ofSeconds(10));
    assertEquals(serviceId, lastOf(MessageCategoryType.STORED_VALUE));
  }

  @Test
  void operationsThatSendNoSingleMoneyRequestCarryNoServiceId() throws Exception {
    String id = sessionWithItem();
    assertTrue(submit(id, "{'type':'settle'}").body.path("serviceId").isMissingNode());
  }

  private String sessionWithItem() throws Exception {
    String id =
        client
            .post(
                "/v1/sessions",
                json(
                    "{'kind':'terminal','saleId':'LANE-1','poiId':'SIM-POI','currency':'USD',"
                        + "'autoDisplay':false}"))
            .expect(201)
            .text("id");
    client
        .post(
            "/v1/sessions/" + id + "/basket/items",
            json("{'sku':'SKU-1','description':'Item','unitPrice':'20.00'}"))
        .expect(200);
    return id;
  }

  private HostClient.Response submit(String id, String body) throws Exception {
    return client.post("/v1/sessions/" + id + "/operations", json(body)).expect(202);
  }

  private JsonNode awaitStep(String id, String operationId) throws Exception {
    return awaitStep(id, operationId, "BEFORE_STEP");
  }

  private JsonNode awaitStep(String id, String operationId, String kind) throws Exception {
    long deadline = System.nanoTime() + Duration.ofSeconds(10).toNanos();
    JsonNode last = null;
    while (System.nanoTime() < deadline) {
      last = client.get("/v1/sessions/" + id + "/operations/" + operationId).expect(200).body;
      JsonNode step = last.path("pendingStep");
      if ("awaitingReply".equals(last.path("status").asText())
          && kind.equals(step.path("kind").asText())) {
        return step;
      }
      Thread.sleep(25);
    }
    throw new AssertionError("no pending " + kind + " step; last: " + last);
  }

  private void reply(String id, String operationId, JsonNode step) throws Exception {
    client
        .post(
            "/v1/sessions/" + id + "/operations/" + operationId + "/reply",
            json("{'stepId':'" + step.path("stepId").asText() + "'}"))
        .expect(200);
  }

  /** The {@code ServiceID} of the last request of {@code category}, or empty when none was sent. */
  private String lastOf(MessageCategoryType category) {
    String serviceId = "";
    for (SaleToPOIRequest request : sent) {
      if (request.getMessageHeader().getMessageCategory() == category) {
        serviceId = request.getMessageHeader().getServiceID();
      }
    }
    return serviceId;
  }
}
