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
import com.bilt.pos.nexo.model.SaleItem;
import com.bilt.pos.nexo.model.SaleToPOIRequest;
import com.bilt.pos.nexo.model.SaleToPOIResponse;
import com.bilt.pos.nexo.model.StoredValueTransactionTypeEnum;
import com.bilt.pos.session.RefundResult;
import com.bilt.pos.session.ReversalDecision;
import com.bilt.pos.session.ReversalStep;
import com.bilt.pos.session.SessionError;
import com.bilt.pos.session.SessionException;
import com.bilt.pos.session.VoidResult;
import com.bilt.pos.session.storedvalue.StoredValueOperationResult;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
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
public final class ReversalManager {

    private static final Logger LOGGER = Logger.getLogger(ReversalManager.class.getName());

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
    private final StoredValueManager storedValueManager;

    public ReversalManager(NexoExchange exchange, String currency,
                           StoredValueManager storedValueManager) {
        this.exchange = exchange;
        this.currency = currency;
        this.storedValueManager = Objects.requireNonNull(
                storedValueManager, "storedValueManager");
    }

    // ─── Refund ───

    /**
     * Linked or unlinked refund: the tender refund, then — when the award
     * reference of a linked original is known — the award reversal.
     *
     * @param amount           the amount to refund, or {@code null} for a
     *                         full linked refund
     * @param saleItems        the refunded items, attached to the refund's
     *                         {@code PaymentTransaction} (magnitudes only —
     *                         the payment type carries the direction), or
     *                         {@code null} for an amount-only refund
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
     *                         flow — and only then: a tender skipped by
     *                         decision leaves the sale voidable
     * @param onAwardReversed  invoked when the award reversal completes,
     *                         so the caller records it as reversal
     *                         progress — a later void must not re-credit
     *                         it
     */
    public RefundResult refund(BigDecimal amount, List<SaleItem> saleItems,
                               String originalPoiTxnId,
                               Instant originalPoiTimestamp,
                               String awardPoiTxnId, Instant awardPoiTimestamp,
                               String memberId, StepDecider decider, Runnable onRefunded,
                               Runnable onAwardReversed) {
        return refund(amount, saleItems, originalPoiTxnId, originalPoiTimestamp,
                awardPoiTxnId, awardPoiTimestamp, memberId, null, decider, onRefunded,
                onAwardReversed);
    }

    public RefundResult refund(BigDecimal amount, List<SaleItem> saleItems,
                               String originalPoiTxnId,
                               Instant originalPoiTimestamp,
                               String awardPoiTxnId, Instant awardPoiTimestamp,
                               String memberId, String saleTransactionId,
                               StepDecider decider, Runnable onRefunded,
                               Runnable onAwardReversed) {
        // the tender refund anchors the flow, so the award is best-effort
        StepDecider effective = decider != null ? decider : defaultPolicy(true);
        SessionException[] lastFailure = new SessionException[1];

        PaymentResponse body = runStep(ReversalStep.CARD, effective,
                () -> sendRefund(amount, saleItems, originalPoiTxnId, originalPoiTimestamp,
                        saleTransactionId),
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
                            awardPoiTxnId, awardPoiTimestamp, memberId, null),
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
                onAwardReversed.run();
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

    private PaymentResponse sendRefund(BigDecimal amount, List<SaleItem> saleItems,
                                       String originalPoiTxnId,
                                       Instant originalPoiTimestamp) {
        return sendRefund(amount, saleItems, originalPoiTxnId, originalPoiTimestamp, null);
    }

    private PaymentResponse sendRefund(BigDecimal amount, List<SaleItem> saleItems,
                                       String originalPoiTxnId,
                                       Instant originalPoiTimestamp,
                                       String saleTransactionId) {
        AmountsReq.Builder amounts = AmountsReq.builder().currency(currency);
        if (amount != null) {
            amounts.requestedAmount(amount.doubleValue());
        }
        PaymentTransaction.Builder transaction = PaymentTransaction.builder()
                .amountsReq(amounts.build());
        if (saleItems != null && !saleItems.isEmpty()) {
            transaction.saleItem(saleItems.toArray(new SaleItem[0]));
        }
        if (originalPoiTxnId != null) {
            transaction.originalPOITransaction(Wire.originalTransaction(
                    originalPoiTxnId, originalPoiTimestamp));
        }
        SaleToPOIRequest request = SaleToPOIRequest.builder()
                .messageHeader(exchange.factory().header(
                        MessageClassType.SERVICE, MessageCategoryType.PAYMENT))
                .paymentRequest(PaymentRequest.builder()
                        .saleData(saleTransactionId == null || saleTransactionId.isEmpty()
                                ? exchange.factory().saleData()
                                : exchange.factory().saleData(saleTransactionId))
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

    /** Runs a single loyalty refund movement and returns its parsed reversal result. */
    public VoidResult refundLoyalty(ReversalStep step, String originalPoiTxnId,
                                    Instant originalPoiTimestamp, String memberId,
                                    String saleTransactionId) {
        LoyaltyTransactionTypeEnum refundType = LOYALTY_REFUND_TYPES.get(step);
        if (refundType == null || isMoneyLeg(step)) {
            throw new IllegalArgumentException(step + " is not a loyalty refund movement");
        }
        LoyaltyResponse body = loyaltyRefund(refundType, originalPoiTxnId,
                originalPoiTimestamp, memberId, saleTransactionId);
        LoyaltyReversal reversal = parseReversal(body);
        return VoidResult.builder()
                .success(true)
                .reversedAmount(sumLoyaltyAmounts(body))
                .poiTransactionId(Wire.txnId(body.getPoiData()))
                .poiTransactionTimestamp(Wire.txnTimestamp(body.getPoiData()))
                .pointsReversed(reversal.pointsReversed)
                .remainingPointBalance(reversal.remainingBalance)
                .build();
    }

    // ─── Void ───

    /**
     * Reverses the sale's movements in order, skipping the steps already
     * recorded in {@code reversedMovements} so a retried void resumes at the
     * movements still standing. Each failed step is resolved by the
     * decider — retry, leave standing, or abort. A skipped money leg is
     * honored (the remaining steps still run) but leaves the void
     * incomplete: after the loop the failure is rethrown, since a standing
     * money leg has no store-and-forward retry and a completed void would
     * strand the charge beyond a retry's reach. A step that reverses is
     * added to {@code reversedMovements}, and on abort the failure is
     * annotated with what this attempt reversed so the register can retry
     * or escalate. If nothing of the sale was ever reversed — this attempt
     * skipped every step and no prior attempt made progress — the last
     * failure is thrown rather than reporting a void that changed nothing;
     * once any movement stands reversed, an attempt that only skips the
     * remainder is a success, like a skip in a single-attempt void. The
     * returned {@link VoidResult} is built from THIS attempt's responses
     * only — a resumed void does not restate the amounts, references, or
     * receipts of movements earlier attempts reversed. With
     * nothing left standing (empty target, or every movement already
     * reversed) the void is a success.
     *
     * <p>{@code movements} must be the sale's WHOLE void target, not the
     * filtered remainder: the default policy judges "money-anchored"
     * against it, so a retry that already reversed the money leg is still
     * the reversal of a money-anchored sale and its loyalty legs stay
     * best-effort.</p>
     */
    public VoidResult voidMovements(List<ReversalMovement> movements, String memberId,
                                    StepDecider decider,
                                    Set<ReversalMovement.Key> reversedMovements) {
        StepDecider effective = decider != null ? decider : defaultPolicy(
                movements.stream().anyMatch(movement -> isMoneyLeg(movement.getStep())));
        boolean priorProgress = !reversedMovements.isEmpty();
        List<ReversalMovement> remaining = movements.stream()
                .filter(movement -> !reversedMovements.contains(movement.key()))
                .collect(Collectors.toList());
        if (remaining.isEmpty()) {
            return VoidResult.builder().success(true).build();
        }
        Map<ReversalStep, ReversalResponse> money = new EnumMap<>(ReversalStep.class);
        Map<ReversalStep, LoyaltyResponse> loyalty = new EnumMap<>(ReversalStep.class);
        List<StoredValueOperationResult> storedValueLoads = new ArrayList<>();
        List<ReversalMovement> reversed = new ArrayList<>();
        List<ReversalMovement> skippedMoneyLegs = new ArrayList<>();
        SessionException[] lastFailure = new SessionException[1];
        SessionException[] moneyFailure = new SessionException[1];

        for (ReversalMovement movement : remaining) {
            Boolean sent = runStep(movement.getStep(), effective,
                    () -> {
                        execute(movement, memberId, money, loyalty, storedValueLoads);
                        return true;
                    },
                    e -> {
                        lastFailure[0] = e;
                        if (isMoneyLeg(movement.getStep())) {
                            skippedMoneyLegs.add(movement);
                            moneyFailure[0] = e;
                        }
                        LOGGER.warning(movement.getStep() + " reversal of "
                                + movement.poiTransactionId + " skipped, the movement "
                                + "is still standing"
                                + (isMoneyLeg(movement.getStep())
                                        ? "" : " (terminal may retry loyalty via SAF)")
                                + ": " + e.getError());
                    },
                    e -> abortError(reversed, movement, e));
            if (sent != null) {
                reversed.add(movement);
                reversedMovements.add(movement.key());
            }
        }
        if (!skippedMoneyLegs.isEmpty()) {
            // the skip was honored — the remaining legs ran — but a
            // standing money leg has no store-and-forward retry, and a
            // completed void would strand the charge, so the void stays
            // incomplete and retryable
            throw standingMoneyError(skippedMoneyLegs, moneyFailure[0]);
        }
        if (reversed.isEmpty()) {
            if (!priorProgress) {
                // every movement was skipped and nothing of the sale has
                // ever been reversed: the void changed nothing, so it must
                // not report success
                throw lastFailure[0];
            }
            // this attempt only skipped loyalty legs of a partly-reversed
            // sale — the movements reversed by the prior attempt stand,
            // and the skipped ones remain SAF-retryable, exactly like a
            // skip in a single-attempt void
            return VoidResult.builder().success(true).build();
        }
        return buildVoidResult(money, loyalty, storedValueLoads);
    }

    /**
     * A void whose money leg was skipped and remains charged: the legs
     * reversed this attempt stand recorded, and the retry sends only what
     * is still standing.
     */
    private static SessionException standingMoneyError(List<ReversalMovement> skipped,
                                                       SessionException cause) {
        String legs = skipped.stream()
                .map(movement -> label(movement.getStep()) + " " + movement.poiTransactionId)
                .collect(Collectors.joining(" and "));
        return new SessionException(Wire.annotated(cause.getError(),
                legs + (skipped.size() == 1 ? " was skipped and remains" : " were skipped and remain")
                        + " charged — the void is incomplete; retry voidTransaction() to "
                        + "reverse " + (skipped.size() == 1 ? "it" : "them") + ": "
                        + cause.getError().getMessage(), cause));
    }

    private void execute(ReversalMovement movement, String memberId,
                         Map<ReversalStep, ReversalResponse> money,
                         Map<ReversalStep, LoyaltyResponse> loyalty,
                         List<StoredValueOperationResult> storedValueLoads) {
        ReversalStep step = movement.getStep();
        if (step == ReversalStep.STORED_VALUE_LOAD) {
            storedValueLoads.add(storedValueManager.operation(
                    StoredValueTransactionTypeEnum.REVERSE,
                    null, null, movement.poiTransactionId,
                    movement.poiTransactionTimestamp));
            return;
        }
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
                movement.poiTransactionId, movement.poiTransactionTimestamp, memberId, null));
    }

    private static VoidResult buildVoidResult(Map<ReversalStep, ReversalResponse> money,
                                              Map<ReversalStep, LoyaltyResponse> loyalty,
                                              List<StoredValueOperationResult> storedValueLoads) {
        ReversalResponse cardLeg = money.get(ReversalStep.CARD);
        ReversalResponse storedValueLeg = money.get(ReversalStep.STORED_VALUE);
        ReversalResponse primaryMoney = cardLeg != null ? cardLeg : storedValueLeg;
        LoyaltyResponse awardLeg = loyalty.get(ReversalStep.AWARD);
        LoyaltyReversal points = awardLeg == null ? NO_REVERSAL : parseReversal(awardLeg);

        // the reversed amount is what the money legs report; a void with no
        // money legs reports the monetary value of the loyalty refunds
        BigDecimal loyaltyAmount = sumLoyaltyAmounts(loyalty.get(ReversalStep.REDEMPTION),
                loyalty.get(ReversalStep.REBATE));
        BigDecimal reversedAmount = primaryMoney != null
                ? sumReversedAmounts(cardLeg, storedValueLeg)
                : loyaltyAmount != null && loyaltyAmount.signum() != 0 ? loyaltyAmount
                        : sumStoredValueAmounts(storedValueLoads);

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
        } else if (!loyalty.isEmpty()) {
            LoyaltyResponse primary = loyalty.values().iterator().next();
            result.poiTransactionId(Wire.txnId(primary.getPoiData()))
                    .poiTransactionTimestamp(Wire.txnTimestamp(primary.getPoiData()));
        } else if (!storedValueLoads.isEmpty()) {
            StoredValueOperationResult primary = storedValueLoads.get(0);
            result.poiTransactionId(primary.getPoiTransactionId())
                    .poiTransactionTimestamp(primary.getPoiTransactionTimestamp());
        }
        return result.build();
    }

    private static BigDecimal sumStoredValueAmounts(
            List<StoredValueOperationResult> storedValueLoads) {
        BigDecimal total = BigDecimal.ZERO;
        for (StoredValueOperationResult load : storedValueLoads) {
            if (load.getAmount() != null) {
                total = total.add(load.getAmount());
            }
        }
        return total;
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
     * anchors the reversal (the terminal can retry them via
     * store-and-forward), strict when they are its substance.
     */
    private static StepDecider defaultPolicy(boolean moneyAnchored) {
        return (step, error) -> {
            switch (step) {
                case REDEMPTION:
                case REBATE:
                case AWARD:
                    return moneyAnchored ? ReversalDecision.SKIP : ReversalDecision.ABORT;
                default:  // Stored value load, card, or stored value tender
                    return ReversalDecision.ABORT;
            }
        };
    }

    private static boolean isMoneyLeg(ReversalStep step) {
        return step == ReversalStep.STORED_VALUE_LOAD
                || step == ReversalStep.CARD || step == ReversalStep.STORED_VALUE;
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
            case STORED_VALUE_LOAD: return "the stored value load";
            case STORED_VALUE: return "the stored value leg";
            case REDEMPTION: return "the redemption";
            case REBATE: return "the rebate";
            case AWARD: return "the award";
            default: return "the card leg";
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
        return loyaltyRefund(refundType, originalPoiTxnId, originalPoiTimestamp, memberId, null);
    }

    private LoyaltyResponse loyaltyRefund(LoyaltyTransactionTypeEnum refundType,
                                          String originalPoiTxnId, Instant originalPoiTimestamp,
                                          String memberId, String saleTransactionId) {
        SaleToPOIRequest request = exchange.factory().loyaltyRefundRequest(refundType,
                Wire.originalTransaction(originalPoiTxnId, originalPoiTimestamp), memberId,
                saleTransactionId);
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
