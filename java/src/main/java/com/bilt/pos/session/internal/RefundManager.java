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
import com.bilt.pos.nexo.model.LoyaltyAmount;
import com.bilt.pos.nexo.model.LoyaltyData;
import com.bilt.pos.nexo.model.LoyaltyRequest;
import com.bilt.pos.nexo.model.LoyaltyResponse;
import com.bilt.pos.nexo.model.LoyaltyResult;
import com.bilt.pos.nexo.model.LoyaltyTransaction;
import com.bilt.pos.nexo.model.LoyaltyTransactionTypeEnum;
import com.bilt.pos.nexo.model.LoyaltyUnitEnum;
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
import com.bilt.pos.nexo.model.SaleData;
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

    /**
     * The committed loyalty movements of the original sale — redemption and
     * rebate references plus the rewardRefs payload the redemption refund
     * must carry. All fields optional.
     */
    public static final class LoyaltyLegs {
        public static final LoyaltyLegs NONE = new LoyaltyLegs(null, null, null, null, null);

        final String redemptionPoiTxnId;
        final Instant redemptionPoiTimestamp;
        final String rewardRefsPayload;
        final String rebatePoiTxnId;
        final Instant rebatePoiTimestamp;

        public LoyaltyLegs(String redemptionPoiTxnId, Instant redemptionPoiTimestamp,
                           String rewardRefsPayload,
                           String rebatePoiTxnId, Instant rebatePoiTimestamp) {
            this.redemptionPoiTxnId = redemptionPoiTxnId;
            this.redemptionPoiTimestamp = redemptionPoiTimestamp;
            this.rewardRefsPayload = rewardRefsPayload;
            this.rebatePoiTxnId = rebatePoiTxnId;
            this.rebatePoiTimestamp = rebatePoiTimestamp;
        }
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
        TransactionIdentificationType poiTxn = Wire.poiRef(body.getPoiData());
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

    /**
     * Reverses a completed transaction ({@code ReversalRequest} per tender
     * leg, card first — mirroring the orchestrator's reverse-commit unwind
     * order — then best-effort refunds of the sale's committed loyalty
     * movements and the award). {@code cardPoiTxnId} may be {@code null} on
     * a retry whose card leg was already reversed — only the stored value
     * leg and the loyalty refunds run then.
     *
     * @param onCardLegReversed invoked right after the card leg reversal
     *        succeeds, so the caller can record progress before a later leg
     *        fails and make its retry resumable
     */
    public VoidResult voidTransaction(String cardPoiTxnId, Instant cardPoiTimestamp,
                                      String storedValuePoiTxnId,
                                      Instant storedValuePoiTimestamp,
                                      LoyaltyLegs loyaltyLegs,
                                      LoyaltyRef loyaltyRef, Runnable onCardLegReversed) {
        if (cardPoiTxnId == null && storedValuePoiTxnId == null) {
            throw new IllegalArgumentException("nothing to void: no leg references");
        }
        ReversalResponse cardLeg = null;
        if (cardPoiTxnId != null) {
            cardLeg = reverse(cardPoiTxnId, cardPoiTimestamp);
            if (onCardLegReversed != null) {
                onCardLegReversed.run();
            }
        }

        ReversalResponse storedValueLeg = null;
        if (storedValuePoiTxnId != null) {
            try {
                storedValueLeg = reverse(storedValuePoiTxnId, storedValuePoiTimestamp);
            } catch (SessionException e) {
                if (cardPoiTxnId == null) {
                    // stored-value-only retry: nothing new was reversed
                    throw e;
                }
                throw partialUnwind("the card leg", cardPoiTxnId,
                        "the stored value leg", storedValuePoiTxnId, e);
            }
        }

        // a full void also returns the sale's committed loyalty movements.
        // Best-effort, like the award refund below: the tender reversal is
        // the substance of the void, the terminal can retry loyalty via
        // SAF, and a failed refund must not strand a half-voided tender
        LoyaltyLegs legs = loyaltyLegs == null ? LoyaltyLegs.NONE : loyaltyLegs;
        LoyaltyRef ref = loyaltyRef == null ? LoyaltyRef.NONE : loyaltyRef;
        bestEffortLoyaltyRefund(LoyaltyTransactionTypeEnum.REDEMPTION_REFUND,
                legs.redemptionPoiTxnId, legs.redemptionPoiTimestamp,
                ref.memberId, legs.rewardRefsPayload);
        bestEffortLoyaltyRefund(LoyaltyTransactionTypeEnum.REBATE_REFUND,
                legs.rebatePoiTxnId, legs.rebatePoiTimestamp, ref.memberId, null);

        LoyaltyReversal loyalty = awardRefund(loyaltyRef,
                cardPoiTxnId != null ? cardPoiTxnId : storedValuePoiTxnId,
                cardPoiTxnId != null ? cardPoiTimestamp : storedValuePoiTimestamp);

        ReversalResponse primary = cardLeg != null ? cardLeg : storedValueLeg;
        BigDecimal reversedAmount = sumReversedAmounts(cardLeg, storedValueLeg);
        TransactionIdentificationType poiTxn = Wire.poiRef(primary.getPoiData());
        return VoidResult.builder()
                .success(true)
                .reversedAmount(reversedAmount)
                .poiTransactionId(poiTxn == null ? null : poiTxn.getTransactionID())
                .poiTransactionTimestamp(Wire.instant(
                        poiTxn == null ? null : poiTxn.getTimeStamp()))
                .customerReceipt(ReceiptMapper.customerReceipt(primary.getPaymentReceipt()))
                .merchantReceipt(ReceiptMapper.merchantReceipt(primary.getPaymentReceipt()))
                .pointsReversed(loyalty.pointsReversed)
                .remainingPointBalance(loyalty.remainingBalance)
                .build();
    }

    /**
     * Voids a checkout that had no payment legs — rewards covered the whole
     * basket — by refunding the committed loyalty movements: redemption and
     * rebate strictly, then the award. The redemption and rebate refunds
     * throw on failure so the caller can retry; {@code onRedemptionReversed}
     * records progress so a retry resumes at the rebate leg instead of
     * re-crediting the redemption. When the award is the only movement,
     * reversing it IS the void, so unlike the payment-void paths it is
     * strict rather than best-effort.
     */
    public VoidResult voidLoyalty(LoyaltyLegs loyaltyLegs,
                                  LoyaltyRef loyaltyRef, Runnable onRedemptionReversed) {
        LoyaltyLegs legs = loyaltyLegs == null ? LoyaltyLegs.NONE : loyaltyLegs;
        LoyaltyRef ref = loyaltyRef == null ? LoyaltyRef.NONE : loyaltyRef;
        if (legs.redemptionPoiTxnId == null && legs.rebatePoiTxnId == null
                && ref.awardPoiTransactionId == null) {
            throw new IllegalArgumentException("nothing to void: no loyalty movement references");
        }

        LoyaltyResponse redemptionLeg = null;
        if (legs.redemptionPoiTxnId != null) {
            redemptionLeg = loyaltyRefund(LoyaltyTransactionTypeEnum.REDEMPTION_REFUND,
                    legs.redemptionPoiTxnId, legs.redemptionPoiTimestamp,
                    ref.memberId, legs.rewardRefsPayload);
            if (onRedemptionReversed != null) {
                onRedemptionReversed.run();
            }
        }

        LoyaltyResponse rebateLeg = null;
        if (legs.rebatePoiTxnId != null) {
            try {
                rebateLeg = loyaltyRefund(LoyaltyTransactionTypeEnum.REBATE_REFUND,
                        legs.rebatePoiTxnId, legs.rebatePoiTimestamp, ref.memberId, null);
            } catch (SessionException e) {
                if (redemptionLeg == null) {
                    // rebate-only (or resumed) void: nothing new was reversed
                    throw e;
                }
                throw partialUnwind("the redemption", legs.redemptionPoiTxnId,
                        "the rebate", legs.rebatePoiTxnId, e);
            }
        }

        LoyaltyReversal loyalty;
        LoyaltyResponse awardLeg = null;
        if (redemptionLeg == null && rebateLeg == null) {
            awardLeg = loyaltyRefund(LoyaltyTransactionTypeEnum.AWARD_REFUND,
                    ref.awardPoiTransactionId, ref.awardPoiTransactionTimestamp,
                    ref.memberId, null);
            loyalty = parseReversal(awardLeg);
        } else {
            loyalty = awardRefund(ref,
                    legs.redemptionPoiTxnId != null
                            ? legs.redemptionPoiTxnId : legs.rebatePoiTxnId,
                    legs.redemptionPoiTxnId != null
                            ? legs.redemptionPoiTimestamp : legs.rebatePoiTimestamp);
        }

        LoyaltyResponse primary = redemptionLeg != null ? redemptionLeg
                : rebateLeg != null ? rebateLeg : awardLeg;
        TransactionIdentificationType poiTxn = Wire.poiRef(primary.getPoiData());
        return VoidResult.builder()
                .success(true)
                .reversedAmount(sumLoyaltyAmounts(redemptionLeg, rebateLeg))
                .poiTransactionId(poiTxn == null ? null : poiTxn.getTransactionID())
                .poiTransactionTimestamp(Wire.instant(
                        poiTxn == null ? null : poiTxn.getTimeStamp()))
                .pointsReversed(loyalty.pointsReversed)
                .remainingPointBalance(loyalty.remainingBalance)
                .build();
    }

    /**
     * A loyalty refund that accompanies a money movement: logged on
     * failure, never thrown — the terminal can retry loyalty via SAF.
     * No-op without a reference. Returns the response, or {@code null}
     * when skipped or failed.
     */
    private LoyaltyResponse bestEffortLoyaltyRefund(LoyaltyTransactionTypeEnum refundType,
                                                    String originalPoiTxnId,
                                                    Instant originalPoiTimestamp,
                                                    String memberId, String rewardRefsPayload) {
        if (originalPoiTxnId == null) {
            return null;
        }
        try {
            return loyaltyRefund(refundType, originalPoiTxnId, originalPoiTimestamp,
                    memberId, rewardRefsPayload);
        } catch (SessionException e) {
            LOGGER.log(Level.WARNING, refundType
                    + " failed (terminal may retry via SAF): " + e.getError(), e);
            return null;
        }
    }

    /**
     * A strict loyalty refund: {@code LoyaltyRequest(refundType)} against
     * the movement's own POI reference, throwing on failure.
     */
    private LoyaltyResponse loyaltyRefund(LoyaltyTransactionTypeEnum refundType,
                                          String originalPoiTxnId, Instant originalPoiTimestamp,
                                          String memberId, String saleToPoiData) {
        SaleData saleData = exchange.factory().saleData();
        if (saleToPoiData != null) {
            // the reverse-redemption contract carries the rewardRefs to
            // re-credit in SaleToPOIData, mirroring the redemption
            saleData.setSaleToPOIData(saleToPoiData);
        }
        LoyaltyRequest.Builder loyaltyRequest = LoyaltyRequest.builder()
                .saleData(saleData)
                .loyaltyTransaction(LoyaltyTransaction.builder()
                        .loyaltyTransactionType(refundType)
                        .originalPOITransaction(originalTransaction(
                                originalPoiTxnId, originalPoiTimestamp))
                        .build());
        if (memberId != null) {
            loyaltyRequest.loyaltyData(new LoyaltyData[] {LoyaltyData.builder()
                    .loyaltyAccountID(Wire.memberAccount(memberId))
                    .build()});
        }
        SaleToPOIRequest request = SaleToPOIRequest.builder()
                .messageHeader(exchange.factory().header(
                        MessageClassType.SERVICE, MessageCategoryType.LOYALTY))
                .loyaltyRequest(loyaltyRequest.build())
                .build();
        SaleToPOIResponse response = exchange.sendExpectingBody(
                MessageCategoryType.LOYALTY, request);
        LoyaltyResponse body = response.getLoyaltyResponse();
        if (body == null) {
            throw Wire.missing("LoyaltyResponse");
        }
        exchange.requireSuccess(MessageCategoryType.LOYALTY, body.getResponse());
        return body;
    }

    /** Points and balance reported by a loyalty reversal response. */
    private static LoyaltyReversal parseReversal(LoyaltyResponse body) {
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
    }

    /** Sum of the monetary amounts the refund legs report, or {@code null}. */
    private static BigDecimal sumLoyaltyAmounts(LoyaltyResponse... legs) {
        BigDecimal sum = null;
        for (LoyaltyResponse leg : legs) {
            if (leg == null || leg.getLoyaltyResult() == null
                    || leg.getLoyaltyResult().length == 0) {
                continue;
            }
            LoyaltyAmount amount = leg.getLoyaltyResult()[0].getLoyaltyAmount();
            if (amount == null || amount.getLoyaltyUnit() == LoyaltyUnitEnum.POINT) {
                continue;
            }
            BigDecimal value = BigDecimal.valueOf(amount.getAmountValue());
            sum = sum == null ? value : sum.add(value);
        }
        return sum;
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

    private static BigDecimal sumReversedAmounts(ReversalResponse... legs) {
        BigDecimal sum = null;
        for (ReversalResponse leg : legs) {
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
        LoyaltyResponse body = bestEffortLoyaltyRefund(LoyaltyTransactionTypeEnum.AWARD_REFUND,
                originalPoiTxnId, originalPoiTimestamp, ref.memberId, null);
        return body == null ? NO_REVERSAL : parseReversal(body);
    }

    // ─── Internals ───

    /**
     * A void failed between legs: surface exactly what was reversed and
     * what remains standing so the register can retry (the reversed leg is
     * skipped on retry) or escalate.
     */
    private static SessionException partialUnwind(String doneLabel, String doneId,
                                                  String pendingLabel, String pendingId,
                                                  SessionException cause) {
        return new SessionException(Wire.annotated(cause.getError(),
                doneLabel + " " + doneId + " was reversed but " + pendingLabel + " "
                        + pendingId + " was not: " + cause.getError().getMessage(), cause));
    }

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
