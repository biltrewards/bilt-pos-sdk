package com.bilt.pos.nexo.sim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bilt.pos.nexo.client.BiltNexoClientException;
import com.bilt.pos.nexo.model.NexoTerminalAPI;
import com.bilt.pos.nexo.model.SaleToPOIResponse;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Base64;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SimulatedTerminalClientTest {

  private static final ObjectMapper MAPPER =
      new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

  @Test
  void recordsPaymentsAndResolvesThemByServiceId() throws Exception {
    SimulatedTerminalClient terminal = SimulatedTerminalClient.builder().build();

    SaleToPOIResponse payment =
        terminal
            .request(
                request(
                    "Payment",
                    "payment-1",
                    "{\"PaymentRequest\":{\"PaymentData\":{\"PaymentType\":\"Normal\"},"
                        + "\"PaymentTransaction\":{\"AmountsReq\":{\"RequestedAmount\":41.24,"
                        + "\"Currency\":\"USD\"}}}}"))
            .getSaleToPOIResponse();

    assertEquals("Success", payment.getPaymentResponse().getResponse().getResult().toValue());
    assertTrue(
        payment
            .getPaymentResponse()
            .getPoiData()
            .getPoiTransactionID()
            .getTransactionID()
            .startsWith("SIM-"));
    assertEquals("Response", payment.getMessageHeader().getMessageType().toValue());
    assertEquals("payment-1", payment.getMessageHeader().getServiceID());

    SaleToPOIResponse status =
        terminal
            .request(
                request(
                    "TransactionStatus",
                    "status-1",
                    "{\"TransactionStatusRequest\":{\"MessageReference\":"
                        + "{\"ServiceID\":\"payment-1\",\"MessageCategory\":\"Payment\"}}}"))
            .getSaleToPOIResponse();
    assertEquals(
        payment.getPaymentResponse().getPoiData().getPoiTransactionID().getTransactionID(),
        status
            .getTransactionStatusResponse()
            .getRepeatedMessageResponse()
            .getRepeatedResponseMessageBody()
            .getPaymentResponse()
            .getPoiData()
            .getPoiTransactionID()
            .getTransactionID());
  }

  @Test
  void enforcesRefundAndReversalRules() throws Exception {
    SimulatedTerminalClient terminal = SimulatedTerminalClient.builder().build();
    SaleToPOIResponse sale =
        terminal
            .request(request("Payment", "sale", paymentBody("41.24", null)))
            .getSaleToPOIResponse();
    String poiId = sale.getPaymentResponse().getPoiData().getPoiTransactionID().getTransactionID();

    SaleToPOIResponse unknownRefund =
        terminal
            .request(request("Payment", "refund-unknown", paymentBody("1.00", "SIM-missing")))
            .getSaleToPOIResponse();
    assertEquals(
        "NotFound", unknownRefund.getPaymentResponse().getResponse().getErrorCondition().toValue());

    SaleToPOIResponse partialRefund =
        terminal
            .request(request("Payment", "refund-partial", paymentBody("40.00", poiId)))
            .getSaleToPOIResponse();
    assertEquals("Success", partialRefund.getPaymentResponse().getResponse().getResult().toValue());
    SaleToPOIResponse excessiveRefund =
        terminal
            .request(request("Payment", "refund-excess", paymentBody("2.00", poiId)))
            .getSaleToPOIResponse();
    assertEquals(
        "Refusal",
        excessiveRefund.getPaymentResponse().getResponse().getErrorCondition().toValue());

    SaleToPOIResponse unknownReversal =
        terminal
            .request(request("Reversal", "void-unknown", reversalBody("SIM-missing")))
            .getSaleToPOIResponse();
    assertEquals(
        "NotFound",
        unknownReversal.getReversalResponse().getResponse().getErrorCondition().toValue());
    SaleToPOIResponse voidableSale =
        terminal
            .request(request("Payment", "sale-to-void", paymentBody("3.00", null)))
            .getSaleToPOIResponse();
    String voidablePoiId =
        voidableSale.getPaymentResponse().getPoiData().getPoiTransactionID().getTransactionID();
    SaleToPOIResponse reversal =
        terminal
            .request(request("Reversal", "void-sale", reversalBody(voidablePoiId)))
            .getSaleToPOIResponse();
    assertEquals("Success", reversal.getReversalResponse().getResponse().getResult().toValue());
    SaleToPOIResponse secondReversal =
        terminal
            .request(request("Reversal", "void-sale-again", reversalBody(voidablePoiId)))
            .getSaleToPOIResponse();
    assertEquals(
        "Refusal",
        secondReversal.getReversalResponse().getResponse().getErrorCondition().toValue());
  }

  @Test
  void refusesVoidingAPaymentAfterPartialRefund() throws Exception {
    SimulatedTerminalClient terminal = SimulatedTerminalClient.builder().build();
    SaleToPOIResponse sale =
        terminal
            .request(request("Payment", "sale-after-refund", paymentBody("20.00", null)))
            .getSaleToPOIResponse();
    String poiId = sale.getPaymentResponse().getPoiData().getPoiTransactionID().getTransactionID();

    SaleToPOIResponse partialRefund =
        terminal
            .request(request("Payment", "refund-5", paymentBody("5.00", poiId)))
            .getSaleToPOIResponse();
    assertEquals("Success", partialRefund.getPaymentResponse().getResponse().getResult().toValue());

    SaleToPOIResponse reversal =
        terminal
            .request(request("Reversal", "void-after-refund", reversalBody(poiId)))
            .getSaleToPOIResponse();
    assertEquals(
        "Refusal", reversal.getReversalResponse().getResponse().getErrorCondition().toValue());

    SaleToPOIResponse status =
        terminal
            .request(
                request(
                    "TransactionStatus",
                    "status-after-refused-void",
                    "{\"TransactionStatusRequest\":{\"MessageReference\":"
                        + "{\"ServiceID\":\"sale-after-refund\",\"MessageCategory\":\"Payment\"}}}"))
            .getSaleToPOIResponse();
    assertEquals(
        "Success",
        status
            .getTransactionStatusResponse()
            .getRepeatedMessageResponse()
            .getRepeatedResponseMessageBody()
            .getPaymentResponse()
            .getResponse()
            .getResult()
            .toValue());

    SaleToPOIResponse remainingRefund =
        terminal
            .request(request("Payment", "refund-remaining", paymentBody("15.00", poiId)))
            .getSaleToPOIResponse();
    assertEquals(
        "Success", remainingRefund.getPaymentResponse().getResponse().getResult().toValue());
  }

  @Test
  void declinesTheDocumentedAmountAndApprovesTheNextCent() throws Exception {
    SimulatedTerminalClient terminal = SimulatedTerminalClient.builder().build();
    SaleToPOIResponse declined =
        terminal
            .request(request("Payment", "declined", paymentBody("41.23", null)))
            .getSaleToPOIResponse();
    assertEquals(
        "Refusal", declined.getPaymentResponse().getResponse().getErrorCondition().toValue());
    SaleToPOIResponse approved =
        terminal
            .request(request("Payment", "approved", paymentBody("41.24", null)))
            .getSaleToPOIResponse();
    assertEquals("Success", approved.getPaymentResponse().getResponse().getResult().toValue());

    SaleToPOIResponse declinedStatus =
        terminal
            .request(
                request(
                    "TransactionStatus",
                    "declined-status",
                    "{\"TransactionStatusRequest\":{\"MessageReference\":{\"ServiceID\":\"declined\"}}}"))
            .getSaleToPOIResponse();
    assertEquals(
        "NotFound",
        declinedStatus.getTransactionStatusResponse().getResponse().getErrorCondition().toValue());
  }

  @Test
  void timeoutIsNotRecordedAndDroppedApprovalIsRecoverable() throws Exception {
    SimulatedTerminalClient terminal = SimulatedTerminalClient.builder().build();
    terminal.timeoutNextRequest();
    BiltNexoClientException timeout =
        assertThrows(
            BiltNexoClientException.class,
            () -> terminal.request(request("Payment", "timeout", paymentBody("9.00", null))));
    assertTrue(hasCause(timeout, SocketTimeoutException.class));

    SaleToPOIResponse timeoutStatus =
        terminal
            .request(
                request(
                    "TransactionStatus",
                    "status-timeout",
                    "{\"TransactionStatusRequest\":{\"MessageReference\":{\"ServiceID\":\"timeout\"}}}"))
            .getSaleToPOIResponse();
    assertEquals(
        "NotFound",
        timeoutStatus.getTransactionStatusResponse().getResponse().getErrorCondition().toValue());

    terminal.dropConnectionAfterNextApproval();
    BiltNexoClientException dropped =
        assertThrows(
            BiltNexoClientException.class,
            () -> terminal.request(request("Payment", "dropped", paymentBody("9.00", null))));
    assertTrue(hasCause(dropped, IOException.class));
    SaleToPOIResponse recovered =
        terminal
            .request(
                request(
                    "TransactionStatus",
                    "status-dropped",
                    "{\"TransactionStatusRequest\":{\"MessageReference\":{\"ServiceID\":\"dropped\"}}}"))
            .getSaleToPOIResponse();
    assertEquals(
        "Success",
        recovered
            .getTransactionStatusResponse()
            .getRepeatedMessageResponse()
            .getRepeatedResponseMessageBody()
            .getPaymentResponse()
            .getResponse()
            .getResult()
            .toValue());
  }

  @Test
  void reportsInProgressAndHonorsMatchingAbort() throws Exception {
    SimulatedTerminalClient terminal =
        SimulatedTerminalClient.builder().customerDelay(Duration.ofSeconds(5)).build();
    ExecutorService executor = Executors.newSingleThreadExecutor();
    try {
      CompletableFuture<NexoTerminalAPI> payment =
          CompletableFuture.supplyAsync(
              () -> {
                try {
                  return terminal.request(request("Payment", "waiting", paymentBody("7.00", null)));
                } catch (IOException | BiltNexoClientException e) {
                  throw new RuntimeException(e);
                }
              },
              executor);
      assertEquals("InProgress", awaitStatus(terminal, "waiting", "InProgress"));

      assertEquals(
          null,
          terminal.request(
              request(
                  "Abort",
                  "abort-1",
                  "{\"AbortRequest\":{\"MessageReference\":{\"ServiceID\":\"waiting\"}}}")));
      SaleToPOIResponse aborted = payment.get(2, TimeUnit.SECONDS).getSaleToPOIResponse();
      assertEquals(
          "Aborted", aborted.getPaymentResponse().getResponse().getErrorCondition().toValue());
      assertEquals("NotFound", awaitStatus(terminal, "waiting", "NotFound"));
    } finally {
      executor.shutdownNow();
    }
  }

  @Test
  void nonMatchingAbortLeavesCustomerRequestInProgress() throws Exception {
    SimulatedTerminalClient terminal =
        SimulatedTerminalClient.builder().customerDelay(Duration.ofMillis(400)).build();
    ExecutorService executor = Executors.newSingleThreadExecutor();
    try {
      CompletableFuture<NexoTerminalAPI> payment =
          CompletableFuture.supplyAsync(
              () -> {
                try {
                  return terminal.request(request("Payment", "waiting", paymentBody("8.00", null)));
                } catch (IOException | BiltNexoClientException e) {
                  throw new RuntimeException(e);
                }
              },
              executor);
      assertEquals("InProgress", awaitStatus(terminal, "waiting", "InProgress"));
      terminal.request(
          request(
              "Abort",
              "abort-other",
              "{\"AbortRequest\":{\"MessageReference\":{\"ServiceID\":\"other\"}}}"));
      assertEquals("InProgress", awaitStatus(terminal, "waiting", "InProgress"));
      assertEquals(
          "Success",
          payment
              .get(2, TimeUnit.SECONDS)
              .getSaleToPOIResponse()
              .getPaymentResponse()
              .getResponse()
              .getResult()
              .toValue());
    } finally {
      executor.shutdownNow();
    }
  }

  @Test
  void identifiesMembersByPhoneOrAccountAndReturnsNotFoundForUnknown() throws Exception {
    SimulatedTerminalClient terminal = SimulatedTerminalClient.builder().build();
    SaleToPOIResponse acquisition =
        terminal
            .request(
                request(
                    "CardAcquisition",
                    "acquire",
                    "{\"CardAcquisitionRequest\":{\"CardAcquisitionTransaction\":"
                        + "{\"LoyaltyHandling\":\"Proposed\"}}}"))
            .getSaleToPOIResponse();
    assertEquals(
        "mbr_sim_1",
        acquisition
            .getCardAcquisitionResponse()
            .getLoyaltyAccount()[0]
            .getLoyaltyAccountID()
            .getLoyaltyID());
    JsonNode additional =
        MAPPER.readTree(
            Base64.getDecoder()
                .decode(
                    acquisition
                        .getCardAcquisitionResponse()
                        .getResponse()
                        .getAdditionalResponse()));
    assertEquals(
        "Sam", additional.path("shopperContext").path("shopper").path("firstName").asText());
    assertEquals("rwd_sim_1", additional.path("rewards").get(0).path("rewardRef").asText());

    assertEquals(
        12000,
        terminal
            .request(request("BalanceInquiry", "phone", balanceBody("PhoneNumber", "+15550000001")))
            .getSaleToPOIResponse()
            .getBalanceInquiryResponse()
            .getLoyaltyAccountStatus()
            .getCurrentBalance()
            .intValue());
    assertEquals(
        "mbr_sim_1",
        terminal
            .request(
                request("BalanceInquiry", "account", balanceBody("AccountNumber", "acct_sim_1")))
            .getSaleToPOIResponse()
            .getBalanceInquiryResponse()
            .getLoyaltyAccountStatus()
            .getLoyaltyAccount()
            .getLoyaltyAccountID()
            .getLoyaltyID());
    assertEquals(
        "NotFound",
        terminal
            .request(request("BalanceInquiry", "missing", balanceBody("PhoneNumber", "unknown")))
            .getSaleToPOIResponse()
            .getBalanceInquiryResponse()
            .getResponse()
            .getErrorCondition()
            .toValue());
  }

  @Test
  void awardsAndRedeemsFixturePoints() throws Exception {
    SimulatedTerminalClient terminal = SimulatedTerminalClient.builder().build();
    SaleToPOIResponse award =
        terminal
            .request(
                request(
                    "Loyalty",
                    "award",
                    "{\"LoyaltyRequest\":{\"LoyaltyTransaction\":{\"LoyaltyTransactionType\":\"Award\","
                        + "\"Currency\":\"USD\",\"TotalAmount\":10.9},\"LoyaltyData\":["
                        + "{\"LoyaltyAccountID\":{\"LoyaltyID\":\"mbr_sim_1\"}}]}}"))
            .getSaleToPOIResponse();
    assertEquals(
        10, award.getLoyaltyResponse().getLoyaltyResult()[0].getLoyaltyAmount().getAmountValue());
    assertEquals(12010, terminal.points("mbr_sim_1"));

    String rewardRefs =
        Base64.getEncoder()
            .encodeToString("{\"rewardRefs\":[\"rwd_sim_1\"]}".getBytes(StandardCharsets.UTF_8));
    SaleToPOIResponse redemption =
        terminal
            .request(
                request(
                    "Loyalty",
                    "redeem",
                    "{\"LoyaltyRequest\":{\"LoyaltyTransaction\":{\"LoyaltyTransactionType\":\"Redemption\","
                        + "\"Currency\":\"USD\",\"TotalAmount\":20.0},\"LoyaltyData\":["
                        + "{\"LoyaltyAccountID\":{\"LoyaltyID\":\"mbr_sim_1\"},"
                        + "\"LoyaltyAmount\":{\"AmountValue\":0.0,\"Currency\":\"USD\","
                        + "\"LoyaltyUnit\":\"Monetary\"}}],\"SaleData\":{\"SaleToPOIData\":\""
                        + rewardRefs
                        + "\"}}}"))
            .getSaleToPOIResponse();
    assertEquals("Success", redemption.getLoyaltyResponse().getResponse().getResult().toValue());
    assertEquals(
        5.0,
        redemption.getLoyaltyResponse().getLoyaltyResult()[0].getLoyaltyAmount().getAmountValue());
    assertEquals(11510, terminal.points("mbr_sim_1"));
    JsonNode pointsUsed =
        MAPPER.readTree(
            Base64.getDecoder()
                .decode(redemption.getLoyaltyResponse().getResponse().getAdditionalResponse()));
    assertEquals(500, pointsUsed.path("pointsUsed").asInt());
  }

  @Test
  void managesSessionAndLatestDisplay() throws Exception {
    SimulatedTerminalClient terminal = SimulatedTerminalClient.builder().build();
    terminal.request(
        request(
            "Admin",
            "start",
            "{\"AdminRequest\":{\"ServiceIdentification\":\"BiltSession,Start,v1,test-session\"}}"));
    assertEquals("test-session", terminal.activeSessionId().orElseThrow());

    terminal.request(
        request(
            "Display",
            "display",
            "{\"DisplayRequest\":{\"DisplayOutput\":[{\"Device\":\"CustomerDisplay\","
                + "\"InfoQualify\":\"Display\"}]}}"));
    assertTrue(terminal.latestDisplay().isPresent());

    terminal.request(
        request(
            "Admin",
            "end",
            "{\"AdminRequest\":{\"ServiceIdentification\":\"BiltSession,End,v1,test-session\"}}"));
    assertFalse(terminal.activeSessionId().isPresent());
    assertFalse(terminal.latestDisplay().isPresent());
  }

  @Test
  void loadsCustomMembersAndRejectsInvalidFiles(@TempDir Path directory) throws Exception {
    Path members = directory.resolve("members.json");
    Files.writeString(
        members,
        "{\"members\":[{\"memberId\":\"custom\",\"accountId\":\"custom-account\","
            + "\"phone\":\"555-0100\",\"firstName\":\"Custom\",\"lastName\":\"User\","
            + "\"tierName\":\"Silver\",\"points\":25,\"rewards\":[]}]}");
    SimulatedTerminalClient terminal = SimulatedTerminalClient.builder().members(members).build();
    assertEquals(25, terminal.points("custom"));
    assertThrows(
        IllegalArgumentException.class,
        () -> SimulatedTerminalClient.builder().members(directory.resolve("missing.json")).build());
    Path invalid = directory.resolve("invalid.json");
    Files.writeString(invalid, "{\"members\":[{}]}");
    assertThrows(
        IllegalArgumentException.class,
        () -> SimulatedTerminalClient.builder().members(invalid).build());
  }

  @Test
  void returnsConfirmationInputAndRejectsUnsupportedCategories() throws Exception {
    SimulatedTerminalClient terminal =
        SimulatedTerminalClient.builder().confirmInputs(false).build();
    SaleToPOIResponse response =
        terminal
            .request(
                request(
                    "Input",
                    "input",
                    "{\"InputRequest\":{\"InputData\":{\"InputCommand\":\"GetConfirmation\"}}}"))
            .getSaleToPOIResponse();
    assertFalse(response.getInputResponse().getInputResult().getInput().getConfirmedFlag());
    assertNotNull(
        response.getInputResponse().getInputResult().getResponse().getAdditionalResponse());
    BiltNexoClientException unsupported =
        assertThrows(
            BiltNexoClientException.class,
            () -> terminal.request(request("PIN", "pin", "{\"PINRequest\":{}}")));
    assertTrue(unsupported.getMessage().contains("PIN"));
  }

  @Test
  void acquiresCardDataWhenLoyaltyIsForbidden() throws Exception {
    SaleToPOIResponse response =
        SimulatedTerminalClient.builder()
            .build()
            .request(
                request(
                    "CardAcquisition",
                    "card",
                    "{\"CardAcquisitionRequest\":{\"CardAcquisitionTransaction\":"
                        + "{\"LoyaltyHandling\":\"Forbidden\"}}}"))
            .getSaleToPOIResponse();
    assertEquals(
        "Visa",
        response
            .getCardAcquisitionResponse()
            .getPaymentInstrumentData()
            .getCardData()
            .getPaymentBrand());
    assertEquals(
        "Tapped",
        response
            .getCardAcquisitionResponse()
            .getPaymentInstrumentData()
            .getCardData()
            .getEntryMode()[0]
            .toValue());
    assertEquals(
        "simulated-visa-token",
        response
            .getCardAcquisitionResponse()
            .getPaymentInstrumentData()
            .getCardData()
            .getPaymentToken()
            .getTokenValue());
  }

  @Test
  void tracksStoredValueBalancesAndReversesLoads() throws Exception {
    SimulatedTerminalClient terminal = SimulatedTerminalClient.builder().build();
    SaleToPOIResponse loaded =
        terminal
            .request(
                request(
                    "StoredValue",
                    "load",
                    "{\"StoredValueRequest\":{\"StoredValueData\":[{"
                        + "\"StoredValueTransactionType\":\"Load\",\"Currency\":\"USD\","
                        + "\"ItemAmount\":25.00,\"StoredValueAccountID\":"
                        + "{\"StoredValueID\":\"gift-1\"}}]}}"))
            .getSaleToPOIResponse();
    String poiId =
        loaded.getStoredValueResponse().getPoiData().getPoiTransactionID().getTransactionID();
    assertEquals(
        25.0,
        loaded
            .getStoredValueResponse()
            .getStoredValueResult()[0]
            .getStoredValueAccountStatus()
            .getCurrentBalance());

    SaleToPOIResponse balance =
        terminal
            .request(
                request(
                    "BalanceInquiry",
                    "gift-balance",
                    "{\"BalanceInquiryRequest\":{\"PaymentAccountReq\":{\"PaymentInstrumentData\":"
                        + "{\"PaymentInstrumentType\":\"StoredValue\",\"StoredValueAccountID\":"
                        + "{\"StoredValueID\":\"gift-1\"}}}}}"))
            .getSaleToPOIResponse();
    assertEquals(
        25.0, balance.getBalanceInquiryResponse().getPaymentAccountStatus().getCurrentBalance());

    terminal.request(request("Reversal", "reverse-load", reversalBody(poiId)));
    SaleToPOIResponse reversedBalance =
        terminal
            .request(
                request(
                    "BalanceInquiry",
                    "gift-balance-after-reversal",
                    "{\"BalanceInquiryRequest\":{\"PaymentAccountReq\":{\"PaymentInstrumentData\":"
                        + "{\"PaymentInstrumentType\":\"StoredValue\",\"StoredValueAccountID\":"
                        + "{\"StoredValueID\":\"gift-1\"}}}}}"))
            .getSaleToPOIResponse();
    assertEquals(
        0.0,
        reversedBalance.getBalanceInquiryResponse().getPaymentAccountStatus().getCurrentBalance());
  }

  private static String awaitStatus(
      SimulatedTerminalClient terminal, String serviceId, String expectedStatus) throws Exception {
    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
    String status = null;
    while (System.nanoTime() < deadline) {
      SaleToPOIResponse response =
          terminal
              .request(
                  request(
                      "TransactionStatus",
                      "status-" + serviceId,
                      "{\"TransactionStatusRequest\":{\"MessageReference\":{\"ServiceID\":\""
                          + serviceId
                          + "\"}}}"))
              .getSaleToPOIResponse();
      status = response.getTransactionStatusResponse().getResponse().getErrorCondition().toValue();
      if (expectedStatus.equals(status)) {
        return status;
      }
      Thread.sleep(10);
    }
    throw new AssertionError(
        "transaction did not reach " + expectedStatus + " (last status: " + status + ")");
  }

  private static NexoTerminalAPI request(String category, String serviceId, String body)
      throws IOException {
    ObjectNode root = MAPPER.createObjectNode();
    ObjectNode saleRequest = root.putObject("SaleToPOIRequest");
    ObjectNode header = saleRequest.putObject("MessageHeader");
    header.put("ProtocolVersion", "3.0");
    header.put("MessageClass", "Service");
    header.put("MessageCategory", category);
    header.put("MessageType", "Request");
    header.put("ServiceID", serviceId);
    header.put("SaleID", "SIM-SALE");
    header.put("POIID", "SIM-POI");
    saleRequest.setAll((ObjectNode) MAPPER.readTree(body));
    return MAPPER.convertValue(root, NexoTerminalAPI.class);
  }

  private static String paymentBody(String amount, String originalPoiId) {
    return "{\"PaymentRequest\":{\"PaymentData\":{\"PaymentType\":\""
        + (originalPoiId == null ? "Normal" : "Refund")
        + "\"},\"PaymentTransaction\":{\"AmountsReq\":{\"RequestedAmount\":"
        + amount
        + ",\"Currency\":\"USD\"}"
        + (originalPoiId == null
            ? ""
            : ",\"OriginalPOITransaction\":{\"POITransactionID\":{\"TransactionID\":\""
                + originalPoiId
                + "\"}}")
        + "}}}";
  }

  private static String reversalBody(String poiId) {
    return "{\"ReversalRequest\":{\"OriginalPOITransaction\":{\"POITransactionID\":"
        + "{\"TransactionID\":\""
        + poiId
        + "\"}}}}";
  }

  private static String balanceBody(String type, String value) {
    return "{\"BalanceInquiryRequest\":{\"LoyaltyAccountReq\":{\"LoyaltyAccountID\":"
        + "{\"LoyaltyID\":\""
        + value
        + "\",\"IdentificationType\":\""
        + type
        + "\"}}}}";
  }

  private static boolean hasCause(Throwable failure, Class<? extends Throwable> type) {
    for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
      if (type.isInstance(cause)) {
        return true;
      }
    }
    return false;
  }
}
