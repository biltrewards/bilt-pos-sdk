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

import com.bilt.pos.nexo.model.AmountsReq;
import com.bilt.pos.nexo.model.EntryModeType;
import com.bilt.pos.nexo.model.IdentificationTypeEnum;
import com.bilt.pos.nexo.model.LoyaltyAccountID;
import com.bilt.pos.nexo.model.LoyaltyData;
import com.bilt.pos.nexo.model.LoyaltyRequest;
import com.bilt.pos.nexo.model.LoyaltyResponse;
import com.bilt.pos.nexo.model.LoyaltyResult;
import com.bilt.pos.nexo.model.LoyaltyTransaction;
import com.bilt.pos.nexo.model.LoyaltyTransactionTypeEnum;
import com.bilt.pos.nexo.model.MessageCategoryType;
import com.bilt.pos.nexo.model.MessageClassType;
import com.bilt.pos.nexo.model.OriginalPOITransaction;
import com.bilt.pos.nexo.model.PaymentData;
import com.bilt.pos.nexo.model.PaymentRequest;
import com.bilt.pos.nexo.model.PaymentResponse;
import com.bilt.pos.nexo.model.PaymentTransaction;
import com.bilt.pos.nexo.model.PaymentTypeEnum;
import com.bilt.pos.nexo.model.ReversalReasonEnum;
import com.bilt.pos.nexo.model.ReversalRequest;
import com.bilt.pos.nexo.model.ReversalResponse;
import com.bilt.pos.nexo.model.SaleToPOIRequest;
import com.bilt.pos.nexo.model.SaleToPOIResponse;
import com.bilt.pos.nexo.model.TransactionIdentificationType;
import com.bilt.pos.session.RefundResult;
import com.bilt.pos.session.SessionError;
import com.bilt.pos.session.SessionException;
import com.bilt.pos.session.VoidResult;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Refund and void flows: {@code PaymentRequest(Refund)} /
 * {@code ReversalRequest}, each followed for linked operations by a
 * best-effort {@code LoyaltyRequest(AwardRefund)}.
 */
public final class RefundManager {

    private static final Logger LOGGER = Logger.getLogger(RefundManager.class.getName());

    /** Points reversed and remaining balance from a loyalty reversal. */
    private static final class LoyaltyReversal {
        final int pointsReversed;
        final int remainingBalance;

        LoyaltyReversal(int pointsReversed, int remainingBalance) {
            this.pointsReversed = pointsReversed;
            this.remainingBalance = remainingBalance;
        }
    }

    private static final LoyaltyReversal NO_REVERSAL = new LoyaltyReversal(0, 0);

    /**
     * What the caller knows about the loyalty side of the original sale:
     * the award's own POI reference (preferred by the reverse-award
     * contract) and the member, both optional.
     */
    public static final class LoyaltyRef {
        final String awardPoiTransactionId;
        final Instant awardPoiTransactionTimestamp;
        final String memberId;

        public LoyaltyRef(String awardPoiTransactionId, Instant awardPoiTransactionTimestamp,
                          String memberId) {
            this.awardPoiTransactionId = awardPoiTransactionId;
            this.awardPoiTransactionTimestamp = awardPoiTransactionTimestamp;
            this.memberId = memberId;
        }

        public static final LoyaltyRef NONE = new LoyaltyRef(null, null, null);
    }

    private final NexoExchange exchange;
    private final String currency;

    public RefundManager(NexoExchange exchange, String currency) {
        this.exchange = exchange;
        this.currency = currency;
    }

    /**
     * Linked or unlinked refund.
     *
     * @param amount           the amount to refund, or {@code null} for a
     *                         full linked refund
     * @param originalPoiTxnId original transaction reference; {@code null}
     *                         for an unlinked refund
     */
    public RefundResult refund(BigDecimal amount, String originalPoiTxnId,
                               Instant originalPoiTimestamp, LoyaltyRef loyaltyRef) {
        AmountsReq.Builder amounts = AmountsReq.builder().currency(currency);
        if (amount != null) {
            amounts.requestedAmount(amount.doubleValue());
        }
        PaymentTransaction.Builder transaction = PaymentTransaction.builder()
                .amountsReq(amounts.build());
        if (originalPoiTxnId != null) {
            transaction.originalPOITransaction(originalTransaction(
                    originalPoiTxnId, originalPoiTimestamp));
        }
        SaleToPOIRequest request = SaleToPOIRequest.builder()
                .messageHeader(exchange.factory().header(
                        MessageClassType.SERVICE, MessageCategoryType.PAYMENT))
                .paymentRequest(PaymentRequest.builder()
                        .saleData(exchange.factory().saleData())
                        .paymentData(PaymentData.builder()
                                .paymentType(PaymentTypeEnum.REFUND)
                                .build())
                        .paymentTransaction(transaction.build())
                        .build())
                .build();

        SaleToPOIResponse response = exchange.sendExpectingBody(
                MessageCategoryType.PAYMENT, request);
        PaymentResponse body = response.getPaymentResponse();
        if (body == null) {
            throw Wire.missing("PaymentResponse");
        }
        exchange.requireSuccess(MessageCategoryType.PAYMENT, body.getResponse());

        // linked refunds also reverse loyalty points, best-effort
        LoyaltyReversal loyalty = originalPoiTxnId != null
                ? awardRefund(loyaltyRef, originalPoiTxnId, originalPoiTimestamp)
                : NO_REVERSAL;

        Double authorized = body.getPaymentResult() != null
                && body.getPaymentResult().getAmountsResp() != null
                ? body.getPaymentResult().getAmountsResp().getAuthorizedAmount() : null;
        TransactionIdentificationType poiTxn = body.getPoiData() == null
                ? null : body.getPoiData().getPoiTransactionID();
        return RefundResult.builder()
                .success(true)
                .refundedAmount(authorized != null
                        ? BigDecimal.valueOf(authorized)
                        : amount)
                .approvalCode(approvalCode(body))
                .poiTransactionId(poiTxn == null ? null : poiTxn.getTransactionID())
                .poiTransactionTimestamp(Wire.instant(
                        poiTxn == null ? null : poiTxn.getTimeStamp()))
                .customerReceipt(ReceiptMapper.customerReceipt(body.getPaymentReceipt()))
                .merchantReceipt(ReceiptMapper.merchantReceipt(body.getPaymentReceipt()))
                .pointsReversed(loyalty.pointsReversed)
                .remainingPointBalance(loyalty.remainingBalance)
                .build();
    }

    /** Reverses a completed transaction ({@code ReversalRequest} + award refund). */
    public VoidResult voidTransaction(String originalPoiTxnId, Instant originalPoiTimestamp) {
        return voidTransaction(originalPoiTxnId, originalPoiTimestamp, null, null,
                LoyaltyRef.NONE);
    }

    /**
     * Reverses a completed transaction; when a split tender's stored value
     * leg is supplied, both legs are reversed (card first, mirroring the
     * orchestrator's reverse-commit unwind order).
     */
    public VoidResult voidTransaction(String originalPoiTxnId, Instant originalPoiTimestamp,
                                      String storedValuePoiTxnId,
                                      Instant storedValuePoiTimestamp,
                                      LoyaltyRef loyaltyRef) {
        ReversalResponse cardLeg = reverse(originalPoiTxnId, originalPoiTimestamp);

        ReversalResponse storedValueLeg = null;
        if (storedValuePoiTxnId != null) {
            try {
                storedValueLeg = reverse(storedValuePoiTxnId, storedValuePoiTimestamp);
            } catch (SessionException e) {
                // the card leg is already reversed: surface exactly what
                // remains standing so the register can retry or escalate
                throw new SessionException(new SessionError(e.getError().getCode(),
                        "the card leg " + originalPoiTxnId + " was reversed but the stored "
                                + "value leg " + storedValuePoiTxnId + " was not: "
                                + e.getError().getMessage(),
                        e.getError().getNexoErrorCondition(), e));
            }
        }

        LoyaltyReversal loyalty = awardRefund(loyaltyRef, originalPoiTxnId,
                originalPoiTimestamp);

        BigDecimal reversedAmount = sumReversedAmounts(cardLeg, storedValueLeg);
        TransactionIdentificationType poiTxn = cardLeg.getPoiData() == null
                ? null : cardLeg.getPoiData().getPoiTransactionID();
        return VoidResult.builder()
                .success(true)
                .reversedAmount(reversedAmount)
                .poiTransactionId(poiTxn == null ? null : poiTxn.getTransactionID())
                .poiTransactionTimestamp(Wire.instant(
                        poiTxn == null ? null : poiTxn.getTimeStamp()))
                .customerReceipt(ReceiptMapper.customerReceipt(cardLeg.getPaymentReceipt()))
                .merchantReceipt(ReceiptMapper.merchantReceipt(cardLeg.getPaymentReceipt()))
                .pointsReversed(loyalty.pointsReversed)
                .remainingPointBalance(loyalty.remainingBalance)
                .build();
    }

    private ReversalResponse reverse(String originalPoiTxnId, Instant originalPoiTimestamp) {
        SaleToPOIRequest request = SaleToPOIRequest.builder()
                .messageHeader(exchange.factory().header(
                        MessageClassType.SERVICE, MessageCategoryType.REVERSAL))
                .reversalRequest(ReversalRequest.builder()
                        .saleData(exchange.factory().saleData())
                        .originalPOITransaction(originalTransaction(
                                originalPoiTxnId, originalPoiTimestamp))
                        .reversalReason(ReversalReasonEnum.MERCHANT_CANCEL)
                        .build())
                .build();
        SaleToPOIResponse response = exchange.sendExpectingBody(
                MessageCategoryType.REVERSAL, request);
        ReversalResponse body = response.getReversalResponse();
        if (body == null) {
            throw Wire.missing("ReversalResponse");
        }
        exchange.requireSuccess(MessageCategoryType.REVERSAL, body.getResponse());
        return body;
    }

    private static BigDecimal sumReversedAmounts(ReversalResponse cardLeg,
                                                 ReversalResponse storedValueLeg) {
        BigDecimal sum = null;
        for (ReversalResponse leg : new ReversalResponse[] {cardLeg, storedValueLeg}) {
            if (leg != null && leg.getReversedAmount() != null) {
                BigDecimal amount = BigDecimal.valueOf(leg.getReversedAmount());
                sum = sum == null ? amount : sum.add(amount);
            }
        }
        return sum;
    }

    /**
     * {@code LoyaltyRequest(AwardRefund)} against the original transaction.
     * Best-effort: failures are logged, never thrown — the money movement
     * already succeeded and the terminal can retry loyalty via SAF.
     */
    private LoyaltyReversal awardRefund(LoyaltyRef loyaltyRef, String paymentPoiTxnId,
                                        Instant paymentPoiTimestamp) {
        LoyaltyRef ref = loyaltyRef == null ? LoyaltyRef.NONE : loyaltyRef;
        // the reverse-award contract references the AWARD's own transaction;
        // fall back to the payment reference (terminal-side resolution) only
        // when the caller does not have the award reference
        String originalPoiTxnId = ref.awardPoiTransactionId != null
                ? ref.awardPoiTransactionId : paymentPoiTxnId;
        Instant originalPoiTimestamp = ref.awardPoiTransactionId != null
                ? ref.awardPoiTransactionTimestamp : paymentPoiTimestamp;
        LoyaltyRequest.Builder loyaltyRequest = LoyaltyRequest.builder()
                .saleData(exchange.factory().saleData())
                .loyaltyTransaction(LoyaltyTransaction.builder()
                        .loyaltyTransactionType(LoyaltyTransactionTypeEnum.AWARD_REFUND)
                        .originalPOITransaction(originalTransaction(
                                originalPoiTxnId, originalPoiTimestamp))
                        .build());
        if (ref.memberId != null) {
            loyaltyRequest.loyaltyData(new LoyaltyData[] {LoyaltyData.builder()
                    .loyaltyAccountID(LoyaltyAccountID.builder()
                            .loyaltyID(ref.memberId)
                            .identificationType(IdentificationTypeEnum.PAN)
                            .entryMode(new EntryModeType[] {EntryModeType.KEYED})
                            .build())
                    .build()});
        }
        SaleToPOIRequest request = SaleToPOIRequest.builder()
                .messageHeader(exchange.factory().header(
                        MessageClassType.SERVICE, MessageCategoryType.LOYALTY))
                .loyaltyRequest(loyaltyRequest.build())
                .build();
        try {
            SaleToPOIResponse response = exchange.sendExpectingBody(
                    MessageCategoryType.LOYALTY, request);
            LoyaltyResponse body = response.getLoyaltyResponse();
            if (body == null) {
                throw Wire.missing("LoyaltyResponse");
            }
            exchange.requireSuccess(MessageCategoryType.LOYALTY, body.getResponse());
            LoyaltyResult result = body.getLoyaltyResult() != null
                    && body.getLoyaltyResult().length > 0 ? body.getLoyaltyResult()[0] : null;
            if (result == null) {
                return NO_REVERSAL;
            }
            int points = result.getLoyaltyAmount() == null
                    ? 0 : (int) Math.round(result.getLoyaltyAmount().getAmountValue());
            int balance = result.getCurrentBalance() == null
                    ? 0 : (int) Math.round(result.getCurrentBalance());
            return new LoyaltyReversal(points, balance);
        } catch (SessionException e) {
            LOGGER.log(Level.WARNING,
                    "loyalty award reversal failed (terminal may retry via SAF): "
                            + e.getError(), e);
            return NO_REVERSAL;
        }
    }

    // ─── Internals ───

    private static OriginalPOITransaction originalTransaction(String poiTxnId, Instant timestamp) {
        return OriginalPOITransaction.builder()
                .poiTransactionID(TransactionIdentificationType.builder()
                        .transactionID(poiTxnId)
                        .timeStamp(timestamp == null ? null : timestamp.toString())
                        .build())
                .build();
    }

    private static String approvalCode(PaymentResponse body) {
        if (body.getPaymentResult() == null
                || body.getPaymentResult().getPaymentAcquirerData() == null) {
            return null;
        }
        return body.getPaymentResult().getPaymentAcquirerData().getApprovalCode();
    }
}
