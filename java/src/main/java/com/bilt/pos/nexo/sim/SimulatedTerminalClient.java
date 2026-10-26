/*
 *    ____  _ _ _
 *   | __ )(_) | |_
 *   |  _ \| | | __|
 *   | |_) | | | |_
 *   |____/|_|_|\__|
 *
 *   Bilt POS SDK
 */
package com.bilt.pos.nexo.sim;

import com.bilt.pos.nexo.client.BiltNexoClientException;
import com.bilt.pos.nexo.client.TerminalClient;
import com.bilt.pos.nexo.model.DisplayRequest;
import com.bilt.pos.nexo.model.LoyaltyTransactionTypeEnum;
import com.bilt.pos.nexo.model.MessageCategoryType;
import com.bilt.pos.nexo.model.MessageHeader;
import com.bilt.pos.nexo.model.NexoTerminalAPI;
import com.bilt.pos.nexo.model.PaymentInstrumentTypeEnum;
import com.bilt.pos.nexo.model.PaymentTypeEnum;
import com.bilt.pos.nexo.model.SaleToPOIRequest;
import com.bilt.pos.nexo.model.StoredValueTransactionTypeEnum;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * In-memory Nexo terminal for development and tests. It models a small member file, payment and
 * stored-value balances, and a transaction ledger without opening a network connection.
 */
public final class SimulatedTerminalClient implements TerminalClient {

  private static final String DEFAULT_MEMBERS = "/com/bilt/pos/nexo/sim/members.json";
  private static final String VISA_MASKED_PAN = "411111******1111";
  private static final String VISA_TOKEN = "simulated-visa-token";
  private static final ObjectMapper MAPPER =
      new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

  private final Object lock = new Object();
  private final Duration customerDelay;
  private final boolean confirmInputs;
  private final List<Member> members;
  private final Map<String, Member> membersById = new LinkedHashMap<>();
  private final Map<String, BigDecimal> storedValueBalances = new HashMap<>();
  private final Map<String, LedgerEntry> ledgerByPoiId = new HashMap<>();
  private final Map<String, LedgerEntry> ledgerByServiceId = new HashMap<>();
  private final Map<String, WaitState> waitingByServiceId = new HashMap<>();

  private boolean timeoutNextRequest;
  private boolean dropAfterNextApproval;
  private long transactionCounter;
  private String activeSessionId;
  private DisplayRequest latestDisplay;

  private SimulatedTerminalClient(Builder builder) {
    customerDelay = builder.customerDelay;
    confirmInputs = builder.confirmInputs;
    members = readMembers(builder.membersFile);
    for (Member member : members) {
      membersById.put(member.memberId, member);
    }
  }

  /** Creates a builder with the built-in sample member file. */
  public static Builder builder() {
    return new Builder();
  }

  /** Makes the next non-abort request fail with a simulated read timeout. */
  public void timeoutNextRequest() {
    synchronized (lock) {
      timeoutNextRequest = true;
    }
  }

  /** Makes the next recorded approval fail after it has been added to the transaction ledger. */
  public void dropConnectionAfterNextApproval() {
    synchronized (lock) {
      dropAfterNextApproval = true;
    }
  }

  /** Returns the active simulated session identifier, if one has been started. */
  public Optional<String> activeSessionId() {
    synchronized (lock) {
      return Optional.ofNullable(activeSessionId);
    }
  }

  /** Returns a copy of the latest display request, if one has been received. */
  public Optional<DisplayRequest> latestDisplay() {
    synchronized (lock) {
      return latestDisplay == null
          ? Optional.empty()
          : Optional.of(MAPPER.convertValue(latestDisplay, DisplayRequest.class));
    }
  }

  /** Returns the current points balance for a fixture member. */
  public int points(String memberId) {
    synchronized (lock) {
      Member member = membersById.get(memberId);
      if (member == null) {
        throw new IllegalArgumentException("unknown simulated member: " + memberId);
      }
      return member.points;
    }
  }

  @Override
  public NexoTerminalAPI request(NexoTerminalAPI request) throws BiltNexoClientException {
    return request(request, null);
  }

  @Override
  public NexoTerminalAPI request(NexoTerminalAPI request, Duration timeout)
      throws BiltNexoClientException {
    SaleToPOIRequest body = request == null ? null : request.getSaleToPOIRequest();
    MessageHeader header = body == null ? null : body.getMessageHeader();
    MessageCategoryType category = header == null ? null : header.getMessageCategory();
    if (category == null) {
      throw new BiltNexoClientException("simulated terminal received a request without a category");
    }
    if (category == MessageCategoryType.ABORT) {
      abort(body);
      return null;
    }
    synchronized (lock) {
      if (timeoutNextRequest) {
        timeoutNextRequest = false;
        SocketTimeoutException cause =
            new SocketTimeoutException("simulated terminal read timed out");
        throw new BiltNexoClientException("simulated terminal request timed out", cause);
      }
    }
    WaitState waitState =
        waitsForCustomer(body, category) ? waitForCustomer(header, timeout) : null;
    synchronized (lock) {
      try {
        if (waitState != null && waitState.aborted) {
          return failure(requestHeader(header, category), category, "Aborted");
        }
        switch (category) {
          case ADMIN:
            return admin(body, header);
          case BALANCE_INQUIRY:
            return balanceInquiry(body, header);
          case CARD_ACQUISITION:
            return cardAcquisition(body, header);
          case DIAGNOSIS:
            return diagnosis(header);
          case DISPLAY:
            return display(body, header);
          case INPUT:
            return input(body, header);
          case INPUT_UPDATE:
            return null;
          case LOYALTY:
            return loyalty(body, header);
          case PAYMENT:
            return payment(body, header);
          case REVERSAL:
            return reversal(body, header);
          case STORED_VALUE:
            return storedValue(body, header);
          case TRANSACTION_STATUS:
            return transactionStatus(body, header);
          default:
            throw new BiltNexoClientException(
                "simulated terminal does not support " + category.toValue());
        }
      } finally {
        if (waitState != null) {
          removeWaiting(header.getServiceID(), waitState);
        }
      }
    }
  }

  private boolean waitsForCustomer(SaleToPOIRequest request, MessageCategoryType category) {
    switch (category) {
      case CARD_ACQUISITION:
        return request.getCardAcquisitionRequest() != null
            && request.getCardAcquisitionRequest().getCardAcquisitionTransaction() != null
            && request
                    .getCardAcquisitionRequest()
                    .getCardAcquisitionTransaction()
                    .getLoyaltyHandling()
                != null
            && request
                .getCardAcquisitionRequest()
                .getCardAcquisitionTransaction()
                .getLoyaltyHandling()
                .toValue()
                .matches("Proposed|Required");
      case INPUT:
        return true;
      case PAYMENT:
        return request.getPaymentRequest() == null
            || request.getPaymentRequest().getPaymentData() == null
            || request.getPaymentRequest().getPaymentData().getPaymentType()
                != PaymentTypeEnum.REFUND;
      default:
        return false;
    }
  }

  private WaitState waitForCustomer(MessageHeader header, Duration timeout)
      throws BiltNexoClientException {
    WaitState waitState = new WaitState();
    String serviceId = header.getServiceID();
    synchronized (lock) {
      if (serviceId != null) {
        waitingByServiceId.put(serviceId, waitState);
      }
    }
    boolean timeoutBeforeCustomer =
        !customerDelay.isZero() && timeout != null && customerDelay.compareTo(timeout) > 0;
    Duration waitDuration = timeoutBeforeCustomer ? timeout : customerDelay;
    try {
      if (!waitDuration.isZero() || timeoutBeforeCustomer) {
        boolean signaled = waitState.signal.await(nanos(waitDuration), TimeUnit.NANOSECONDS);
        if (timeoutBeforeCustomer && !signaled) {
          synchronized (lock) {
            if (waitState.aborted) {
              return waitState;
            }
            removeWaiting(serviceId, waitState);
          }
          SocketTimeoutException cause =
              new SocketTimeoutException("simulated terminal request timed out");
          throw new BiltNexoClientException("simulated terminal request timed out", cause);
        }
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      synchronized (lock) {
        removeWaiting(serviceId, waitState);
      }
      throw new BiltNexoClientException("interrupted while awaiting simulated customer", e);
    }
    return waitState;
  }

  private void removeWaiting(String serviceId, WaitState waitState) {
    if (serviceId != null && waitingByServiceId.get(serviceId) == waitState) {
      waitingByServiceId.remove(serviceId);
    }
  }

  private void abort(SaleToPOIRequest request) {
    if (request.getAbortRequest() == null
        || request.getAbortRequest().getMessageReference() == null) {
      return;
    }
    String serviceId = request.getAbortRequest().getMessageReference().getServiceID();
    synchronized (lock) {
      WaitState waitState = waitingByServiceId.get(serviceId);
      if (waitState != null) {
        waitState.aborted = true;
        waitState.signal.countDown();
      }
    }
  }

  private NexoTerminalAPI admin(SaleToPOIRequest request, MessageHeader header) {
    String signal =
        request.getAdminRequest() == null
            ? null
            : request.getAdminRequest().getServiceIdentification();
    if (signal != null && signal.matches("BiltSession,Start,v1,.+")) {
      activeSessionId = signal.substring("BiltSession,Start,v1,".length());
    } else if (signal != null && signal.matches("BiltSession,End,v1,.+")) {
      activeSessionId = null;
      latestDisplay = null;
    }
    ObjectNode response = MAPPER.createObjectNode();
    response.set("Response", success());
    return envelope(header, MessageCategoryType.ADMIN, "AdminResponse", response);
  }

  private NexoTerminalAPI cardAcquisition(SaleToPOIRequest request, MessageHeader header) {
    ObjectNode response = success();
    ObjectNode body = MAPPER.createObjectNode();
    body.set("Response", response);
    boolean loyalty =
        request.getCardAcquisitionRequest() != null
            && request.getCardAcquisitionRequest().getCardAcquisitionTransaction() != null
            && request
                    .getCardAcquisitionRequest()
                    .getCardAcquisitionTransaction()
                    .getLoyaltyHandling()
                != null
            && !request
                .getCardAcquisitionRequest()
                .getCardAcquisitionTransaction()
                .getLoyaltyHandling()
                .toValue()
                .equals("Forbidden");
    if (loyalty && !members.isEmpty()) {
      Member member = members.get(0);
      ObjectNode account = body.putArray("LoyaltyAccount").addObject();
      ObjectNode accountId = account.putObject("LoyaltyAccountID");
      accountId.put("LoyaltyID", member.memberId);
      accountId.put("IdentificationType", "AccountNumber");
      accountId.putArray("EntryMode").add("Mobile");
      account.put("LoyaltyBrand", "Bilt");
      response.put("AdditionalResponse", loyaltyPayload(member));
    } else if (!loyalty) {
      ObjectNode card = body.putObject("PaymentInstrumentData").putObject("CardData");
      card.put("MaskedPAN", VISA_MASKED_PAN);
      card.put("PaymentBrand", "Visa");
      card.putArray("EntryMode").add("Tapped");
      card.putObject("PaymentToken").put("TokenValue", VISA_TOKEN);
    }
    return envelope(header, MessageCategoryType.CARD_ACQUISITION, "CardAcquisitionResponse", body);
  }

  private NexoTerminalAPI balanceInquiry(SaleToPOIRequest request, MessageHeader header) {
    ObjectNode body = MAPPER.createObjectNode();
    if (request.getBalanceInquiryRequest() != null
        && request.getBalanceInquiryRequest().getPaymentAccountReq() != null) {
      String cardId = storedValueId(request.getBalanceInquiryRequest().getPaymentAccountReq());
      body.set("Response", success());
      ObjectNode status = body.putObject("PaymentAccountStatus");
      status.put(
          "CurrentBalance",
          storedValueBalances.getOrDefault(cardId, BigDecimal.ZERO).doubleValue());
      status.put("Currency", "USD");
    } else {
      String lookup = null;
      String identificationType = null;
      if (request.getBalanceInquiryRequest() != null
          && request.getBalanceInquiryRequest().getLoyaltyAccountReq() != null
          && request.getBalanceInquiryRequest().getLoyaltyAccountReq().getLoyaltyAccountID()
              != null) {
        lookup =
            request
                .getBalanceInquiryRequest()
                .getLoyaltyAccountReq()
                .getLoyaltyAccountID()
                .getLoyaltyID();
        identificationType =
            request
                        .getBalanceInquiryRequest()
                        .getLoyaltyAccountReq()
                        .getLoyaltyAccountID()
                        .getIdentificationType()
                    == null
                ? null
                : request
                    .getBalanceInquiryRequest()
                    .getLoyaltyAccountReq()
                    .getLoyaltyAccountID()
                    .getIdentificationType()
                    .toValue();
      }
      Member member = findMember(lookup, identificationType);
      if (member == null) {
        body.set("Response", failureResult("NotFound"));
      } else {
        body.set("Response", success());
        ObjectNode status = body.putObject("LoyaltyAccountStatus");
        status.put("CurrentBalance", member.points);
        status.put("LoyaltyUnit", "Point");
        ObjectNode account = status.putObject("LoyaltyAccount").putObject("LoyaltyAccountID");
        account.put("LoyaltyID", member.memberId);
        account.put("IdentificationType", "AccountNumber");
        ((ObjectNode) body.get("Response")).put("AdditionalResponse", loyaltyPayload(member));
      }
    }
    return envelope(header, MessageCategoryType.BALANCE_INQUIRY, "BalanceInquiryResponse", body);
  }

  private NexoTerminalAPI payment(SaleToPOIRequest request, MessageHeader header)
      throws BiltNexoClientException {
    PaymentTypeEnum type =
        request.getPaymentRequest() == null || request.getPaymentRequest().getPaymentData() == null
            ? null
            : request.getPaymentRequest().getPaymentData().getPaymentType();
    if (type == PaymentTypeEnum.REFUND) {
      return refund(request, header);
    }
    BigDecimal amount = paymentAmount(request);
    if (declines(amount)) {
      return failure(
          requestHeader(header, MessageCategoryType.PAYMENT),
          MessageCategoryType.PAYMENT,
          "Refusal");
    }
    com.bilt.pos.nexo.model.PaymentData paymentData =
        request.getPaymentRequest() == null ? null : request.getPaymentRequest().getPaymentData();
    com.bilt.pos.nexo.model.PaymentInstrumentData instrumentData =
        paymentData == null ? null : paymentData.getPaymentInstrumentData();
    boolean storedValuePayment =
        instrumentData != null
            && instrumentData.getPaymentInstrumentType() == PaymentInstrumentTypeEnum.STORED_VALUE;
    String storedValueId =
        !storedValuePayment || instrumentData.getStoredValueAccountID() == null
            ? ""
            : Optional.ofNullable(instrumentData.getStoredValueAccountID().getStoredValueID())
                .orElse("");
    BigDecimal storedValueBalance = null;
    if (storedValuePayment) {
      storedValueBalance = storedValueBalances.getOrDefault(storedValueId, BigDecimal.ZERO);
      if (amount.signum() <= 0 || storedValueBalance.signum() <= 0) {
        return failure(
            requestHeader(header, MessageCategoryType.PAYMENT),
            MessageCategoryType.PAYMENT,
            "Refusal");
      }
      amount = amount.min(storedValueBalance);
      storedValueBalance = storedValueBalance.subtract(amount);
      storedValueBalances.put(storedValueId, storedValueBalance);
    }
    String currency = paymentCurrency(request);
    ObjectNode body = successBody();
    ObjectNode paymentResult = body.putObject("PaymentResult");
    paymentResult
        .putObject("AmountsResp")
        .put("AuthorizedAmount", amount.doubleValue())
        .put("Currency", currency);
    ObjectNode acquirerData = paymentResult.putObject("PaymentAcquirerData");
    acquirerData.put("ApprovalCode", "SIM-APPROVED");
    acquirerData
        .putObject("AcquirerTransactionID")
        .put("TransactionID", "SIM-ACQ-" + (transactionCounter + 1));
    if (storedValuePayment) {
      ((ObjectNode) body.get("Response"))
          .put("AdditionalResponse", "currentBalance=" + storedValueBalance.toPlainString());
    } else {
      ObjectNode card = paymentResult.putObject("PaymentInstrumentData").putObject("CardData");
      card.put("MaskedPAN", VISA_MASKED_PAN);
      card.put("PaymentBrand", "Visa");
    }
    ObjectNode poi = addPoiData(body);
    String poiId = poi.path("POITransactionID").path("TransactionID").asText();
    LedgerEntry entry =
        new LedgerEntry(
            poiId,
            header.getServiceID(),
            MessageCategoryType.PAYMENT,
            body.deepCopy(),
            responseHeader(header, MessageCategoryType.PAYMENT),
            amount,
            currency);
    entry.paymentType = PaymentTypeEnum.NORMAL;
    if (storedValuePayment) {
      entry.storedValueId = storedValueId;
    }
    record(entry);
    return approved(header, MessageCategoryType.PAYMENT, "PaymentResponse", body);
  }

  private NexoTerminalAPI refund(SaleToPOIRequest request, MessageHeader header)
      throws BiltNexoClientException {
    PaymentRequestParts parts = paymentRequestParts(request);
    OriginalTransactionRef originalRef = parts.original;
    LedgerEntry original = originalRef == null ? null : ledgerByPoiId.get(originalRef.poiId);
    BigDecimal amount = parts.amount;
    if (originalRef != null) {
      if (original == null || original.category != MessageCategoryType.PAYMENT) {
        return failure(
            requestHeader(header, MessageCategoryType.PAYMENT),
            MessageCategoryType.PAYMENT,
            "NotFound");
      }
      if (original.reversed) {
        return failure(
            requestHeader(header, MessageCategoryType.PAYMENT),
            MessageCategoryType.PAYMENT,
            "Refusal");
      }
      BigDecimal remaining = original.amount.subtract(original.refunded);
      if (amount == null) {
        amount = remaining;
      }
      if (amount.signum() < 0 || amount.compareTo(remaining) > 0) {
        return failure(
            requestHeader(header, MessageCategoryType.PAYMENT),
            MessageCategoryType.PAYMENT,
            "Refusal");
      }
      original.refunded = original.refunded.add(amount);
      if (original.storedValueId != null) {
        storedValueBalances.merge(original.storedValueId, amount, BigDecimal::add);
      }
    } else if (amount == null) {
      amount = BigDecimal.ZERO;
    }
    String currency =
        parts.currency == null ? original == null ? "USD" : original.currency : parts.currency;
    ObjectNode body = successBody();
    body.putObject("PaymentResult")
        .putObject("AmountsResp")
        .put("AuthorizedAmount", amount.doubleValue())
        .put("Currency", currency);
    ((ObjectNode) body.get("PaymentResult"))
        .putObject("PaymentAcquirerData")
        .put("ApprovalCode", "SIM-REFUND");
    addPoiData(body);
    LedgerEntry entry =
        new LedgerEntry(
            poiId(body),
            header.getServiceID(),
            MessageCategoryType.PAYMENT,
            body.deepCopy(),
            responseHeader(header, MessageCategoryType.PAYMENT),
            amount,
            currency);
    entry.paymentType = PaymentTypeEnum.REFUND;
    entry.originalPoiId = originalRef == null ? null : originalRef.poiId;
    record(entry);
    return approved(header, MessageCategoryType.PAYMENT, "PaymentResponse", body);
  }

  private NexoTerminalAPI loyalty(SaleToPOIRequest request, MessageHeader header)
      throws BiltNexoClientException {
    if (request.getLoyaltyRequest() == null
        || request.getLoyaltyRequest().getLoyaltyTransaction() == null) {
      return failure(
          requestHeader(header, MessageCategoryType.LOYALTY),
          MessageCategoryType.LOYALTY,
          "NotFound");
    }
    LoyaltyTransactionTypeEnum type =
        request.getLoyaltyRequest().getLoyaltyTransaction().getLoyaltyTransactionType();
    if (type == null) {
      return failure(
          requestHeader(header, MessageCategoryType.LOYALTY),
          MessageCategoryType.LOYALTY,
          "NotFound");
    }
    String memberId = loyaltyMemberId(request);
    Member member = membersById.get(memberId);
    if (member == null) {
      return failure(
          requestHeader(header, MessageCategoryType.LOYALTY),
          MessageCategoryType.LOYALTY,
          "NotFound");
    }
    if (type == LoyaltyTransactionTypeEnum.AWARD_REFUND
        || type == LoyaltyTransactionTypeEnum.REBATE_REFUND
        || type == LoyaltyTransactionTypeEnum.REDEMPTION_REFUND) {
      return loyaltyRefund(request, header, type, member);
    }

    int pointsEffect = 0;
    BigDecimal monetaryValue = BigDecimal.ZERO;
    int pointsUsed = 0;
    ObjectNode result = MAPPER.createObjectNode();
    switch (type) {
      case REBATE:
        break;
      case AWARD:
        double totalAmount =
            request.getLoyaltyRequest().getLoyaltyTransaction().getTotalAmount() == null
                ? 0.0
                : request.getLoyaltyRequest().getLoyaltyTransaction().getTotalAmount();
        pointsEffect = (int) Math.floor(totalAmount);
        member.points += pointsEffect;
        result.put("CurrentBalance", member.points);
        result
            .putObject("LoyaltyAmount")
            .put("AmountValue", pointsEffect)
            .put("LoyaltyUnit", "Point");
        break;
      case REDEMPTION:
        Redemption redemption = redemption(request, member);
        pointsUsed = redemption.pointsUsed;
        monetaryValue = redemption.monetaryValue;
        Double totalAmountDue =
            request.getLoyaltyRequest().getLoyaltyTransaction().getTotalAmount();
        if (totalAmountDue != null
            && monetaryValue.compareTo(BigDecimal.valueOf(totalAmountDue)) > 0) {
          return failure(
              requestHeader(header, MessageCategoryType.LOYALTY),
              MessageCategoryType.LOYALTY,
              "Refusal");
        }
        if (pointsUsed > member.points) {
          return failure(
              requestHeader(header, MessageCategoryType.LOYALTY),
              MessageCategoryType.LOYALTY,
              "Refusal");
        }
        member.points -= pointsUsed;
        pointsEffect = -pointsUsed;
        result.put("CurrentBalance", member.points);
        result
            .putObject("LoyaltyAmount")
            .put("AmountValue", monetaryValue.doubleValue())
            .put("Currency", loyaltyCurrency(request))
            .put("LoyaltyUnit", "Monetary");
        break;
      default:
        return failure(
            requestHeader(header, MessageCategoryType.LOYALTY),
            MessageCategoryType.LOYALTY,
            "NotFound");
    }
    ObjectNode body = successBody();
    if (type == LoyaltyTransactionTypeEnum.REDEMPTION) {
      ((ObjectNode) body.get("Response"))
          .put("AdditionalResponse", encodedJson("pointsUsed", pointsUsed));
    }
    body.putArray("LoyaltyResult").add(result);
    addPoiData(body);
    LedgerEntry entry =
        new LedgerEntry(
            poiId(body),
            header.getServiceID(),
            MessageCategoryType.LOYALTY,
            body.deepCopy(),
            responseHeader(header, MessageCategoryType.LOYALTY),
            monetaryValue,
            loyaltyCurrency(request));
    entry.memberId = member.memberId;
    entry.pointsEffect = pointsEffect;
    entry.loyaltyType = type;
    entry.pointsUsed = pointsUsed;
    record(entry);
    return approved(header, MessageCategoryType.LOYALTY, "LoyaltyResponse", body);
  }

  private NexoTerminalAPI loyaltyRefund(
      SaleToPOIRequest request,
      MessageHeader header,
      LoyaltyTransactionTypeEnum type,
      Member member)
      throws BiltNexoClientException {
    OriginalTransactionRef reference = loyaltyOriginal(request);
    LedgerEntry original = reference == null ? null : ledgerByPoiId.get(reference.poiId);
    if (original == null
        || original.category != MessageCategoryType.LOYALTY
        || !matchesRefundType(original.loyaltyType, type)
        || !member.memberId.equals(original.memberId)) {
      return failure(
          requestHeader(header, MessageCategoryType.LOYALTY),
          MessageCategoryType.LOYALTY,
          "NotFound");
    }
    if (original.loyaltyRefunded) {
      return failure(
          requestHeader(header, MessageCategoryType.LOYALTY),
          MessageCategoryType.LOYALTY,
          "Refusal");
    }
    member.points -= original.pointsEffect;
    original.loyaltyRefunded = true;
    ObjectNode body = successBody();
    ObjectNode result = body.putArray("LoyaltyResult").addObject();
    result.put("CurrentBalance", member.points);
    result
        .putObject("LoyaltyAmount")
        .put("AmountValue", Math.abs(original.pointsEffect))
        .put("LoyaltyUnit", "Point");
    addPoiData(body);
    LedgerEntry entry =
        new LedgerEntry(
            poiId(body),
            header.getServiceID(),
            MessageCategoryType.LOYALTY,
            body.deepCopy(),
            responseHeader(header, MessageCategoryType.LOYALTY),
            BigDecimal.ZERO,
            loyaltyCurrency(request));
    entry.memberId = member.memberId;
    entry.loyaltyType = type;
    entry.originalPoiId = original.poiId;
    record(entry);
    return approved(header, MessageCategoryType.LOYALTY, "LoyaltyResponse", body);
  }

  private NexoTerminalAPI reversal(SaleToPOIRequest request, MessageHeader header)
      throws BiltNexoClientException {
    OriginalTransactionRef reference =
        request.getReversalRequest() == null
            ? null
            : originalRef(request.getReversalRequest().getOriginalPOITransaction());
    LedgerEntry original = reference == null ? null : ledgerByPoiId.get(reference.poiId);
    if (original == null
        || (original.category != MessageCategoryType.PAYMENT
            && original.category != MessageCategoryType.STORED_VALUE)) {
      return failure(
          requestHeader(header, MessageCategoryType.REVERSAL),
          MessageCategoryType.REVERSAL,
          "NotFound");
    }
    if (original.reversed || original.refunded.signum() > 0) {
      return failure(
          requestHeader(header, MessageCategoryType.REVERSAL),
          MessageCategoryType.REVERSAL,
          "Refusal");
    }
    original.reversed = true;
    if (original.category == MessageCategoryType.STORED_VALUE) {
      BigDecimal change =
          original.storedValueType == StoredValueTransactionTypeEnum.UNLOAD
              ? original.amount
              : original.amount.negate();
      storedValueBalances.merge(original.storedValueId, change, BigDecimal::add);
    } else if (original.storedValueId != null) {
      storedValueBalances.merge(original.storedValueId, original.amount, BigDecimal::add);
    }
    ObjectNode body = successBody();
    body.put("ReversedAmount", original.amount.doubleValue());
    body.putObject("OriginalPOITransaction")
        .putObject("POITransactionID")
        .put("TransactionID", original.poiId);
    addPoiData(body);
    LedgerEntry entry =
        new LedgerEntry(
            poiId(body),
            header.getServiceID(),
            MessageCategoryType.REVERSAL,
            body.deepCopy(),
            responseHeader(header, MessageCategoryType.REVERSAL),
            original.amount,
            original.currency);
    entry.originalPoiId = original.poiId;
    record(entry);
    return approved(header, MessageCategoryType.REVERSAL, "ReversalResponse", body);
  }

  private NexoTerminalAPI storedValue(SaleToPOIRequest request, MessageHeader header)
      throws BiltNexoClientException {
    JsonNode data =
        request.getStoredValueRequest() == null
                || request.getStoredValueRequest().getStoredValueData() == null
                || request.getStoredValueRequest().getStoredValueData().length == 0
            ? null
            : MAPPER.valueToTree(request.getStoredValueRequest().getStoredValueData()[0]);
    String storedValueId =
        data == null ? null : data.path("StoredValueAccountID").path("StoredValueID").asText(null);
    String key = storedValueId == null ? "" : storedValueId;
    StoredValueTransactionTypeEnum type =
        data == null
            ? null
            : enumValue(
                data.path("StoredValueTransactionType").asText(null),
                StoredValueTransactionTypeEnum.class);
    BigDecimal amount =
        data == null || data.path("ItemAmount").isMissingNode()
            ? BigDecimal.ZERO
            : data.path("ItemAmount").decimalValue();
    String originalPoiId =
        data == null
            ? null
            : data.path("OriginalPOITransaction")
                .path("POITransactionID")
                .path("TransactionID")
                .asText(null);
    LedgerEntry original =
        type == StoredValueTransactionTypeEnum.REVERSE && originalPoiId != null
            ? ledgerByPoiId.get(originalPoiId)
            : null;
    if (type == StoredValueTransactionTypeEnum.REVERSE) {
      if (original == null
          || original.category != MessageCategoryType.STORED_VALUE
          || (original.storedValueType != StoredValueTransactionTypeEnum.ACTIVATE
              && original.storedValueType != StoredValueTransactionTypeEnum.LOAD
              && original.storedValueType != StoredValueTransactionTypeEnum.UNLOAD)) {
        return failure(
            requestHeader(header, MessageCategoryType.STORED_VALUE),
            MessageCategoryType.STORED_VALUE,
            "NotFound");
      }
      if (original.reversed) {
        return failure(
            requestHeader(header, MessageCategoryType.STORED_VALUE),
            MessageCategoryType.STORED_VALUE,
            "Refusal");
      }
      storedValueId = original.storedValueId;
      key = storedValueId == null ? "" : storedValueId;
      amount = original.amount;
    }
    BigDecimal balance = storedValueBalances.getOrDefault(key, BigDecimal.ZERO);
    if (type == StoredValueTransactionTypeEnum.UNLOAD && amount.compareTo(balance) > 0) {
      return failure(
          requestHeader(header, MessageCategoryType.STORED_VALUE),
          MessageCategoryType.STORED_VALUE,
          "Refusal");
    }
    if (type == StoredValueTransactionTypeEnum.REVERSE
        && (original.storedValueType == StoredValueTransactionTypeEnum.ACTIVATE
            || original.storedValueType == StoredValueTransactionTypeEnum.LOAD)
        && amount.compareTo(balance) > 0) {
      return failure(
          requestHeader(header, MessageCategoryType.STORED_VALUE),
          MessageCategoryType.STORED_VALUE,
          "Refusal");
    }
    if (type == StoredValueTransactionTypeEnum.ACTIVATE
        || type == StoredValueTransactionTypeEnum.LOAD) {
      balance = balance.add(amount);
      storedValueBalances.put(key, balance);
    } else if (type == StoredValueTransactionTypeEnum.UNLOAD) {
      balance = balance.subtract(amount);
      storedValueBalances.put(key, balance);
    } else if (type == StoredValueTransactionTypeEnum.REVERSE) {
      BigDecimal change =
          original.storedValueType == StoredValueTransactionTypeEnum.UNLOAD
              ? amount
              : amount.negate();
      balance = balance.add(change);
      storedValueBalances.put(key, balance);
      original.reversed = true;
    }
    String currency = data == null ? "USD" : data.path("Currency").asText("USD");
    ObjectNode body = successBody();
    ObjectNode poi = addPoiData(body);
    ObjectNode result = body.putArray("StoredValueResult").addObject();
    result.put("Currency", currency);
    result.put("ItemAmount", amount.doubleValue());
    if (type != null) {
      result.put("StoredValueTransactionType", type.toValue());
    }
    ObjectNode accountStatus = result.putObject("StoredValueAccountStatus");
    accountStatus.put("CurrentBalance", balance.doubleValue());
    if (storedValueId != null) {
      accountStatus.putObject("StoredValueAccountID").put("StoredValueID", storedValueId);
    }
    LedgerEntry entry =
        new LedgerEntry(
            poi.path("POITransactionID").path("TransactionID").asText(),
            header.getServiceID(),
            MessageCategoryType.STORED_VALUE,
            body.deepCopy(),
            responseHeader(header, MessageCategoryType.STORED_VALUE),
            amount,
            currency);
    entry.storedValueId = key;
    entry.storedValueType = type;
    entry.originalPoiId = original == null ? null : original.poiId;
    record(entry);
    return approved(header, MessageCategoryType.STORED_VALUE, "StoredValueResponse", body);
  }

  private NexoTerminalAPI transactionStatus(SaleToPOIRequest request, MessageHeader header) {
    String serviceId =
        request.getTransactionStatusRequest() == null
                || request.getTransactionStatusRequest().getMessageReference() == null
            ? null
            : request.getTransactionStatusRequest().getMessageReference().getServiceID();
    WaitState waiting = waitingByServiceId.get(serviceId);
    if (waiting != null) {
      return failure(
          requestHeader(header, MessageCategoryType.TRANSACTION_STATUS),
          MessageCategoryType.TRANSACTION_STATUS,
          "InProgress");
    }
    String saleId =
        serviceId == null
            ? null
            : request.getTransactionStatusRequest().getMessageReference().getSaleID();
    LedgerEntry entry = ledgerByServiceId.get(serviceId);
    if (entry == null
        || (saleId != null
            && entry.messageHeader.getSaleID() != null
            && !saleId.equals(entry.messageHeader.getSaleID()))) {
      return failure(
          requestHeader(header, MessageCategoryType.TRANSACTION_STATUS),
          MessageCategoryType.TRANSACTION_STATUS,
          "NotFound");
    }
    ObjectNode body = successBody();
    ObjectNode repeated = body.putObject("RepeatedMessageResponse");
    repeated.set("MessageHeader", MAPPER.valueToTree(entry.messageHeader));
    String responseName = responseName(entry.category);
    repeated
        .putObject("RepeatedResponseMessageBody")
        .set(responseName, entry.responseBody.deepCopy());
    return envelope(
        header, MessageCategoryType.TRANSACTION_STATUS, "TransactionStatusResponse", body);
  }

  private NexoTerminalAPI display(SaleToPOIRequest request, MessageHeader header) {
    latestDisplay = request.getDisplayRequest();
    ObjectNode body = MAPPER.createObjectNode();
    ArrayNode results = body.putArray("OutputResult");
    if (latestDisplay != null && latestDisplay.getDisplayOutput() != null) {
      for (com.bilt.pos.nexo.model.DisplayOutput output : latestDisplay.getDisplayOutput()) {
        ObjectNode result = results.addObject();
        if (output.getDevice() != null) {
          result.set("Device", MAPPER.valueToTree(output.getDevice()));
        }
        if (output.getInfoQualify() != null) {
          result.set("InfoQualify", MAPPER.valueToTree(output.getInfoQualify()));
        }
        result.set("Response", success());
      }
    }
    return envelope(header, MessageCategoryType.DISPLAY, "DisplayResponse", body);
  }

  private NexoTerminalAPI diagnosis(MessageHeader header) {
    ObjectNode body = successBody();
    body.putObject("POIStatus").put("GlobalStatus", "OK");
    return envelope(header, MessageCategoryType.DIAGNOSIS, "DiagnosisResponse", body);
  }

  private NexoTerminalAPI input(SaleToPOIRequest request, MessageHeader header) {
    ObjectNode body = MAPPER.createObjectNode();
    ObjectNode inputResult = body.putObject("InputResult");
    if (request.getInputRequest() != null && request.getInputRequest().getInputData() != null) {
      if (request.getInputRequest().getInputData().getDevice() != null) {
        inputResult.set(
            "Device", MAPPER.valueToTree(request.getInputRequest().getInputData().getDevice()));
      }
      if (request.getInputRequest().getInputData().getInfoQualify() != null) {
        inputResult.set(
            "InfoQualify",
            MAPPER.valueToTree(request.getInputRequest().getInputData().getInfoQualify()));
      }
    }
    ObjectNode inputResponse = success();
    inputResult.set("Response", inputResponse);
    ObjectNode value = inputResult.putObject("Input");
    if (request.getInputRequest() != null
        && request.getInputRequest().getInputData() != null
        && request.getInputRequest().getInputData().getInputCommand() != null) {
      String command = request.getInputRequest().getInputData().getInputCommand().toValue();
      value.put("InputCommand", command);
      switch (request.getInputRequest().getInputData().getInputCommand()) {
        case GET_CONFIRMATION:
        case SITE_MANAGER:
          value.put("ConfirmedFlag", confirmInputs);
          inputResponse.put("AdditionalResponse", signatureImage());
          break;
        case GET_MENU_ENTRY:
          value.putArray("MenuEntryNumber").add(1);
          break;
        case DECIMAL_STRING:
          value.put("DigitInput", "0");
          break;
        case DIGIT_STRING:
          value.put("DigitInput", "");
          break;
        case TEXT_STRING:
          value.put("TextInput", "");
          break;
        case GET_ANY_KEY:
        case GET_FUNCTION_KEY:
          value.put("FunctionKey", "Enter");
          break;
        case PASSWORD:
          value.put("DigitInput", "");
          break;
        default:
          break;
      }
    }
    return envelope(header, MessageCategoryType.INPUT, "InputResponse", body);
  }

  private NexoTerminalAPI approved(
      MessageHeader header, MessageCategoryType category, String responseName, ObjectNode body)
      throws BiltNexoClientException {
    if (dropAfterNextApproval) {
      dropAfterNextApproval = false;
      throw new BiltNexoClientException(
          "simulated terminal connection reset", new IOException("connection reset"));
    }
    return envelope(header, category, responseName, body);
  }

  private NexoTerminalAPI failure(
      MessageHeader header, MessageCategoryType category, String errorCondition) {
    ObjectNode body = MAPPER.createObjectNode();
    if (category == MessageCategoryType.INPUT) {
      body.putObject("InputResult").set("Response", failureResult(errorCondition));
    } else {
      body.set("Response", failureResult(errorCondition));
    }
    return envelope(header, category, responseName(category), body);
  }

  private NexoTerminalAPI envelope(
      MessageHeader header,
      MessageCategoryType category,
      String responseName,
      ObjectNode responseBody) {
    ObjectNode root = MAPPER.createObjectNode();
    ObjectNode response = root.putObject("SaleToPOIResponse");
    response.set("MessageHeader", MAPPER.valueToTree(responseHeader(header, category)));
    response.set(responseName, responseBody);
    return MAPPER.convertValue(root, NexoTerminalAPI.class);
  }

  private MessageHeader responseHeader(MessageHeader header, MessageCategoryType category) {
    return MessageHeader.builder()
        .protocolVersion(header.getProtocolVersion())
        .messageClass(header.getMessageClass())
        .messageCategory(
            header.getMessageCategory() == null ? category : header.getMessageCategory())
        .messageType(com.bilt.pos.nexo.model.MessageTypeType.RESPONSE)
        .serviceID(header.getServiceID())
        .saleID(header.getSaleID())
        .poiid(header.getPoiid())
        .deviceID(header.getDeviceID())
        .build();
  }

  private MessageHeader requestHeader(MessageHeader header, MessageCategoryType category) {
    return header == null
        ? MessageHeader.builder()
            .messageClass(com.bilt.pos.nexo.model.MessageClassType.SERVICE)
            .messageCategory(category)
            .messageType(com.bilt.pos.nexo.model.MessageTypeType.REQUEST)
            .build()
        : header;
  }

  private ObjectNode addPoiData(ObjectNode response) {
    long next = ++transactionCounter;
    String timestamp = Instant.now().toString();
    ObjectNode poiData = response.putObject("POIData");
    poiData
        .putObject("POITransactionID")
        .put("TransactionID", "SIM-" + next)
        .put("TimeStamp", timestamp);
    return poiData;
  }

  private void record(LedgerEntry entry) {
    ledgerByPoiId.put(entry.poiId, entry);
    if (entry.serviceId != null) {
      ledgerByServiceId.put(entry.serviceId, entry);
    }
  }

  private Member findMember(String value, String identificationType) {
    if (value == null) {
      return null;
    }
    for (Member member : members) {
      if ("PhoneNumber".equals(identificationType) && member.phone.equals(value)) {
        return member;
      }
      if (!"PhoneNumber".equals(identificationType)
          && (member.accountId.equals(value) || member.memberId.equals(value))) {
        return member;
      }
    }
    return null;
  }

  private Redemption redemption(SaleToPOIRequest request, Member member) {
    Set<String> refs = rewardRefs(request);
    int points = 0;
    BigDecimal value = BigDecimal.ZERO;
    for (Reward reward : member.rewards) {
      if (refs.contains(reward.rewardRef)) {
        points += reward.points;
        value = value.add(reward.value);
      }
    }
    com.bilt.pos.nexo.model.LoyaltyData[] data = request.getLoyaltyRequest().getLoyaltyData();
    if (data != null
        && data.length > 0
        && data[0].getLoyaltyAmount() != null
        && data[0].getLoyaltyAmount().getLoyaltyUnit()
            == com.bilt.pos.nexo.model.LoyaltyUnitEnum.POINT
        && data[0].getLoyaltyAmount().getAmountValue() > 0) {
      points = (int) Math.round(data[0].getLoyaltyAmount().getAmountValue());
      value = BigDecimal.valueOf(points).movePointLeft(2);
    }
    return new Redemption(points, value);
  }

  private Set<String> rewardRefs(SaleToPOIRequest request) {
    String encoded =
        request.getLoyaltyRequest() == null || request.getLoyaltyRequest().getSaleData() == null
            ? null
            : request.getLoyaltyRequest().getSaleData().getSaleToPOIData();
    if (encoded == null) {
      return Collections.emptySet();
    }
    try {
      JsonNode refs = MAPPER.readTree(Base64.getDecoder().decode(encoded)).path("rewardRefs");
      Set<String> result = new HashSet<>();
      if (refs.isArray()) {
        refs.forEach(ref -> result.add(ref.asText()));
      }
      return result;
    } catch (IOException | IllegalArgumentException e) {
      return Collections.emptySet();
    }
  }

  private String loyaltyMemberId(SaleToPOIRequest request) {
    com.bilt.pos.nexo.model.LoyaltyData[] data = request.getLoyaltyRequest().getLoyaltyData();
    return data == null || data.length == 0 || data[0].getLoyaltyAccountID() == null
        ? null
        : data[0].getLoyaltyAccountID().getLoyaltyID();
  }

  private OriginalTransactionRef loyaltyOriginal(SaleToPOIRequest request) {
    return originalRef(
        request.getLoyaltyRequest().getLoyaltyTransaction().getOriginalPOITransaction());
  }

  private static OriginalTransactionRef originalRef(
      com.bilt.pos.nexo.model.OriginalPOITransaction transaction) {
    if (transaction == null
        || transaction.getPoiTransactionID() == null
        || transaction.getPoiTransactionID().getTransactionID() == null) {
      return null;
    }
    return new OriginalTransactionRef(transaction.getPoiTransactionID().getTransactionID());
  }

  private String loyaltyCurrency(SaleToPOIRequest request) {
    return request.getLoyaltyRequest().getLoyaltyTransaction().getCurrency() == null
        ? "USD"
        : request.getLoyaltyRequest().getLoyaltyTransaction().getCurrency();
  }

  private PaymentRequestParts paymentRequestParts(SaleToPOIRequest request) {
    com.bilt.pos.nexo.model.PaymentRequest payment = request.getPaymentRequest();
    com.bilt.pos.nexo.model.PaymentTransaction transaction =
        payment == null ? null : payment.getPaymentTransaction();
    com.bilt.pos.nexo.model.AmountsReq amounts =
        transaction == null ? null : transaction.getAmountsReq();
    return new PaymentRequestParts(
        amounts == null || amounts.getRequestedAmount() == null
            ? null
            : BigDecimal.valueOf(amounts.getRequestedAmount()),
        amounts == null ? null : amounts.getCurrency(),
        transaction == null ? null : originalRef(transaction.getOriginalPOITransaction()));
  }

  private BigDecimal paymentAmount(SaleToPOIRequest request) {
    BigDecimal amount = paymentRequestParts(request).amount;
    return amount == null ? BigDecimal.ZERO : amount;
  }

  private String paymentCurrency(SaleToPOIRequest request) {
    String currency = paymentRequestParts(request).currency;
    return currency == null ? "USD" : currency;
  }

  private static boolean declines(BigDecimal amount) {
    return amount
            .movePointRight(2)
            .setScale(0, java.math.RoundingMode.HALF_UP)
            .abs()
            .remainder(BigDecimal.valueOf(1000))
            .intValue()
        == 123;
  }

  private String storedValueId(com.bilt.pos.nexo.model.PaymentAccountReq request) {
    return request.getPaymentInstrumentData() == null
            || request.getPaymentInstrumentData().getStoredValueAccountID() == null
        ? ""
        : Optional.ofNullable(
                request.getPaymentInstrumentData().getStoredValueAccountID().getStoredValueID())
            .orElse("");
  }

  private static String poiId(ObjectNode body) {
    return body.path("POIData").path("POITransactionID").path("TransactionID").asText();
  }

  private static ObjectNode success() {
    ObjectNode response = MAPPER.createObjectNode();
    response.put("Result", "Success");
    return response;
  }

  private static ObjectNode successBody() {
    ObjectNode body = MAPPER.createObjectNode();
    body.set("Response", success());
    return body;
  }

  private static ObjectNode failureResult(String errorCondition) {
    ObjectNode response = MAPPER.createObjectNode();
    response.put("Result", "Failure");
    response.put("ErrorCondition", errorCondition);
    return response;
  }

  private static String responseName(MessageCategoryType category) {
    switch (category) {
      case ADMIN:
        return "AdminResponse";
      case BALANCE_INQUIRY:
        return "BalanceInquiryResponse";
      case CARD_ACQUISITION:
        return "CardAcquisitionResponse";
      case DISPLAY:
        return "DisplayResponse";
      case INPUT:
        return "InputResponse";
      case LOYALTY:
        return "LoyaltyResponse";
      case PAYMENT:
        return "PaymentResponse";
      case REVERSAL:
        return "ReversalResponse";
      case STORED_VALUE:
        return "StoredValueResponse";
      case TRANSACTION_STATUS:
        return "TransactionStatusResponse";
      default:
        throw new IllegalArgumentException("unsupported response category: " + category.toValue());
    }
  }

  private static String loyaltyPayload(Member member) {
    ObjectNode root = MAPPER.createObjectNode();
    ArrayNode rewards = root.putArray("rewards");
    for (Reward reward : member.rewards) {
      ObjectNode item = rewards.addObject();
      item.put("rewardRef", reward.rewardRef);
      item.put("type", reward.type);
      item.put("name", reward.name);
    }
    ObjectNode shopper = root.putObject("shopperContext").putObject("shopper");
    shopper.put("firstName", member.firstName);
    shopper.put("lastName", member.lastName);
    shopper.put("tierName", member.tierName);
    shopper.put("pointsBalance", member.points);
    shopper.put("kind", "member");
    return encode(root);
  }

  private static String encodedJson(String field, int value) {
    ObjectNode root = MAPPER.createObjectNode();
    root.put(field, value);
    return encode(root);
  }

  private static String signatureImage() {
    return "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAIAAACQd1PeAAAADUlEQVR4nGP4z8AAAAMBAQDJ/pLvAAAAAElFTkSuQmCC";
  }

  private static boolean matchesRefundType(
      LoyaltyTransactionTypeEnum original, LoyaltyTransactionTypeEnum refund) {
    return (original == LoyaltyTransactionTypeEnum.AWARD
            && refund == LoyaltyTransactionTypeEnum.AWARD_REFUND)
        || (original == LoyaltyTransactionTypeEnum.REBATE
            && refund == LoyaltyTransactionTypeEnum.REBATE_REFUND)
        || (original == LoyaltyTransactionTypeEnum.REDEMPTION
            && refund == LoyaltyTransactionTypeEnum.REDEMPTION_REFUND);
  }

  private static String encode(ObjectNode node) {
    return Base64.getEncoder().encodeToString(node.toString().getBytes(StandardCharsets.UTF_8));
  }

  private static long nanos(Duration duration) {
    try {
      return duration.toNanos();
    } catch (ArithmeticException e) {
      return Long.MAX_VALUE;
    }
  }

  private static List<Member> readMembers(Path path) {
    try (InputStream input =
        path == null
            ? SimulatedTerminalClient.class.getResourceAsStream(DEFAULT_MEMBERS)
            : Files.newInputStream(path)) {
      if (input == null) {
        throw new IllegalArgumentException("built-in simulated members resource is missing");
      }
      JsonNode root = MAPPER.readTree(input);
      JsonNode memberNodes = root == null ? null : root.get("members");
      if (memberNodes == null || !memberNodes.isArray() || memberNodes.isEmpty()) {
        throw new IllegalArgumentException("members file must contain a non-empty members array");
      }
      List<Member> result = new ArrayList<>();
      Set<String> ids = new HashSet<>();
      for (JsonNode node : memberNodes) {
        Member member = parseMember(node);
        if (!ids.add(member.memberId)) {
          throw new IllegalArgumentException(
              "duplicate memberId in members file: " + member.memberId);
        }
        result.add(member);
      }
      return result;
    } catch (IOException e) {
      throw new IllegalArgumentException("unable to read simulated members file", e);
    }
  }

  private static Member parseMember(JsonNode node) {
    String memberId = requiredText(node, "memberId");
    String accountId = requiredText(node, "accountId");
    String phone = requiredText(node, "phone");
    String firstName = requiredText(node, "firstName");
    String lastName = requiredText(node, "lastName");
    String tierName = requiredText(node, "tierName");
    JsonNode pointsNode = node.get("points");
    if (pointsNode == null
        || !pointsNode.isIntegralNumber()
        || !pointsNode.canConvertToInt()
        || pointsNode.asInt() < 0) {
      throw new IllegalArgumentException("member points must be a non-negative integer");
    }
    JsonNode rewardNodes = node.get("rewards");
    if (rewardNodes == null || !rewardNodes.isArray()) {
      throw new IllegalArgumentException("member rewards must be an array");
    }
    List<Reward> rewards = new ArrayList<>();
    Set<String> rewardIds = new HashSet<>();
    for (JsonNode reward : rewardNodes) {
      String ref = requiredText(reward, "rewardRef");
      String type = requiredText(reward, "type");
      if (!Set.of("reward", "coupon", "point").contains(type.toLowerCase(java.util.Locale.ROOT))) {
        throw new IllegalArgumentException("unsupported reward type: " + type);
      }
      String name = requiredText(reward, "name");
      JsonNode rewardPoints = reward.get("points");
      JsonNode value = reward.get("value");
      if (rewardPoints == null
          || !rewardPoints.isIntegralNumber()
          || !rewardPoints.canConvertToInt()
          || rewardPoints.asInt() < 0
          || value == null
          || !value.isNumber()
          || value.decimalValue().signum() < 0) {
        throw new IllegalArgumentException("reward points and value must be non-negative numbers");
      }
      if (!rewardIds.add(ref)) {
        throw new IllegalArgumentException("duplicate rewardRef for member: " + ref);
      }
      rewards.add(
          new Reward(
              ref,
              type.toLowerCase(java.util.Locale.ROOT),
              name,
              rewardPoints.asInt(),
              value.decimalValue()));
    }
    return new Member(
        memberId, accountId, phone, firstName, lastName, tierName, pointsNode.asInt(), rewards);
  }

  private static String requiredText(JsonNode node, String field) {
    JsonNode value = node == null ? null : node.get(field);
    if (value == null || !value.isTextual() || value.asText().trim().isEmpty()) {
      throw new IllegalArgumentException("members file requires a non-empty " + field);
    }
    return value.asText();
  }

  private static <E extends Enum<E>> E enumValue(String value, Class<E> type) {
    if (value == null) {
      return null;
    }
    for (E item : type.getEnumConstants()) {
      if (item instanceof StoredValueTransactionTypeEnum
          && ((StoredValueTransactionTypeEnum) item).toValue().equals(value)) {
        return item;
      }
    }
    return null;
  }

  /** Builder for a simulated terminal instance. */
  public static final class Builder {
    private Path membersFile;
    private Duration customerDelay = Duration.ZERO;
    private boolean confirmInputs = true;

    private Builder() {}

    /** Loads member data from the given JSON file instead of the built-in sample. */
    public Builder members(Path jsonFile) {
      if (jsonFile == null) {
        throw new IllegalArgumentException("members file path must not be null");
      }
      membersFile = jsonFile;
      return this;
    }

    /** Sets how long customer-driven requests wait before receiving their simulated response. */
    public Builder customerDelay(Duration delay) {
      if (delay == null || delay.isNegative()) {
        throw new IllegalArgumentException("customer delay must be non-negative");
      }
      customerDelay = delay;
      return this;
    }

    /** Sets the result returned for confirmation inputs. */
    public Builder confirmInputs(boolean confirm) {
      confirmInputs = confirm;
      return this;
    }

    /** Builds a terminal, failing fast if its member file cannot be read or validated. */
    public SimulatedTerminalClient build() {
      return new SimulatedTerminalClient(this);
    }
  }

  private static final class Member {
    private final String memberId;
    private final String accountId;
    private final String phone;
    private final String firstName;
    private final String lastName;
    private final String tierName;
    private final List<Reward> rewards;
    private int points;

    private Member(
        String memberId,
        String accountId,
        String phone,
        String firstName,
        String lastName,
        String tierName,
        int points,
        List<Reward> rewards) {
      this.memberId = memberId;
      this.accountId = accountId;
      this.phone = phone;
      this.firstName = firstName;
      this.lastName = lastName;
      this.tierName = tierName;
      this.points = points;
      this.rewards = List.copyOf(rewards);
    }
  }

  private static final class Reward {
    private final String rewardRef;
    private final String type;
    private final String name;
    private final int points;
    private final java.math.BigDecimal value;

    private Reward(
        String rewardRef, String type, String name, int points, java.math.BigDecimal value) {
      this.rewardRef = rewardRef;
      this.type = type;
      this.name = name;
      this.points = points;
      this.value = value;
    }
  }

  private static final class WaitState {
    private final CountDownLatch signal = new CountDownLatch(1);
    private boolean aborted;
  }

  private static final class LedgerEntry {
    private final String poiId;
    private final String serviceId;
    private final MessageCategoryType category;
    private final ObjectNode responseBody;
    private final MessageHeader messageHeader;
    private final BigDecimal amount;
    private final String currency;
    private BigDecimal refunded = BigDecimal.ZERO;
    private PaymentTypeEnum paymentType;
    private StoredValueTransactionTypeEnum storedValueType;
    private String storedValueId;
    private String originalPoiId;
    private String memberId;
    private LoyaltyTransactionTypeEnum loyaltyType;
    private int pointsEffect;
    private int pointsUsed;
    private boolean reversed;
    private boolean loyaltyRefunded;

    private LedgerEntry(
        String poiId,
        String serviceId,
        MessageCategoryType category,
        ObjectNode responseBody,
        MessageHeader messageHeader,
        BigDecimal amount,
        String currency) {
      this.poiId = poiId;
      this.serviceId = serviceId;
      this.category = category;
      this.responseBody = responseBody;
      this.messageHeader = messageHeader;
      this.amount = amount;
      this.currency = currency;
    }
  }

  private static final class OriginalTransactionRef {
    private final String poiId;

    private OriginalTransactionRef(String poiId) {
      this.poiId = poiId;
    }
  }

  private static final class PaymentRequestParts {
    private final BigDecimal amount;
    private final String currency;
    private final OriginalTransactionRef original;

    private PaymentRequestParts(
        BigDecimal amount, String currency, OriginalTransactionRef original) {
      this.amount = amount;
      this.currency = currency;
      this.original = original;
    }
  }

  private static final class Redemption {
    private final int pointsUsed;
    private final BigDecimal monetaryValue;

    private Redemption(int pointsUsed, BigDecimal monetaryValue) {
      this.pointsUsed = pointsUsed;
      this.monetaryValue = monetaryValue;
    }
  }
}
