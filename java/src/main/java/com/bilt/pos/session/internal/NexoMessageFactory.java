/*
 *    ____  _ _ _
 *   | __ )(_) | |_
 *   |  _ \| | | __|
 *   | |_) | | | |_
 *   |____/|_|_|\__|
 *
 *   Bilt POS SDK
 *
 *   Internal API — subject to change without notice.
 */
package com.bilt.pos.session.internal;

import com.bilt.pos.nexo.model.DeviceEnum;
import com.bilt.pos.nexo.model.DisplayOutput;
import com.bilt.pos.nexo.model.DisplayRequest;
import com.bilt.pos.nexo.model.InfoQualifyEnum;
import com.bilt.pos.nexo.model.LoyaltyData;
import com.bilt.pos.nexo.model.LoyaltyRequest;
import com.bilt.pos.nexo.model.LoyaltyTransaction;
import com.bilt.pos.nexo.model.LoyaltyTransactionTypeEnum;
import com.bilt.pos.nexo.model.MessageCategoryType;
import com.bilt.pos.nexo.model.MessageClassType;
import com.bilt.pos.nexo.model.MessageHeader;
import com.bilt.pos.nexo.model.MessageTypeType;
import com.bilt.pos.nexo.model.NexoTerminalAPI;
import com.bilt.pos.nexo.model.OriginalPOITransaction;
import com.bilt.pos.nexo.model.OutputContent;
import com.bilt.pos.nexo.model.OutputFormatEnum;
import com.bilt.pos.nexo.model.ReversalReasonEnum;
import com.bilt.pos.nexo.model.ReversalRequest;
import com.bilt.pos.nexo.model.SaleData;
import com.bilt.pos.nexo.model.SaleTerminalData;
import com.bilt.pos.nexo.model.SaleToPOIRequest;
import com.bilt.pos.nexo.model.TransactionIdentificationType;
import com.bilt.pos.session.ServiceIdListener;
import com.bilt.pos.session.ServiceIdProvider;
import com.bilt.pos.session.SessionError;
import com.bilt.pos.session.SessionErrorCode;
import com.bilt.pos.session.SessionException;
import java.security.SecureRandom;
import java.util.regex.Pattern;

/**
 * Builds Nexo message headers and envelopes for a checkout session, encapsulating the {@code
 * ProtocolVersion}/{@code ServiceID}/{@code SaleID}/ {@code POIID} boilerplate.
 *
 * <p>{@code ServiceID}s come from the register's {@link ServiceIdProvider} when one is configured,
 * otherwise they are random 10-character alphanumeric strings — unique within the terminal's
 * 48-hour correlation window without requiring session-restart-safe counters. Every minted id is
 * validated and reported to the {@link ServiceIdListener} before the request carrying it is built.
 */
public final class NexoMessageFactory {

  private static final String PROTOCOL_VERSION = "3.0";
  private static final char[] SERVICE_ID_ALPHABET =
      "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789".toCharArray();
  private static final int SERVICE_ID_LENGTH = 10;
  private static final Pattern VALID_SERVICE_ID = Pattern.compile("[A-Za-z0-9]{1,10}");

  private final String saleId;
  private final String poiId;
  private final String totalsGroupId;
  private final ServiceIdProvider serviceIdProvider;
  private final ServiceIdListener serviceIdListener;
  private final SecureRandom random = new SecureRandom();

  /**
   * @param serviceIdProvider the register's id source, or {@code null} for random ids
   * @param serviceIdListener notified of every minted id, or {@code null}
   */
  public NexoMessageFactory(
      String saleId,
      String poiId,
      String totalsGroupId,
      ServiceIdProvider serviceIdProvider,
      ServiceIdListener serviceIdListener) {
    this.saleId = saleId;
    this.poiId = poiId;
    this.totalsGroupId = totalsGroupId;
    this.serviceIdProvider = serviceIdProvider;
    this.serviceIdListener = serviceIdListener;
  }

  public String getSaleId() {
    return saleId;
  }

  public String getPoiId() {
    return poiId;
  }

  /**
   * Mints the {@code ServiceID} for a request of {@code category} about to be sent: takes it from
   * the provider (or the random default), validates it, and reports it to the listener. Send the
   * request with exactly this id through {@link #header(MessageClassType, MessageCategoryType,
   * String)}.
   *
   * @throws SessionException when the provider fails or returns an invalid id, or the listener
   *     throws; the request must then not be sent
   */
  public String mintServiceId(MessageCategoryType category) {
    String serviceId;
    try {
      serviceId = serviceIdProvider == null ? randomServiceId() : serviceIdProvider.next();
    } catch (RuntimeException e) {
      throw serviceIdFailure("ServiceIdProvider failed: " + e, e);
    }
    requireValidServiceId(serviceId);
    if (serviceIdListener != null) {
      try {
        serviceIdListener.serviceIdAssigned(serviceId, category);
      } catch (RuntimeException e) {
        throw serviceIdFailure("ServiceIdListener rejected " + serviceId + ": " + e, e);
      }
    }
    return serviceId;
  }

  private String randomServiceId() {
    char[] chars = new char[SERVICE_ID_LENGTH];
    for (int i = 0; i < chars.length; i++) {
      chars[i] = SERVICE_ID_ALPHABET[random.nextInt(SERVICE_ID_ALPHABET.length)];
    }
    return new String(chars);
  }

  private static void requireValidServiceId(String serviceId) {
    if (serviceId == null || !VALID_SERVICE_ID.matcher(serviceId).matches()) {
      throw serviceIdFailure(
          "invalid ServiceID "
              + (serviceId == null ? "null" : "\"" + serviceId + "\"")
              + ": must be 1-10 characters [A-Za-z0-9]",
          null);
    }
  }

  private static SessionException serviceIdFailure(String message, Exception cause) {
    return new SessionException(
        new SessionError(SessionErrorCode.INVALID_STATE, message, null, cause));
  }

  /**
   * Builds a request header for the given class/category with a freshly minted {@code ServiceID}.
   */
  public MessageHeader header(MessageClassType messageClass, MessageCategoryType category) {
    return header(messageClass, category, mintServiceId(category));
  }

  /**
   * Builds a request header with a {@code ServiceID} already minted by {@link
   * #mintServiceId(MessageCategoryType)} — the request goes out with exactly that id and the
   * listener is not notified again.
   *
   * @throws SessionException when {@code serviceId} is not 1–10 characters {@code [A-Za-z0-9]}
   */
  public MessageHeader header(
      MessageClassType messageClass, MessageCategoryType category, String serviceId) {
    requireValidServiceId(serviceId);
    return MessageHeader.builder()
        .protocolVersion(PROTOCOL_VERSION)
        .messageClass(messageClass)
        .messageCategory(category)
        .messageType(MessageTypeType.REQUEST)
        .serviceID(serviceId)
        .saleID(saleId)
        .poiid(poiId)
        .build();
  }

  /** Wraps a {@link SaleToPOIRequest} into the top-level API envelope. */
  public NexoTerminalAPI envelope(SaleToPOIRequest request) {
    return NexoTerminalAPI.builder().saleToPOIRequest(request).build();
  }

  /**
   * Sale data carrying the basket's shared {@code SaleTransactionID} — ID and timestamp as minted,
   * so every step of the checkout reads as one transaction. When a totals group (store location) is
   * configured it is attached as {@code SaleTerminalData.TotalsGroupID}, grouping the session's
   * transactions for totals and reconciliation.
   */
  public SaleData saleData(TransactionIdentificationType saleTransaction) {
    SaleData.Builder saleData = SaleData.builder().saleTransactionID(saleTransaction);
    if (totalsGroupId != null) {
      saleData.saleTerminalData(SaleTerminalData.builder().totalsGroupID(totalsGroupId).build());
    }
    return saleData.build();
  }

  /**
   * A loyalty refund request ({@code RedemptionRefund} / {@code RebateRefund} / {@code
   * AwardRefund}) against the movement's own POI reference — the one wire shape every loyalty
   * reversal on this terminal uses, whether sent by a void, a refund, or a payment unwind. Per the
   * reversal contracts the original transaction reference alone suffices; the member's {@code
   * LoyaltyData} is attached when known. Sent with {@code serviceId}, minted by {@link
   * #mintServiceId(MessageCategoryType)}.
   */
  public SaleToPOIRequest loyaltyRefundRequest(
      LoyaltyTransactionTypeEnum refundType,
      OriginalPOITransaction original,
      String memberId,
      TransactionIdentificationType saleTransaction,
      String serviceId) {
    LoyaltyRequest.Builder loyaltyRequest =
        LoyaltyRequest.builder()
            .saleData(saleData(saleTransaction))
            .loyaltyTransaction(
                LoyaltyTransaction.builder()
                    .loyaltyTransactionType(refundType)
                    .originalPOITransaction(original)
                    .build());
    if (memberId != null) {
      loyaltyRequest.loyaltyData(
          new LoyaltyData[] {
            LoyaltyData.builder().loyaltyAccountID(Wire.memberAccount(memberId)).build()
          });
    }
    return SaleToPOIRequest.builder()
        .messageHeader(header(MessageClassType.SERVICE, MessageCategoryType.LOYALTY, serviceId))
        .loyaltyRequest(loyaltyRequest.build())
        .build();
  }

  /**
   * A {@code ReversalRequest} (merchant cancel) against a prior transaction, sent with {@code
   * serviceId}, minted by {@link #mintServiceId(MessageCategoryType)}.
   */
  public SaleToPOIRequest reversalRequest(
      OriginalPOITransaction original,
      TransactionIdentificationType saleTransaction,
      String serviceId) {
    return SaleToPOIRequest.builder()
        .messageHeader(header(MessageClassType.SERVICE, MessageCategoryType.REVERSAL, serviceId))
        .reversalRequest(
            ReversalRequest.builder()
                .saleData(saleData(saleTransaction))
                .originalPOITransaction(original)
                .reversalReason(ReversalReasonEnum.MERCHANT_CANCEL)
                .build())
        .build();
  }

  /**
   * A customer-display request carrying a Base64-encoded XHTML payload — the shape used for both
   * basket displays and custom payloads.
   */
  public SaleToPOIRequest displayRequest(String base64Xhtml) {
    return SaleToPOIRequest.builder()
        .messageHeader(header(MessageClassType.DEVICE, MessageCategoryType.DISPLAY))
        .displayRequest(
            DisplayRequest.builder()
                .displayOutput(
                    new DisplayOutput[] {
                      DisplayOutput.builder()
                          .device(DeviceEnum.CUSTOMER_DISPLAY)
                          .infoQualify(InfoQualifyEnum.DISPLAY)
                          .outputContent(
                              OutputContent.builder()
                                  .outputFormat(OutputFormatEnum.XHTML)
                                  .outputXHTML(base64Xhtml)
                                  .build())
                          .build()
                    })
                .build())
        .build();
  }
}
