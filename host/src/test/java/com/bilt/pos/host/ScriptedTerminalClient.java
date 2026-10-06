package com.bilt.pos.host;

import com.bilt.pos.nexo.client.BiltNexoClientException;
import com.bilt.pos.nexo.client.TerminalClient;
import com.bilt.pos.nexo.model.LoyaltyRequest;
import com.bilt.pos.nexo.model.MessageCategoryType;
import com.bilt.pos.nexo.model.MessageHeader;
import com.bilt.pos.nexo.model.NexoTerminalAPI;
import com.bilt.pos.nexo.model.SaleToPOIRequest;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * A terminal that answers from a script. Responses are keyed by message category, and for loyalty
 * requests by the loyalty transaction type too ({@code Loyalty/Rebate}, {@code Loyalty/Award}), so
 * one client can carry a whole settlement. A category with no script fails the request the way an
 * unreachable terminal would. {@link #hold(MessageCategoryType)} parks the next request of a
 * category until {@link #release()} so a test can observe an operation mid-flight.
 */
final class ScriptedTerminalClient implements TerminalClient {

  static final String ADMIN_OK =
      "{\"SaleToPOIResponse\":{\"MessageHeader\":{\"ProtocolVersion\":\"3.0\"},"
          + "\"AdminResponse\":{\"Response\":{\"Result\":\"Success\"}}}}";

  static final String REBATE_OK =
      "{\"SaleToPOIResponse\":{\"LoyaltyResponse\":{"
          + "\"Response\":{\"Result\":\"Success\"},"
          + "\"POIData\":{\"POITransactionID\":{\"TransactionID\":\"POI-RB-1\","
          + "\"TimeStamp\":\"2026-07-20T10:00:01Z\"}},"
          + "\"LoyaltyResult\":[{\"Rebates\":{\"TotalRebate\":10.00,"
          + "\"RebateLabel\":\"Gold Member\","
          + "\"SaleItemRebate\":[{\"ItemID\":1,\"ProductCode\":\"SKU-1\","
          + "\"ItemAmount\":10.00,\"RebateLabel\":\"Gold: $10 off\"}]}}]}}}";

  static final String AWARD_OK =
      "{\"SaleToPOIResponse\":{\"LoyaltyResponse\":{"
          + "\"Response\":{\"Result\":\"Success\"},"
          + "\"POIData\":{\"POITransactionID\":{\"TransactionID\":\"POI-AW-1\"}},"
          + "\"LoyaltyResult\":[{\"CurrentBalance\":789,"
          + "\"LoyaltyAmount\":{\"AmountValue\":89,\"LoyaltyUnit\":\"Point\"}}]}}}";

  static final String INPUT_CONFIRMED =
      "{\"SaleToPOIResponse\":{\"InputResponse\":{"
          + "\"InputResult\":{\"Response\":{\"Result\":\"Success\"},"
          + "\"Input\":{\"ConfirmedFlag\":true}}}}}";

  static String paymentOk(String poiTxn, String authorized) {
    return "{\"SaleToPOIResponse\":{\"PaymentResponse\":{"
        + "\"Response\":{\"Result\":\"Success\"},"
        + "\"POIData\":{\"POITransactionID\":{\"TransactionID\":\""
        + poiTxn
        + "\",\"TimeStamp\":\"2026-07-20T10:00:03Z\"}},"
        + "\"PaymentResult\":{"
        + "\"AmountsResp\":{\"Currency\":\"USD\",\"AuthorizedAmount\":"
        + authorized
        + "},"
        + "\"PaymentAcquirerData\":{\"ApprovalCode\":\"APPR7\","
        + "\"AcquirerTransactionID\":{\"TransactionID\":\"ACQ-1\"}},"
        + "\"PaymentInstrumentData\":{\"CardData\":{\"PaymentBrand\":\"Visa\"}}}}}}";
  }

  private static final ObjectMapper MAPPER =
      new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

  private final Map<String, String> scripts = new ConcurrentHashMap<>();
  private final ConcurrentLinkedQueue<SaleToPOIRequest> requests = new ConcurrentLinkedQueue<>();
  private volatile MessageCategoryType held;
  private volatile CountDownLatch holdReached = new CountDownLatch(1);
  private volatile CountDownLatch release = new CountDownLatch(1);

  ScriptedTerminalClient reply(MessageCategoryType category, String responseJson) {
    scripts.put(category.name(), responseJson);
    return this;
  }

  /** Scripts one loyalty transaction type, for example {@code "Rebate"} or {@code "Award"}. */
  ScriptedTerminalClient replyLoyalty(String transactionType, String responseJson) {
    scripts.put(MessageCategoryType.LOYALTY.name() + "/" + transactionType, responseJson);
    return this;
  }

  /** Parks the next request of the category until {@link #release()}. */
  ScriptedTerminalClient hold(MessageCategoryType category) {
    held = category;
    holdReached = new CountDownLatch(1);
    release = new CountDownLatch(1);
    return this;
  }

  boolean awaitHeld(Duration timeout) throws InterruptedException {
    return holdReached.await(timeout.toMillis(), TimeUnit.MILLISECONDS);
  }

  void release() {
    held = null;
    release.countDown();
  }

  List<SaleToPOIRequest> requests() {
    return new ArrayList<>(requests);
  }

  @Override
  public NexoTerminalAPI request(NexoTerminalAPI request) throws BiltNexoClientException {
    return request(request, null);
  }

  @Override
  public NexoTerminalAPI request(NexoTerminalAPI request, Duration timeout)
      throws BiltNexoClientException {
    SaleToPOIRequest body = request.getSaleToPOIRequest();
    MessageHeader header = body == null ? null : body.getMessageHeader();
    MessageCategoryType category = header == null ? null : header.getMessageCategory();
    requests.add(body);
    if (category != null && category == held) {
      holdReached.countDown();
      try {
        if (!release.await(30, TimeUnit.SECONDS)) {
          throw new BiltNexoClientException("held request was never released");
        }
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new BiltNexoClientException("interrupted while held");
      }
    }
    String script = category == null ? null : scripts.get(key(body, category));
    if (script == null && category != null) {
      script = scripts.get(category.name());
    }
    if (script == null) {
      throw new BiltNexoClientException("no scripted response for " + category);
    }
    try {
      return MAPPER.readValue(script, NexoTerminalAPI.class);
    } catch (IOException e) {
      throw new BiltNexoClientException("malformed scripted response for " + category, e);
    }
  }

  private static String key(SaleToPOIRequest body, MessageCategoryType category) {
    LoyaltyRequest loyalty = body.getLoyaltyRequest();
    if (category == MessageCategoryType.LOYALTY
        && loyalty != null
        && loyalty.getLoyaltyTransaction() != null
        && loyalty.getLoyaltyTransaction().getLoyaltyTransactionType() != null) {
      return category.name()
          + "/"
          + loyalty.getLoyaltyTransaction().getLoyaltyTransactionType().toValue();
    }
    return category.name();
  }
}
