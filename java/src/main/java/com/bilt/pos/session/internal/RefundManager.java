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
import com.bilt.pos.nexo.model.LoyaltyResponse;
import com.bilt.pos.nexo.model.LoyaltyResult;
import com.bilt.pos.nexo.model.LoyaltyTransactionTypeEnum;
import com.bilt.pos.nexo.model.LoyaltyUnitEnum;
import com.bilt.pos.nexo.model.MessageCategoryType;
import com.bilt.pos.nexo.model.MessageClassType;
import com.bilt.pos.nexo.model.PaymentData;
import com.bilt.pos.nexo.model.PaymentRequest;
import com.bilt.pos.nexo.model.PaymentResponse;
import com.bilt.pos.nexo.model.PaymentTransaction;
import com.bilt.pos.nexo.model.PaymentTypeEnum;
import com.bilt.pos.nexo.model.ReversalResponse;
import com.bilt.pos.nexo.model.SaleToPOIRequest;
import com.bilt.pos.nexo.model.SaleToPOIResponse;
import com.bilt.pos.session.RefundResult;
import com.bilt.pos.session.ReversalDecision;
import com.bilt.pos.session.ReversalStep;
import com.bilt.pos.session.SessionError;
import com.bilt.pos.session.SessionException;
import com.bilt.pos.session.VoidResult;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Refund and void flows. A void is a list of {@link ReversalMovement}s
 * executed in order — money legs as Nexo {@code ReversalRequest}s, loyalty
 * legs as the matching {@code LoyaltyRequest} refund types — and a refund
 * is a {@code PaymentRequest(Refund)} followed by an award reversal when
 * the award reference is known. When a step fails, the caller-supplied
 * {@link StepDecider} chooses between retrying it, leaving it standing, or
 * aborting the flow; already-reversed steps always stand. A {@code null}
 * decider selects the default policy (see {@link #defaultPolicy}).
 */
public final class RefundManager {

    private static final Logger LOGGER = Logger.getLogger(RefundManager.class.getName());

    private static final Map<ReversalStep, LoyaltyTransactionTypeEnum> LOYALTY_REFUND_TYPES =
            Map.of(ReversalStep.REDEMPTION, LoyaltyTransactionTypeEnum.REDEMPTION_REFUND,
                    ReversalStep.REBATE, LoyaltyTransactionTypeEnum.REBATE_REFUND,
                    ReversalStep.AWARD, LoyaltyTransactionTypeEnum.AWARD_REFUND);

    /** Resolves a failed step into a {@link ReversalDecision}. */
    public interface StepDecider {
        ReversalDecision decide(ReversalStep step, SessionError error);
    }

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

    private final NexoExchange exchange;
    private final String currency;

    public RefundManager(NexoExchange exchange, String currency) {
        this.exchange = exchange;
        this.currency = currency;
    }

    // ─── Refund ───

    /**
     * Linked or unlinked refund: the tender refund, then — when the award
     * reference of a linked original is known — the award reversal.
     *
     * @param amount           the amount to refund, or {@code null} for a
     *                         full linked refund
     * @param originalPoiTxnId original transaction reference; {@code null}
     *                         for an unlinked refund
     * @param awardPoiTxnId    the original sale's award reference (linked
     *                         refunds only), or {@code null} when no award
     *                         is to be reversed
     * @param decider          per-step failure resolution; {@code null}
     *                         for the default policy (tender aborts, award
     *                         is best-effort)
     * @param onRefunded       invoked the moment the tender refund
     *                         completes, before the award reversal — the
     *                         caller's void guard must rise as soon as
     *                         money moves, even if a later step aborts the
     *                         flow
     */
    public RefundResult refund(BigDecimal amount, String originalPoiTxnId,
                               Instant originalPoiTimestamp,
                               String awardPoiTxnId, Instant awardPoiTimestamp,
                               String memberId, StepDecider decider, Runnable onRefunded) {
        StepDecider effective = decider != null ? decider : defaultPolicy(true, false);
        SessionException[] lastFailure = new SessionException[1];

        PaymentResponse body = runStep(ReversalStep.REFUND, effective,
                () -> sendRefund(amount, originalPoiTxnId, originalPoiTimestamp),
                e -> {
                    lastFailure[0] = e;
                    LOGGER.warning("the tender refund was skipped by decision; "
                            + "no money moved: " + e.getError());
                },
                e -> e);
        if (body != null) {
            onRefunded.run();
        }

        LoyaltyReversal loyalty = NO_REVERSAL;
        boolean awardReversed = false;
        if (awardPoiTxnId != null) {
            LoyaltyResponse awardLeg = runStep(ReversalStep.AWARD, effective,
                    () -> loyaltyRefund(LoyaltyTransactionTypeEnum.AWARD_REFUND,
                            awardPoiTxnId, awardPoiTimestamp, memberId),
                    e -> {
                        lastFailure[0] = e;
                        LOGGER.warning("AwardRefund failed (terminal may retry via "
                                + "SAF): " + e.getError());
                    },
                    e -> body == null ? e : new SessionException(Wire.annotated(
                            e.getError(),
                            "the tender refund completed but the award " + awardPoiTxnId
                                    + " was not reversed: " + e.getError().getMessage(), e)));
            if (awardLeg != null) {
                loyalty = parseReversal(awardLeg);
                awardReversed = true;
            }
        }

        if (body == null) {
            if (!awardReversed) {
                // every step was skipped: nothing moved, so the refund
                // must not report success
                throw lastFailure[0];
            }
            // the tender refund was skipped by decision; only the award moved
            return RefundResult.builder()
                    .success(true)
                    .pointsReversed(loyalty.pointsReversed)
                    .remainingPointBalance(loyalty.remainingBalance)
                    .build();
        }
        Double authorized = body.getPaymentResult() != null
                && body.getPaymentResult().getAmountsResp() != null
                ? body.getPaymentResult().getAmountsResp().getAuthorizedAmount() : null;
        return RefundResult.builder()
                .success(true)
                .refundedAmount(authorized != null ? BigDecimal.valueOf(authorized) : amount)
                .approvalCode(Wire.approvalCode(body))
                .poiTransactionId(Wire.txnId(body.getPoiData()))
                .poiTransactionTimestamp(Wire.txnTimestamp(body.getPoiData()))
                .customerReceipt(ReceiptMapper.customerReceipt(body.getPaymentReceipt()))
                .merchantReceipt(ReceiptMapper.merchantReceipt(body.getPaymentReceipt()))
                .pointsReversed(loyalty.pointsReversed)
                .remainingPointBalance(loyalty.remainingBalance)
                .build();
    }

    private PaymentResponse sendRefund(BigDecimal amount, String originalPoiTxnId,
                                       Instant originalPoiTimestamp) {
        AmountsReq.Builder amounts = AmountsReq.builder().currency(currency);
        if (amount != null) {
            amounts.requestedAmount(amount.doubleValue());
        }
        PaymentTransaction.Builder transaction = PaymentTransaction.builder()
                .amountsReq(amounts.build());
        if (originalPoiTxnId != null) {
            transaction.originalPOITransaction(Wire.originalTransaction(
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
        return body;
    }

    // ─── Void ───

    /**
     * Reverses the given movements in order. Each failed step is resolved
     * by the decider — retry, leave standing, or abort; on abort the
     * failure is annotated with what was already reversed so the register
     * can retry (reversed movements are reported via {@code onReversed}
     * and must be excluded from the retry) or escalate. If no movement at
     * all was reversed, the last failure is thrown rather than reporting a
     * void that changed nothing. An empty list is a success: nothing is
     * left standing — the unwind was drained, or a prior partial void
     * already reversed every movement.
     */
    public VoidResult voidMovements(List<ReversalMovement> movements, String memberId,
                                    StepDecider decider,
                                    Consumer<ReversalMovement> onReversed) {
        if (movements.isEmpty()) {
            return VoidResult.builder().success(true).build();
        }
        StepDecider effective = decider != null ? decider : defaultPolicy(
                movements.stream().anyMatch(movement -> isMoneyLeg(movement.getStep())),
                movements.size() == 1
                        && movements.get(0).getStep() == ReversalStep.AWARD);
        Map<ReversalStep, ReversalResponse> money = new EnumMap<>(ReversalStep.class);
        Map<ReversalStep, LoyaltyResponse> loyalty = new EnumMap<>(ReversalStep.class);
        List<ReversalMovement> reversed = new ArrayList<>();
        SessionException[] lastFailure = new SessionException[1];

        for (ReversalMovement movement : movements) {
            Boolean sent = runStep(movement.getStep(), effective,
                    () -> {
                        execute(movement, memberId, money, loyalty);
                        return true;
                    },
                    e -> {
                        lastFailure[0] = e;
                        LOGGER.warning(movement.getStep() + " reversal of "
                                + movement.poiTransactionId + " skipped, the movement "
                                + "is still standing (terminal may retry loyalty via "
                                + "SAF): " + e.getError());
                    },
                    e -> abortError(reversed, movement, e));
            if (sent != null) {
                reversed.add(movement);
                onReversed.accept(movement);
            }
        }
        if (reversed.isEmpty()) {
            // every movement was skipped: nothing was reversed, so the
            // void must not report success
            throw lastFailure[0];
        }
        return buildVoidResult(money, loyalty);
    }

    private void execute(ReversalMovement movement, String memberId,
                         Map<ReversalStep, ReversalResponse> money,
                         Map<ReversalStep, LoyaltyResponse> loyalty) {
        ReversalStep step = movement.getStep();
        if (isMoneyLeg(step)) {
            money.put(step, reverse(
                    movement.poiTransactionId, movement.poiTransactionTimestamp));
            return;
        }
        LoyaltyTransactionTypeEnum refundType = LOYALTY_REFUND_TYPES.get(step);
        if (refundType == null) {
            throw new IllegalArgumentException(step + " is not a void movement");
        }
        loyalty.put(step, loyaltyRefund(refundType,
                movement.poiTransactionId, movement.poiTransactionTimestamp, memberId));
    }

    private static VoidResult buildVoidResult(Map<ReversalStep, ReversalResponse> money,
                                              Map<ReversalStep, LoyaltyResponse> loyalty) {
        ReversalResponse cardLeg = money.get(ReversalStep.CARD);
        ReversalResponse storedValueLeg = money.get(ReversalStep.STORED_VALUE);
        ReversalResponse primaryMoney = cardLeg != null ? cardLeg : storedValueLeg;
        LoyaltyResponse awardLeg = loyalty.get(ReversalStep.AWARD);
        LoyaltyReversal points = awardLeg == null ? NO_REVERSAL : parseReversal(awardLeg);

        // the reversed amount is what the money legs report; a void with no
        // money legs reports the monetary value of the loyalty refunds
        BigDecimal reversedAmount = primaryMoney == null
                ? sumLoyaltyAmounts(loyalty.get(ReversalStep.REDEMPTION),
                        loyalty.get(ReversalStep.REBATE))
                : sumReversedAmounts(cardLeg, storedValueLeg);

        VoidResult.Builder result = VoidResult.builder()
                .success(true)
                .reversedAmount(reversedAmount)
                .pointsReversed(points.pointsReversed)
                .remainingPointBalance(points.remainingBalance);

        if (primaryMoney != null) {
            result.poiTransactionId(Wire.txnId(primaryMoney.getPoiData()))
                    .poiTransactionTimestamp(Wire.txnTimestamp(primaryMoney.getPoiData()))
                    .customerReceipt(ReceiptMapper.customerReceipt(
                            primaryMoney.getPaymentReceipt()))
                    .merchantReceipt(ReceiptMapper.merchantReceipt(
                            primaryMoney.getPaymentReceipt()));
        } else {
            LoyaltyResponse primary = loyalty.values().iterator().next();
            result.poiTransactionId(Wire.txnId(primary.getPoiData()))
                    .poiTransactionTimestamp(Wire.txnTimestamp(primary.getPoiData()));
        }
        return result.build();
    }

    // ─── Step mechanics ───

    /**
     * Runs one reversal step under the decider: {@code RETRY} re-sends,
     * {@code SKIP} invokes {@code onSkip} and returns {@code null},
     * {@code ABORT} (or a {@code null} decision) throws via
     * {@code onAbort}.
     */
    private static <T> T runStep(ReversalStep step, StepDecider decider, Supplier<T> action,
                                 Consumer<SessionException> onSkip,
                                 UnaryOperator<SessionException> onAbort) {
        while (true) {
            try {
                return action.get();
            } catch (SessionException e) {
                ReversalDecision decision = decider.decide(step, e.getError());
                if (decision == ReversalDecision.RETRY) {
                    continue;
                }
                if (decision == ReversalDecision.SKIP) {
                    onSkip.accept(e);
                    return null;
                }
                throw onAbort.apply(e);
            }
        }
    }

    /**
     * The default decision policy, stated once beside the mechanism: money
     * legs abort; loyalty movements are best-effort when a money leg
     * anchors the reversal, strict when they are its substance (the award
     * only when it is the whole reversal — otherwise the terminal can
     * retry it via store-and-forward).
     */
    private static StepDecider defaultPolicy(boolean moneyAnchored, boolean awardIsTheWhole) {
        return (step, error) -> {
            switch (step) {
                case REDEMPTION:
                case REBATE:
                    return moneyAnchored ? ReversalDecision.SKIP : ReversalDecision.ABORT;
                case AWARD:
                    return awardIsTheWhole ? ReversalDecision.ABORT : ReversalDecision.SKIP;
                default:  // CARD, STORED_VALUE, REFUND — the money moved
                    return ReversalDecision.ABORT;
            }
        };
    }

    private static boolean isMoneyLeg(ReversalStep step) {
        return step == ReversalStep.CARD || step == ReversalStep.STORED_VALUE;
    }

    /**
     * A reversal aborted between steps: surface exactly what was reversed
     * and what remains standing so the register can retry (reversed steps
     * are skipped on retry) or escalate.
     */
    private static SessionException abortError(List<ReversalMovement> reversed,
                                               ReversalMovement failed,
                                               SessionException cause) {
        if (reversed.isEmpty()) {
            return cause;
        }
        String done = reversed.stream()
                .map(movement -> label(movement.getStep()) + " " + movement.poiTransactionId)
                .collect(Collectors.joining(" and "));
        return new SessionException(Wire.annotated(cause.getError(),
                done + (reversed.size() == 1 ? " was" : " were") + " reversed but "
                        + label(failed.getStep()) + " " + failed.poiTransactionId
                        + " was not: " + cause.getError().getMessage(), cause));
    }

    private static String label(ReversalStep step) {
        switch (step) {
            case CARD: return "the card leg";
            case STORED_VALUE: return "the stored value leg";
            case REDEMPTION: return "the redemption";
            case REBATE: return "the rebate";
            case AWARD: return "the award";
            default: return "the tender refund";
        }
    }

    // ─── Wire verbs ───

    /**
     * A loyalty refund against the movement's own POI reference, throwing
     * on failure.
     */
    private LoyaltyResponse loyaltyRefund(LoyaltyTransactionTypeEnum refundType,
                                          String originalPoiTxnId, Instant originalPoiTimestamp,
                                          String memberId) {
        SaleToPOIRequest request = exchange.factory().loyaltyRefundRequest(refundType,
                Wire.originalTransaction(originalPoiTxnId, originalPoiTimestamp), memberId);
        SaleToPOIResponse response = exchange.sendExpectingBody(
                MessageCategoryType.LOYALTY, request);
        LoyaltyResponse body = response.getLoyaltyResponse();
        if (body == null) {
            throw Wire.missing("LoyaltyResponse");
        }
        exchange.requireSuccess(MessageCategoryType.LOYALTY, body.getResponse());
        return body;
    }

    private ReversalResponse reverse(String originalPoiTxnId, Instant originalPoiTimestamp) {
        SaleToPOIRequest request = exchange.factory().reversalRequest(
                Wire.originalTransaction(originalPoiTxnId, originalPoiTimestamp));
        SaleToPOIResponse response = exchange.sendExpectingBody(
                MessageCategoryType.REVERSAL, request);
        ReversalResponse body = response.getReversalResponse();
        if (body == null) {
            throw Wire.missing("ReversalResponse");
        }
        exchange.requireSuccess(MessageCategoryType.REVERSAL, body.getResponse());
        return body;
    }

    // ─── Response parsing ───

    /** Points and balance reported by a loyalty reversal response. */
    private static LoyaltyReversal parseReversal(LoyaltyResponse body) {
        LoyaltyResult result = Wire.firstLoyaltyResult(body);
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
            if (leg == null) {
                continue;
            }
            LoyaltyResult result = Wire.firstLoyaltyResult(leg);
            LoyaltyAmount amount = result == null ? null : result.getLoyaltyAmount();
            if (amount == null || amount.getLoyaltyUnit() == LoyaltyUnitEnum.POINT) {
                continue;
            }
            BigDecimal value = BigDecimal.valueOf(amount.getAmountValue());
            sum = sum == null ? value : sum.add(value);
        }
        return sum;
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
}
