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

import com.bilt.pos.display.DisplayPayload;
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
import com.bilt.pos.nexo.model.PaymentData;
import com.bilt.pos.nexo.model.PaymentInstrumentData;
import com.bilt.pos.nexo.model.PaymentInstrumentTypeEnum;
import com.bilt.pos.nexo.model.PaymentRequest;
import com.bilt.pos.nexo.model.PaymentResponse;
import com.bilt.pos.nexo.model.PaymentTransaction;
import com.bilt.pos.nexo.model.Rebates;
import com.bilt.pos.nexo.model.SaleData;
import com.bilt.pos.nexo.model.SaleItem;
import com.bilt.pos.nexo.model.SaleItemRebate;
import com.bilt.pos.nexo.model.SaleToPOIRequest;
import com.bilt.pos.nexo.model.SaleToPOIResponse;
import com.bilt.pos.nexo.model.StoredValueTransactionTypeEnum;
import com.bilt.pos.nexo.model.TransactionIdentificationType;
import com.bilt.pos.session.Receipt;
import com.bilt.pos.session.SessionError;
import com.bilt.pos.session.SessionErrorCode;
import com.bilt.pos.session.SessionException;
import com.bilt.pos.session.basket.Basket;
import com.bilt.pos.session.basket.BasketLineItem;
import com.bilt.pos.session.identity.IdentifyResult;
import com.bilt.pos.session.payment.EarnedReward;
import com.bilt.pos.session.payment.GiftCardPaymentResult;
import com.bilt.pos.session.payment.PointRedemptionResult;
import com.bilt.pos.session.payment.RebateRedemptionResult;
import com.bilt.pos.session.payment.RedeemedRebate;
import com.bilt.pos.session.settlement.AbandonedSettlementRecord;
import com.bilt.pos.session.settlement.CommittedStep;
import com.bilt.pos.session.settlement.ExternalPayment;
import com.bilt.pos.session.settlement.SettlementContext;
import com.bilt.pos.session.settlement.SettlementFailure;
import com.bilt.pos.session.settlement.SettlementMovement;
import com.bilt.pos.session.settlement.SettlementOptions;
import com.bilt.pos.session.settlement.SettlementRecovery;
import com.bilt.pos.session.settlement.SettlementResult;
import com.bilt.pos.session.settlement.SettlementStep;
import com.bilt.pos.session.settlement.SettlementTarget;
import com.bilt.pos.session.settlement.StoredValueLoad;
import com.bilt.pos.session.storedvalue.StoredValueOperationResult;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Runs the charge-side sequence — rebate, points, tender, stored value line
 * fulfillment, award — with commit tracking, best-effort rollback, and
 * {@code onError}-driven retry. All steps and handlers run on the calling thread.
 */
public final class PaymentOrchestrator {

    private static final Logger LOGGER = Logger.getLogger(PaymentOrchestrator.class.getName());
    /** Handlers captured from the payment flow. All may be {@code null}. */
    public static final class Handlers {
        public Function<SettlementContext, String> beforeStep;
        public Function<RebateRedemptionResult, BigDecimal> onRebatesRedeemed;
        public Function<PointRedemptionResult, BigDecimal> onPointsRedeemed;
        public Function<GiftCardPaymentResult, BigDecimal> onGiftCardPayment;
        public Consumer<SettlementMovement> onMovement;
        public Function<SettlementFailure, SettlementRecovery> onError;
    }

    /** Inputs to a payment run. */
    public static final class Request {
        public Basket basket;
        public Basket fullBasket;
        public IdentifyResult member;
        public com.bilt.pos.session.storedvalue.StoredValueCard storedValueCard;
        public SettlementOptions options;
        public Handlers handlers = new Handlers();
        public List<CommittedStep> priorSteps = List.of();
        public List<SettlementMovement> priorMovements = List.of();
        public String settlementId = UUID.randomUUID().toString();
        List<SettlementMovement> activeMovements = List.of();
        public BooleanSupplier abortRequested = () -> false;
        /** Receives the movements an incomplete unwind left standing. */
        public Consumer<List<StandingMovement>> onUnreversed = movements -> { };
        /** Transfers the partial ledger out of the session on abandon. */
        public Consumer<AbandonedSettlementRecord> onAbandoned = record -> { };
    }

    /**
     * A committed movement whose rollback failed and is still standing.
     * Carries the exact reversal action, so the session can finish the
     * unwind later ({@code voidTransaction()} on the failed session).
     */
    public static final class StandingMovement {
        private final CommittedStep info;
        private final Runnable rollback;

        StandingMovement(CommittedStep info, Runnable rollback) {
            this.info = info;
            this.rollback = rollback;
        }

        /** Re-runs the reversal; throws {@link SessionException} on failure. */
        public void reverse() {
            rollback.run();
        }

        /** Step name plus POI reference, for error messages. */
        String describe() {
            return info.getStep() + (info.getPoiTransactionId() != null
                    ? " (" + info.getPoiTransactionId() + ")" : "");
        }
    }

    /** A step failure that interrupts the sequence. */
    private static final class StepFailure extends RuntimeException {
        private static final long serialVersionUID = 1L;
        final transient SessionError error;
        final boolean aborted;
        final SettlementStep step;
        final MessageCategoryType category;
        final String serviceId;
        final SettlementFailure.OutcomeCertainty certainty;

        StepFailure(SessionError error, boolean aborted) {
            this(error, aborted, null, null, null,
                    SettlementFailure.OutcomeCertainty.DEFINITIVE);
        }

        StepFailure(SessionError error, boolean aborted, SettlementStep step,
                    MessageCategoryType category, String serviceId,
                    SettlementFailure.OutcomeCertainty certainty) {
            super(error.toString());
            this.error = error;
            this.aborted = aborted;
            this.step = step;
            this.category = category;
            this.serviceId = serviceId;
            this.certainty = certainty;
        }
    }

    /** A committed step paired with the action that reverses it. */
    private static final class Commit {
        final CommittedStep info;
        final Runnable rollback;

        Commit(CommittedStep info, Runnable rollback) {
            this.info = info;
            this.rollback = rollback;
        }
    }

    /** Retry-varying charge policy; basket obligations remain on the request. */
    private static final class AttemptOptions {
        final BigDecimal cashback;
        final DisplayPayload paymentProcessingDisplay;
        final boolean disableRebates;
        final boolean disablePoints;
        final boolean disableAward;

        AttemptOptions(SettlementOptions request) {
            this.cashback = request.getCashback();
            this.paymentProcessingDisplay = request.getPaymentProcessingDisplay();
            this.disableRebates = request.isDisableRebates();
            this.disablePoints = request.isDisablePoints();
            this.disableAward = request.isDisableAward();
        }

    }

    @FunctionalInterface
    private interface StepAttempt<T> {
        T run(String saleTransactionId, SaleToPOIResponse recoveredResponse);
    }

    private static final class StepOutcome<T> {
        final T value;
        final boolean skipped;
        final ExternalPayment externalPayment;

        private StepOutcome(T value, boolean skipped, ExternalPayment externalPayment) {
            this.value = value;
            this.skipped = skipped;
            this.externalPayment = externalPayment;
        }

        static <T> StepOutcome<T> completed(T value) {
            return new StepOutcome<>(value, false, null);
        }

        static <T> StepOutcome<T> skipped() {
            return new StepOutcome<>(null, true, null);
        }

        static <T> StepOutcome<T> external(ExternalPayment payment) {
            return new StepOutcome<>(null, false, payment);
        }
    }

    private final NexoExchange exchange;
    private final String currency;
    private final StoredValueManager storedValueManager;

    public PaymentOrchestrator(NexoExchange exchange, String currency,
                               StoredValueManager storedValueManager) {
        this.exchange = exchange;
        this.currency = currency;
        this.storedValueManager = Objects.requireNonNull(
                storedValueManager, "storedValueManager");
    }

    /** Runs the checkpointed charge-side settlement sequence. */
    public SettlementResult run(Request request) {
        AttemptOptions options = new AttemptOptions(request.options);
        List<Commit> committed = new ArrayList<>();
        List<SettlementMovement> movements = new ArrayList<>(request.priorMovements);
        request.activeMovements = movements;
        return runSequence(request, options, committed, movements);
    }

    // ─── The sequence ───

    private SettlementResult runSequence(Request request, AttemptOptions options,
                                         List<Commit> committed,
                                         List<SettlementMovement> movements) {
        Basket workingBasket = request.basket;
        BigDecimal currentTotal = workingBasket.getGrandTotal();
        boolean loyalty = request.member != null;
        boolean chargeDue = currentTotal.signum() > 0;

        SettlementResult.Builder result = SettlementResult.builder().success(true);
        List<RedeemedRebate> redeemedRebates = new ArrayList<>();
        BigDecimal rebateTotal = BigDecimal.ZERO;
        int pointsRedeemed = 0;
        BigDecimal pointsValue = BigDecimal.ZERO;
        BigDecimal storedValueCharged = BigDecimal.ZERO;
        BigDecimal cardCharged = BigDecimal.ZERO;
        BigDecimal externalPaid = BigDecimal.ZERO;
        BigDecimal storedValueLoaded = BigDecimal.ZERO;

        // 1. Rebate redemption
        if (chargeDue && loyalty && !options.disableRebates) {
            Basket stepBasket = workingBasket;
            BigDecimal stepTotal = currentTotal;
            StepOutcome<RebateOutcome> recovered = runStep(request, committed, movements,
                    SettlementStep.REBATE_REDEMPTION, stepBasket, stepTotal,
                    true, false, (saleTxnId, response) -> rebateStep(request, stepBasket,
                            stepTotal, saleTxnId, committed, movements, response));
            if (!recovered.skipped) {
                RebateOutcome outcome = recovered.value;
                redeemedRebates = outcome.rebates;
                rebateTotal = outcome.totalRebate;
                workingBasket = outcome.updatedBasket;
                if (outcome.poiTransactionId != null) {
                    result.rebatePoiTransactionId(outcome.poiTransactionId);
                    result.rebatePoiTransactionTimestamp(outcome.poiTransactionTimestamp);
                }
                currentTotal = applyHandlerTotal(request, committed, movements,
                        SettlementStep.REBATE_REDEMPTION, workingBasket,
                        request.handlers.onRebatesRedeemed, outcome.result,
                        outcome.result.getSuggestedTotal());
            }
        }

        // 2. Point / reward redemption — like the stored value and card
        // steps, skipped once rebates (or the register's handler) have
        // brought the running total to zero: there is nothing left to pay,
        // so redeeming would take the member's points for no discount
        if (loyalty && !options.disablePoints && !request.member.getRewards().isEmpty()
                && currentTotal.signum() > 0) {
            Basket stepBasket = workingBasket;
            BigDecimal stepTotal = currentTotal;
            StepOutcome<PointRedemptionResult> recovered = runStep(request, committed, movements,
                    SettlementStep.POINT_REDEMPTION, stepBasket, stepTotal,
                    true, false, (saleTxnId, response) -> pointsStep(request, stepBasket,
                            stepTotal, saleTxnId, committed, movements, result, response));
            if (!recovered.skipped) {
                PointRedemptionResult points = recovered.value;
                pointsRedeemed = points.getPointsUsed();
                pointsValue = points.getMonetaryValue();
                result.pointsBalance(points.getRemainingPointBalance());
                currentTotal = applyHandlerTotal(request, committed, movements,
                        SettlementStep.POINT_REDEMPTION, workingBasket,
                        request.handlers.onPointsRedeemed, points,
                        points.getSuggestedTotal());
            }
        }

        // 3. Stored value
        if (request.storedValueCard != null && currentTotal.signum() > 0) {
            BigDecimal stepTotal = currentTotal;
            StepOutcome<GiftCardPaymentResult> recovered = runStep(request, committed, movements,
                    SettlementStep.STORED_VALUE_CHARGE, workingBasket, stepTotal,
                    true, false, (saleTxnId, response) -> storedValueStep(request, stepTotal,
                            saleTxnId, committed, movements, result, response));
            if (!recovered.skipped) {
                GiftCardPaymentResult giftCard = recovered.value;
                storedValueCharged = giftCard.getAmountCharged();
                currentTotal = applyHandlerTotal(request, committed, movements,
                        SettlementStep.STORED_VALUE_CHARGE, workingBasket,
                        request.handlers.onGiftCardPayment, giftCard,
                        giftCard.getSuggestedTotal());
            }
        }

        // 4. Card payment
        if (currentTotal.signum() > 0) {
            if (options.paymentProcessingDisplay != null) {
                showProcessingDisplay(options.paymentProcessingDisplay);
            }
            Basket stepBasket = workingBasket;
            BigDecimal stepTotal = currentTotal;
            StepOutcome<BigDecimal> recovered = runStep(request, committed, movements,
                    SettlementStep.CARD_CHARGE, stepBasket, stepTotal,
                    false, options.cashback == null,
                    (saleTxnId, response) -> cardStep(request, stepBasket, stepTotal,
                            options, saleTxnId, committed, movements, result, response));
            if (recovered.externalPayment != null) {
                externalPaid = recovered.externalPayment.getAmount();
            } else {
                cardCharged = recovered.value;
            }
        }

        // 5. Fulfill each stored value line after its funding commits. The
        // register can retry a failed load or abort the whole charge side.
        for (StoredValueLoad fulfillment : request.options.getFulfillments()) {
            BasketLineItem line = Objects.requireNonNull(
                    request.basket.getItemByReference(fulfillment.getBasketReference()),
                    () -> "fulfillment references basket line "
                            + fulfillment.getBasketReference()
                            + " which is absent from the charge basket");
            BigDecimal amount = line.getOriginalTotal();
            StepOutcome<BigDecimal> recovered = runStep(request, committed, movements,
                    SettlementStep.STORED_VALUE_LOAD, workingBasket, amount,
                    false, false, (saleTxnId, response) -> storedValueLoadStep(request,
                            fulfillment, amount, saleTxnId, committed, movements, response));
            storedValueLoaded = storedValueLoaded.add(recovered.value);
        }

        // 6. Award wire failures are best-effort; register callback failures
        // use the same checkpoint recovery as every other step.
        if (chargeDue && loyalty && !options.disableAward) {
            Basket awardBasket = workingBasket;
            BigDecimal awardTotal = storedValueCharged.add(cardCharged).add(externalPaid);
            runStep(request, committed, movements, SettlementStep.AWARD,
                    awardBasket, awardTotal, false, false,
                    (saleTxnId, response) -> {
                        awardStep(request, awardBasket, saleTxnId,
                                committed, movements, result);
                        return Boolean.TRUE;
                    });
        }

        // An abort that landed during the award must still unwind the money
        // movements — the award's own failures are non-fatal, but an abort is
        // a rollback request, not an award failure.
        checkAbort(request, committed);

        Basket finalBasket = chargeDue
                ? withPaymentTotals(workingBasket, rebateTotal, pointsValue,
                        storedValueCharged, cardCharged, externalPaid, options.cashback)
                : workingBasket;

        // last look before success is declared; later aborts are too late
        // and the completed payment stands (void it explicitly instead)
        checkAbort(request, committed);

        // The working ledger starts with refund movements so recovery callbacks
        // see the full settlement. CheckoutSession composes that prefix onto the
        // result separately, so this builder owns only the charge-side suffix.
        List<SettlementMovement> chargeSideMovements = movements.subList(
                request.priorMovements.size(), movements.size());
        return result
                .finalBasket(finalBasket)
                .authorizedAmount(storedValueCharged.add(cardCharged))
                .storedValueAmountUsed(storedValueCharged)
                .cardAmountCharged(cardCharged)
                .externalPaymentAmount(externalPaid)
                .storedValueLoadedAmount(storedValueLoaded)
                .redeemedRebates(redeemedRebates)
                .totalRebateAmount(rebateTotal)
                .pointsRedeemed(pointsRedeemed)
                .pointsMonetaryValue(pointsValue)
                .movements(chargeSideMovements)
                .build();
    }

    /**
     * Executes one checkpointed step. Earlier commits remain standing while
     * the register decides whether to retry, skip, pay externally, abort, or
     * abandon. Only abort performs the full unwind.
     */
    private <T> StepOutcome<T> runStep(Request request, List<Commit> committed,
                                       List<SettlementMovement> movements,
                                       SettlementStep step, Basket basket,
                                       BigDecimal amountDue, boolean maySkip,
                                       boolean mayPayExternally,
                                       StepAttempt<T> attempt) {
        while (true) {
            if (request.abortRequested.getAsBoolean()) {
                throw abort(request, committed, new SessionError(SessionErrorCode.ABORTED,
                        "the payment was aborted"));
            }
            String saleTxnId;
            int commitCheckpoint = committed.size();
            int movementCheckpoint = movements.size();
            try {
                saleTxnId = beforeStep(request, step, basket, amountDue, committed);
            } catch (RuntimeException e) {
                StepFailure failure = normalizeFailure(e, step);
                StepOutcome<T> outcome = resolveFailure(request, committed, movements,
                        step, basket, amountDue, maySkip, mayPayExternally, attempt,
                        null, failure, commitCheckpoint, movementCheckpoint);
                if (outcome != null) {
                    return outcome;
                }
                continue;
            }
            try {
                return StepOutcome.completed(attempt.run(saleTxnId, null));
            } catch (RuntimeException e) {
                StepFailure failure = normalizeFailure(e, step);
                if (failure.aborted
                        || (request.abortRequested.getAsBoolean()
                                && failure.error.getCode() == SessionErrorCode.ABORTED)) {
                    throw abort(request, committed, failure.error);
                }
                StepOutcome<T> outcome = resolveFailure(request, committed, movements,
                        step, basket, amountDue, maySkip, mayPayExternally, attempt,
                        saleTxnId, failure, commitCheckpoint, movementCheckpoint);
                if (outcome != null) {
                    return outcome;
                }
            }
        }
    }

    /** Null means retry the step with a fresh wire request. */
    private <T> StepOutcome<T> resolveFailure(Request request, List<Commit> committed,
            List<SettlementMovement> movements, SettlementStep step, Basket basket,
            BigDecimal amountDue, boolean maySkip, boolean mayPayExternally,
            StepAttempt<T> attempt, String saleTxnId, StepFailure failure,
            int commitCheckpoint, int movementCheckpoint) {
        if (failure.certainty == SettlementFailure.OutcomeCertainty.INDETERMINATE
                && failure.serviceId != null && failure.category != null) {
            StepFailure originalFailure = failure;
            while (true) {
                SaleToPOIResponse recovered;
                try {
                    recovered = exchange.recoverByTransactionStatus(
                            originalFailure.serviceId, originalFailure.category);
                } catch (SessionException statusFailure) {
                    if (request.abortRequested.getAsBoolean()
                            && statusFailure.getError().getCode()
                                    == SessionErrorCode.ABORTED) {
                        throw abort(request, committed, statusFailure.getError());
                    }
                    SessionError unresolved = Wire.annotated(statusFailure.getError(),
                            "could not resolve " + step + " request "
                                    + originalFailure.serviceId + ": "
                                    + statusFailure.getError().getMessage(),
                            statusFailure);
                    StepFailure unresolvedFailure = new StepFailure(unresolved, false,
                            step, originalFailure.category, originalFailure.serviceId,
                            SettlementFailure.OutcomeCertainty.INDETERMINATE);
                    SettlementFailure context = settlementFailure(step,
                            unresolvedFailure, amountDue, movements);
                    SettlementRecovery recovery = consultRecovery(
                            request, committed, context);
                    switch (recovery.getAction()) {
                        case RETRY:
                            // The original request is still indeterminate. Retry
                            // status recovery, never the money-moving request.
                            continue;
                        case ABORT:
                            throw abort(request, committed, unresolved);
                        case ABANDON:
                            throw abandon(request, context, movements, amountDue);
                        case SKIP:
                        case EXTERNAL:
                            throw invalidRecovery(request, committed, step,
                                    recovery.getAction().name().toLowerCase()
                                            + " cannot proceed while TransactionStatus "
                                            + "has not resolved the original request");
                        default:
                            throw new AssertionError("unknown settlement recovery "
                                    + recovery.getAction());
                    }
                }

                if (recovered == null) {
                    // NOT_FOUND authoritatively establishes that the request
                    // did not commit. The register may now choose a money action.
                    failure = withCertainty(originalFailure,
                            SettlementFailure.OutcomeCertainty.DEFINITIVE);
                    break;
                }

                try {
                    // A recovered success is the step result. The register is
                    // consulted only if processing the repeated response fails.
                    return StepOutcome.completed(attempt.run(saleTxnId, recovered));
                } catch (RuntimeException e) {
                    failure = normalizeFailure(e, step);
                    if (failure.aborted
                            || (request.abortRequested.getAsBoolean()
                                    && failure.error.getCode()
                                            == SessionErrorCode.ABORTED)) {
                        throw abort(request, committed, failure.error);
                    }
                    if (failure.certainty
                            == SettlementFailure.OutcomeCertainty.INDETERMINATE) {
                        preserveStanding(request, committed);
                        throw new SessionException(failure.error);
                    }
                    break;
                }
            }
        }

        SettlementFailure context = settlementFailure(step, failure, amountDue, movements);
        SettlementRecovery recovery = consultRecovery(request, committed, context);
        if (recovery.getAction() == SettlementRecovery.Action.ABANDON) {
            throw abandon(request, context, movements, amountDue);
        }

        switch (recovery.getAction()) {
            case RETRY:
                resetFailedStep(request, committed, movements,
                        commitCheckpoint, movementCheckpoint, step);
                return null;
            case SKIP:
                if (!maySkip) {
                    throw invalidRecovery(request, committed, step,
                            "skip would leave a required basket obligation unresolved");
                }
                resetFailedStep(request, committed, movements,
                        commitCheckpoint, movementCheckpoint, step);
                return StepOutcome.skipped();
            case EXTERNAL:
                if (step != SettlementStep.CARD_CHARGE) {
                    throw invalidRecovery(request, committed, step,
                            "external payment is valid only for the final card tender");
                }
                if (!mayPayExternally) {
                    throw invalidRecovery(request, committed, step,
                            "external payment cannot replace a card tender that includes "
                                    + "cashback");
                }
                ExternalPayment payment = recovery.getExternalPayment();
                if (payment.getAmount().compareTo(amountDue) != 0) {
                    throw invalidRecovery(request, committed, step,
                            "external payment " + payment.getAmount()
                                    + " must equal the outstanding " + amountDue);
                }
                resetFailedStep(request, committed, movements,
                        commitCheckpoint, movementCheckpoint, step);
                StepOutcome<ExternalPayment> external = runStep(request, committed,
                        movements, SettlementStep.EXTERNAL_PAYMENT, basket, amountDue,
                        false, false, (externalTxnId, response) ->
                                externalPaymentStep(request, payment, externalTxnId,
                                        movements));
                return StepOutcome.external(external.value);
            case ABORT:
                throw abort(request, committed, failure.error);
            case ABANDON:
                throw new AssertionError("abandon handled before recovery action switch");
            default:
                throw new AssertionError("unknown settlement recovery "
                        + recovery.getAction());
        }
    }

    private static StepFailure withCertainty(StepFailure failure,
            SettlementFailure.OutcomeCertainty certainty) {
        return new StepFailure(failure.error, failure.aborted, failure.step,
                failure.category, failure.serviceId, certainty);
    }

    private static StepFailure normalizeFailure(RuntimeException failure,
                                                 SettlementStep step) {
        if (failure instanceof StepFailure) {
            StepFailure existing = (StepFailure) failure;
            if (existing.step != null) {
                return existing;
            }
            return new StepFailure(existing.error, existing.aborted, step,
                    existing.category, existing.serviceId, existing.certainty);
        }
        if (failure instanceof SessionException) {
            return new StepFailure(((SessionException) failure).getError(), false, step,
                    null, null, SettlementFailure.OutcomeCertainty.DEFINITIVE);
        }
        return new StepFailure(new SessionError(SessionErrorCode.UNKNOWN,
                "unexpected error during " + step + ": " + failure, null, failure),
                false, step, null, null, SettlementFailure.OutcomeCertainty.DEFINITIVE);
    }

    private static SettlementFailure settlementFailure(SettlementStep step,
            StepFailure failure, BigDecimal amountDue,
            List<SettlementMovement> movements) {
        return SettlementFailure.builder()
                .step(step)
                .error(failure.error)
                .amountDue(amountDue)
                .committedMovements(movements)
                .outcomeCertainty(failure.certainty)
                .messageCategory(failure.category == null
                        ? null : failure.category.toValue())
                .serviceId(failure.serviceId)
                .build();
    }

    private SettlementRecovery consultRecovery(Request request, List<Commit> committed,
                                                SettlementFailure failure) {
        if (request.handlers.onError == null) {
            return SettlementRecovery.abort();
        }
        try {
            SettlementRecovery recovery = request.handlers.onError.apply(failure);
            return recovery == null ? SettlementRecovery.abort() : recovery;
        } catch (RuntimeException handlerFailure) {
            throw abort(request, committed, new SessionError(SessionErrorCode.UNKNOWN,
                    "settlement onError handler threw: " + handlerFailure,
                    null, handlerFailure));
        }
    }

    private SessionException abort(Request request, List<Commit> committed,
                                   SessionError failure) {
        List<StandingMovement> unreversed = unwind(committed);
        request.onUnreversed.accept(unreversed);
        SessionError error = withRollbackFailures(failure, unreversed);
        BigDecimal externalStanding = BigDecimal.ZERO;
        for (SettlementMovement movement : request.activeMovements) {
            if (movement.getStep() == SettlementStep.EXTERNAL_PAYMENT
                    && movement.getAmount() != null) {
                externalStanding = externalStanding.add(movement.getAmount());
            }
        }
        if (externalStanding.signum() > 0) {
            error = Wire.annotated(error, error.getMessage()
                    + "; register-managed external payment " + externalStanding
                    + " remains standing", error.getCause());
        }
        return new SessionException(error);
    }

    private SessionException abandon(Request request, SettlementFailure failure,
                                     List<SettlementMovement> movements,
                                     BigDecimal amountDue) {
        AbandonedSettlementRecord record = AbandonedSettlementRecord.builder()
                .settlementId(request.settlementId)
                .abandonedAt(Instant.now())
                .basket(request.fullBasket == null ? request.basket : request.fullBasket)
                .options(request.options)
                .memberId(request.member == null ? null : request.member.getMemberId())
                .failure(failure)
                .outstandingAmount(amountDue)
                .committedMovements(movements)
                .build();
        request.onAbandoned.accept(record);
        return new SessionException(new SessionError(SessionErrorCode.ABANDONED,
                "settlement recovery was abandoned to the register"), record);
    }

    private static SessionException invalidRecovery(Request request, List<Commit> committed,
                                                     SettlementStep step, String reason) {
        preserveStanding(request, committed);
        return new SessionException(new SessionError(SessionErrorCode.INVALID_STATE,
                "invalid recovery for " + step + ": " + reason));
    }

    private static void preserveStanding(Request request, List<Commit> committed) {
        List<StandingMovement> standing = new ArrayList<>();
        for (Commit commit : committed) {
            if (commit.rollback != null) {
                standing.add(new StandingMovement(commit.info, commit.rollback));
            }
        }
        request.onUnreversed.accept(standing);
    }

    /** Reverses only a partially committed failed step before retry/skip/substitution. */
    private void resetFailedStep(Request request, List<Commit> committed,
            List<SettlementMovement> movements, int commitCheckpoint,
            int movementCheckpoint, SettlementStep step) {
        List<StandingMovement> unreversed = new ArrayList<>();
        for (int i = committed.size() - 1; i >= commitCheckpoint; i--) {
            Commit commit = committed.get(i);
            if (commit.rollback != null) {
                try {
                    commit.rollback.run();
                } catch (RuntimeException e) {
                    LOGGER.log(Level.WARNING, "rollback of partially committed "
                            + step + " failed", e);
                    unreversed.add(new StandingMovement(commit.info, commit.rollback));
                    continue;
                }
            }
            committed.remove(i);
        }
        if (!unreversed.isEmpty()) {
            preserveStanding(request, committed);
            throw new SessionException(new SessionError(SessionErrorCode.TERMINAL_ERROR,
                    "could not reset the partially committed " + step
                            + " before continuing; manual reconciliation is required"));
        }
        if (movements.size() > movementCheckpoint) {
            movements.subList(movementCheckpoint, movements.size()).clear();
        }
    }

    // ─── Steps ───

    private static final class RebateOutcome {
        List<RedeemedRebate> rebates;
        BigDecimal totalRebate;
        Basket updatedBasket;
        RebateRedemptionResult result;
        String poiTransactionId;
        Instant poiTransactionTimestamp;
    }

    private RebateOutcome rebateStep(Request request, Basket basket, BigDecimal currentTotal,
                                     String saleTxnId, List<Commit> committed,
                                     List<SettlementMovement> movements,
                                     SaleToPOIResponse recoveredResponse) {
        LoyaltyResponse body = sendLoyalty(SettlementStep.REBATE_REDEMPTION,
                LoyaltyTransactionTypeEnum.REBATE, request, basket,
                saleTxnId, null, recoveredResponse);

        List<RedeemedRebate> rebates = new ArrayList<>();
        BigDecimal totalRebate = BigDecimal.ZERO;
        LoyaltyResult firstRebateResult = Wire.firstLoyaltyResult(body);
        Rebates wireRebates = firstRebateResult == null ? null : firstRebateResult.getRebates();
        if (wireRebates != null) {
            if (wireRebates.getSaleItemRebate() != null) {
                for (SaleItemRebate itemRebate : wireRebates.getSaleItemRebate()) {
                    String itemId = String.valueOf(itemRebate.getItemID());
                    BigDecimal amount = itemRebate.getItemAmount() == null
                            ? BigDecimal.ZERO : Wire.money(itemRebate.getItemAmount());
                    rebates.add(new RedeemedRebate(itemId, itemRebate.getProductCode(), amount,
                            itemRebate.getRebateLabel() != null
                                    ? itemRebate.getRebateLabel() : wireRebates.getRebateLabel(),
                            null));
                }
            }
            BigDecimal attributed = rebates.stream().map(RedeemedRebate::getAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            totalRebate = wireRebates.getTotalRebate() != null
                    ? Wire.money(wireRebates.getTotalRebate())
                    : attributed;
            // a TotalRebate exceeding the per-item entries is a cart-level
            // discount (e.g. "$10 off purchase"); surface it as the
            // documented cart-level RedeemedRebate (null itemId/sku) so the
            // register sees it and applyRebates can prorate it onto lines
            BigDecimal unattributed = totalRebate.subtract(attributed);
            if (unattributed.signum() > 0) {
                rebates.add(new RedeemedRebate(null, null, unattributed,
                        wireRebates.getRebateLabel(), null));
            }
        }

        commit(committed, SettlementStep.REBATE_REDEMPTION, saleTxnId, body.getPoiData(),
                totalRebate.signum() > 0
                        ? loyaltyRollback(LoyaltyTransactionTypeEnum.REBATE_REFUND,
                                Wire.poiRef(body.getPoiData()), request.member.getMemberId())
                        : null);
        publishMovement(request, movements, movement(SettlementStep.REBATE_REDEMPTION,
                totalRebate, saleTxnId, body.getPoiData(), request.member.getMemberId(),
                null, null));
        RebateOutcome outcome = new RebateOutcome();
        outcome.rebates = rebates;
        outcome.totalRebate = totalRebate;
        outcome.updatedBasket = applyRebates(request.basket, rebates, totalRebate);
        outcome.result = new RebateRedemptionResult(rebates, totalRebate, currentTotal,
                currentTotal.subtract(totalRebate), outcome.updatedBasket);
        // Return references only after every failure-prone part of the step
        // has completed. runSequence publishes them only for an accepted
        // outcome; a retry or skip can reverse this attempt.
        TransactionIdentificationType rebatePoiTxn = Wire.poiRef(body.getPoiData());
        if (totalRebate.signum() > 0 && rebatePoiTxn != null) {
            outcome.poiTransactionId = rebatePoiTxn.getTransactionID();
            outcome.poiTransactionTimestamp = Wire.instant(rebatePoiTxn.getTimeStamp());
        }
        return outcome;
    }

    private PointRedemptionResult pointsStep(Request request, Basket basket,
                                             BigDecimal currentTotal, String saleTxnId,
                                             List<Commit> committed,
                                             List<SettlementMovement> movements,
                                             SettlementResult.Builder result,
                                             SaleToPOIResponse recoveredResponse) {
        String rewardRefsPayload = LoyaltyPayloadCodec.memberRewardRefs(
                request.member.getRewards());
        LoyaltyResponse body = sendLoyalty(SettlementStep.POINT_REDEMPTION,
                LoyaltyTransactionTypeEnum.REDEMPTION, request, basket,
                saleTxnId, rewardRefsPayload, recoveredResponse);

        LoyaltyResult first = Wire.firstLoyaltyResult(body);
        BigDecimal monetaryValue = BigDecimal.ZERO;
        int pointsUsed = 0;
        int balance = 0;
        if (first != null) {
            LoyaltyAmount amount = first.getLoyaltyAmount();
            if (amount != null) {
                if (amount.getLoyaltyUnit() == LoyaltyUnitEnum.POINT) {
                    // a point count is not currency: never subtract it from
                    // the running total as dollars. With no monetary value
                    // reported, the discount stays zero — the safe outcome.
                    pointsUsed = (int) Math.round(amount.getAmountValue());
                } else {
                    // Monetary per the redemption contract (also the default
                    // when the unit is omitted)
                    monetaryValue = Wire.money(amount.getAmountValue());
                }
            }
            balance = first.getCurrentBalance() == null
                    ? 0 : (int) Math.round(first.getCurrentBalance());
        }
        pointsUsed = pointsUsed == 0 && monetaryValue.signum() > 0
                ? LoyaltyPayloadCodec.parseIntField(
                        body.getResponse().getAdditionalResponse(), "pointsUsed", 0)
                : pointsUsed;

        commit(committed, SettlementStep.POINT_REDEMPTION, saleTxnId, body.getPoiData(),
                monetaryValue.signum() > 0
                        ? loyaltyRollback(LoyaltyTransactionTypeEnum.REDEMPTION_REFUND,
                                Wire.poiRef(body.getPoiData()), request.member.getMemberId())
                        : null);
        publishMovement(request, movements, movement(SettlementStep.POINT_REDEMPTION,
                monetaryValue, saleTxnId, body.getPoiData(), request.member.getMemberId(),
                pointsUsed, balance));
        // kept on the result so a checkout with no payment legs (rewards
        // covered everything) can still be voided by reversing this movement
        TransactionIdentificationType redemptionPoiTxn = Wire.poiRef(body.getPoiData());
        if (monetaryValue.signum() > 0 && redemptionPoiTxn != null) {
            result.redemptionPoiTransactionId(redemptionPoiTxn.getTransactionID());
            result.redemptionPoiTransactionTimestamp(
                    Wire.instant(redemptionPoiTxn.getTimeStamp()));
        }

        return new PointRedemptionResult(pointsUsed, monetaryValue, currentTotal,
                currentTotal.subtract(monetaryValue), balance);
    }

    private GiftCardPaymentResult storedValueStep(Request request, BigDecimal currentTotal,
                                                  String saleTxnId, List<Commit> committed,
                                                  List<SettlementMovement> movements,
                                                  SettlementResult.Builder result,
                                                  SaleToPOIResponse recoveredResponse) {
        SaleToPOIRequest wireRequest = SaleToPOIRequest.builder()
                .messageHeader(exchange.factory().header(
                        MessageClassType.SERVICE, MessageCategoryType.PAYMENT))
                .paymentRequest(PaymentRequest.builder()
                        .saleData(exchange.factory().saleData(saleTxnId))
                        .paymentTransaction(PaymentTransaction.builder()
                                .amountsReq(AmountsReq.builder()
                                        .currency(currency)
                                        .requestedAmount(currentTotal.doubleValue())
                                        .build())
                                .build())
                        .paymentData(PaymentData.builder()
                                .paymentInstrumentData(PaymentInstrumentData.builder()
                                        .paymentInstrumentType(PaymentInstrumentTypeEnum.STORED_VALUE)
                                        .storedValueAccountID(StoredValueManager.accountId(
                                                request.storedValueCard))
                                        .build())
                                .build())
                        .build())
                .build();

        PaymentResponse body = sendPayment(SettlementStep.STORED_VALUE_CHARGE, wireRequest,
                currentTotal, recoveredResponse);
        BigDecimal charged = body.getPaymentResult() != null
                && body.getPaymentResult().getAmountsResp() != null
                ? Wire.money(body.getPaymentResult().getAmountsResp().getAuthorizedAmount())
                : currentTotal;
        BigDecimal remainingBalance = parseCardBalance(
                body.getResponse().getAdditionalResponse());

        commit(committed, SettlementStep.STORED_VALUE_CHARGE, saleTxnId, body.getPoiData(),
                reversalRollback(Wire.poiRef(body.getPoiData())));
        publishMovement(request, movements, movement(SettlementStep.STORED_VALUE_CHARGE,
                charged, saleTxnId, body.getPoiData(), null, null, null));

        // a gift-card-only checkout has no card step: this payment's
        // references/receipts must reach the result so the session can void
        // or refund it (the card step overwrites them when it runs)
        copyPaymentArtifacts(body, result);
        // the split-tender leg keeps its own reference so a session void can
        // reverse BOTH legs, not just the card payment
        TransactionIdentificationType storedValuePoiTxn = Wire.poiRef(body.getPoiData());
        if (storedValuePoiTxn != null) {
            result.storedValuePoiTransactionId(storedValuePoiTxn.getTransactionID());
            result.storedValuePoiTransactionTimestamp(
                    Wire.instant(storedValuePoiTxn.getTimeStamp()));
        }

        return new GiftCardPaymentResult(charged, remainingBalance, currentTotal,
                currentTotal.subtract(charged));
    }

    private BigDecimal storedValueLoadStep(Request request, StoredValueLoad fulfillment,
            BigDecimal amount, String saleTxnId, List<Commit> committed,
            List<SettlementMovement> movements, SaleToPOIResponse recoveredResponse) {
        StoredValueTransactionTypeEnum type = fulfillment.getType() == StoredValueLoad.Type.ACTIVATE
                ? StoredValueTransactionTypeEnum.ACTIVATE : StoredValueTransactionTypeEnum.LOAD;
        SaleToPOIRequest wireRequest = storedValueManager.operationRequest(type,
                fulfillment.getCard(), amount, null, null, saleTxnId);
        StoredValueOperationResult loaded;
        boolean responseReceived = recoveredResponse != null;
        try {
            SaleToPOIResponse response;
            if (recoveredResponse != null) {
                response = recoveredResponse;
            } else {
                response = exchange.sendExpectingBody(
                        MessageCategoryType.STORED_VALUE, wireRequest);
                responseReceived = true;
            }
            loaded = storedValueManager.operationResult(type, amount, response);
        } catch (SessionException e) {
            throw wireFailure(SettlementStep.STORED_VALUE_LOAD,
                    MessageCategoryType.STORED_VALUE, wireRequest, e.getError(),
                    responseReceived);
        }
        BigDecimal actualAmount = loaded.getAmount() == null ? amount : loaded.getAmount();
        Runnable rollback = loaded.getPoiTransactionId() == null ? null : () ->
                storedValueManager.operation(StoredValueTransactionTypeEnum.REVERSE,
                        null, null, loaded.getPoiTransactionId(),
                        loaded.getPoiTransactionTimestamp());
        commit(committed, SettlementStep.STORED_VALUE_LOAD, saleTxnId,
                loaded.getPoiTransactionId(), loaded.getPoiTransactionTimestamp(), rollback);
        publishMovement(request, movements, movement(SettlementStep.STORED_VALUE_LOAD,
                fulfillment.getTarget(), actualAmount, saleTxnId,
                loaded.getPoiTransactionId(), loaded.getPoiTransactionTimestamp(),
                null, null, null));
        if (actualAmount.compareTo(amount) != 0) {
            throw new StepFailure(new SessionError(SessionErrorCode.DECLINED,
                    "the stored value fulfillment loaded " + actualAmount
                            + " instead of the basket line's " + amount), false);
        }
        return actualAmount;
    }

    private BigDecimal cardStep(Request request, Basket basket, BigDecimal currentTotal,
                                AttemptOptions options, String saleTxnId,
                                List<Commit> committed, List<SettlementMovement> movements,
                                SettlementResult.Builder result,
                                SaleToPOIResponse recoveredResponse) {
        // nexo contract: RequestedAmount is the TOTAL requested for payment
        // INCLUDING cashback — the terminal authorizes sale + cashback as
        // one amount, with CashBackAmount identifying the cash portion
        BigDecimal grossTotal = options.cashback != null
                ? currentTotal.add(options.cashback) : currentTotal;
        AmountsReq.Builder amounts = AmountsReq.builder()
                .currency(currency)
                .requestedAmount(grossTotal.doubleValue());
        if (options.cashback != null) {
            amounts.cashBackAmount(options.cashback.doubleValue());
        }
        SaleToPOIRequest wireRequest = SaleToPOIRequest.builder()
                .messageHeader(exchange.factory().header(
                        MessageClassType.SERVICE, MessageCategoryType.PAYMENT))
                .paymentRequest(PaymentRequest.builder()
                        .saleData(exchange.factory().saleData(saleTxnId))
                        .paymentTransaction(PaymentTransaction.builder()
                                .amountsReq(amounts.build())
                                .saleItem(SaleItemMapper.toAdjustedSaleItems(basket)
                                        .toArray(new SaleItem[0]))
                                .build())
                        .build())
                .build();

        PaymentResponse body = sendPayment(SettlementStep.CARD_CHARGE, wireRequest,
                grossTotal, recoveredResponse);
        BigDecimal charged = body.getPaymentResult() != null
                && body.getPaymentResult().getAmountsResp() != null
                ? Wire.money(body.getPaymentResult().getAmountsResp().getAuthorizedAmount())
                : grossTotal;

        // Commit before validating so recovery sees the partial movement.
        // Retry and abort can reverse it; abandon deliberately leaves it standing.
        commit(committed, SettlementStep.CARD_CHARGE, saleTxnId, body.getPoiData(),
                reversalRollback(Wire.poiRef(body.getPoiData())));
        publishMovement(request, movements, movement(SettlementStep.CARD_CHARGE,
                charged, saleTxnId, body.getPoiData(), null, null, null));

        // a partial authorization on the stored value step is the split
        // tender mechanism, but the card step is the FINAL tender — an
        // under-authorization here would complete a short-paid sale (or
        // hand out cashback the sale never collected)
        if (charged.compareTo(grossTotal) < 0) {
            throw new StepFailure(new SessionError(SessionErrorCode.DECLINED,
                    "the card payment authorized only " + charged + " of the requested "
                            + grossTotal), false);
        }

        copyPaymentArtifacts(body, result);
        return charged;
    }

    /**
     * Copies transaction references, acquirer data, and receipts from a
     * payment response onto the checkout result. Both the stored value and
     * card steps are payments; the card step runs last, so in a split tender
     * its (non-null) values take precedence.
     */
    private static void copyPaymentArtifacts(PaymentResponse body, SettlementResult.Builder result) {
        if (body.getPaymentResult() != null
                && body.getPaymentResult().getPaymentAcquirerData() != null) {
            if (body.getPaymentResult().getPaymentAcquirerData().getApprovalCode() != null) {
                result.approvalCode(body.getPaymentResult()
                        .getPaymentAcquirerData().getApprovalCode());
            }
            if (body.getPaymentResult().getPaymentAcquirerData()
                    .getAcquirerTransactionID() != null) {
                result.acquirerTransactionId(body.getPaymentResult().getPaymentAcquirerData()
                        .getAcquirerTransactionID().getTransactionID());
            }
        }
        if (body.getPaymentResult() != null
                && body.getPaymentResult().getPaymentInstrumentData() != null
                && body.getPaymentResult().getPaymentInstrumentData().getCardData() != null
                && body.getPaymentResult().getPaymentInstrumentData()
                        .getCardData().getPaymentBrand() != null) {
            result.paymentBrand(body.getPaymentResult().getPaymentInstrumentData()
                    .getCardData().getPaymentBrand());
        }
        Receipt customerReceipt = ReceiptMapper.customerReceipt(body.getPaymentReceipt());
        if (customerReceipt != null) {
            result.customerReceipt(customerReceipt);
        }
        Receipt merchantReceipt = ReceiptMapper.merchantReceipt(body.getPaymentReceipt());
        if (merchantReceipt != null) {
            result.merchantReceipt(merchantReceipt);
        }
        TransactionIdentificationType poiTxn = Wire.poiRef(body.getPoiData());
        if (poiTxn != null) {
            result.poiTransactionId(poiTxn.getTransactionID());
            result.poiTransactionTimestamp(Wire.instant(poiTxn.getTimeStamp()));
        }
    }

    private void awardStep(Request request, Basket basket,
                           String saleTxnId, List<Commit> committed,
                           List<SettlementMovement> movements,
                           SettlementResult.Builder result) {
        LoyaltyResponse body;
        try {
            body = sendLoyalty(SettlementStep.AWARD,
                    LoyaltyTransactionTypeEnum.AWARD, request, basket,
                    saleTxnId, null, null);
        } catch (SessionException | StepFailure e) {
            // decision: a failed award never reverses a completed payment.
            // Only the wire exchange is inside this catch. Register handlers
            // run below and escape to runStep recovery if they throw.
            String detail = e instanceof SessionException
                    ? ((SessionException) e).getError().toString() : e.getMessage();
            LOGGER.log(Level.WARNING, "loyalty award failed (terminal may retry via SAF): "
                    + detail, e);
            result.warning("loyalty award failed; points may be credited later: " + detail);
            return;
        }

        // an abort observed after this point unwinds the tender — the
        // credited points must be reversed with it, like void does
        TransactionIdentificationType awardPoiTxn = Wire.poiRef(body.getPoiData());
        commit(committed, SettlementStep.AWARD, saleTxnId, body.getPoiData(),
                awardPoiTxn != null
                        ? loyaltyRollback(LoyaltyTransactionTypeEnum.AWARD_REFUND,
                                awardPoiTxn, request.member.getMemberId())
                        : null);
        LoyaltyResult first = Wire.firstLoyaltyResult(body);
        Integer pointsEarned = first != null && first.getLoyaltyAmount() != null
                ? (int) Math.round(first.getLoyaltyAmount().getAmountValue()) : null;
        Integer pointsBalance = first != null && first.getCurrentBalance() != null
                ? (int) Math.round(first.getCurrentBalance()) : null;
        List<String> promotionMessages = parsePromotionMessages(
                body.getResponse().getAdditionalResponse());
        List<EarnedReward> earnedRewards = LoyaltyPayloadCodec.parseEarnedRewards(
                body.getResponse().getAdditionalResponse());
        publishMovement(request, movements, movement(SettlementStep.AWARD,
                BigDecimal.ZERO, saleTxnId, body.getPoiData(), request.member.getMemberId(),
                pointsEarned, pointsBalance));

        // Apply result fields only after register movement handlers return.
        // A handler failure can then reset and retry this step without stale
        // artifacts from the reversed attempt surviving in the result.
        if (awardPoiTxn != null) {
            result.awardPoiTransactionId(awardPoiTxn.getTransactionID());
            result.awardPoiTransactionTimestamp(Wire.instant(awardPoiTxn.getTimeStamp()));
        }
        if (pointsEarned != null) {
            result.totalPointsEarned(pointsEarned);
        }
        if (pointsBalance != null) {
            result.pointsBalance(pointsBalance);
        }
        result.promotionMessages(promotionMessages);
        result.earnedRewards(earnedRewards);
    }

    private ExternalPayment externalPaymentStep(Request request, ExternalPayment payment,
            String saleTransactionId, List<SettlementMovement> movements) {
        SettlementMovement movement = SettlementMovement.builder()
                .step(SettlementStep.EXTERNAL_PAYMENT)
                .target(SettlementTarget.sales())
                .amount(payment.getAmount())
                .saleTransactionId(saleTransactionId)
                .externalTenderType(payment.getTenderType())
                .externalReference(payment.getReference())
                .build();
        publishMovement(request, movements, movement);
        return payment;
    }

    // ─── Wire plumbing ───

    private LoyaltyResponse sendLoyalty(SettlementStep step,
                                        LoyaltyTransactionTypeEnum type, Request request,
                                        Basket basket, String saleTxnId,
                                        String saleToPoiData,
                                        SaleToPOIResponse recoveredResponse) {
        SaleData saleData = exchange.factory().saleData(saleTxnId);
        if (saleToPoiData != null) {
            saleData.setSaleToPOIData(saleToPoiData);
        }
        List<SaleItem> saleItems = SaleItemMapper.toAdjustedSaleItems(basket);
        // contract: TotalAmount must equal the sum of SaleItem[].ItemAmount —
        // derive it from the items sent rather than the tax-inclusive running
        // total, which diverges once tax or point discounts apply
        BigDecimal itemSum = BigDecimal.ZERO;
        for (BasketLineItem line : basket.getItems()) {
            itemSum = itemSum.add(line.getAdjustedTotal());
        }
        LoyaltyData.Builder loyaltyData = LoyaltyData.builder()
                .loyaltyAccountID(Wire.memberAccount(request.member.getMemberId()));
        if (type == LoyaltyTransactionTypeEnum.REDEMPTION) {
            // the redemption contract requires a Monetary LoyaltyAmount of
            // 0.00 — the provider determines the actual discount from the
            // rewardRefs carried in SaleToPOIData
            loyaltyData.loyaltyAmount(LoyaltyAmount.builder()
                    .loyaltyUnit(LoyaltyUnitEnum.MONETARY)
                    .currency(currency)
                    .amountValue(0.0)
                    .build());
        }
        LoyaltyRequest.Builder loyaltyRequest = LoyaltyRequest.builder()
                .saleData(saleData)
                .loyaltyTransaction(LoyaltyTransaction.builder()
                        .loyaltyTransactionType(type)
                        .currency(currency)
                        .totalAmount(itemSum.doubleValue())
                        .saleItem(saleItems.toArray(new SaleItem[0]))
                        .build())
                .loyaltyData(new LoyaltyData[] {loyaltyData.build()});

        SaleToPOIRequest wireRequest = SaleToPOIRequest.builder()
                .messageHeader(exchange.factory().header(
                        MessageClassType.SERVICE, MessageCategoryType.LOYALTY))
                .loyaltyRequest(loyaltyRequest.build())
                .build();
        boolean responseReceived = recoveredResponse != null;
        try {
            SaleToPOIResponse response;
            if (recoveredResponse != null) {
                response = recoveredResponse;
            } else {
                response = exchange.sendExpectingBody(
                        MessageCategoryType.LOYALTY, wireRequest);
                responseReceived = true;
            }
            LoyaltyResponse body = response.getLoyaltyResponse();
            if (body == null) {
                throw Wire.missing("LoyaltyResponse");
            }
            exchange.requireSuccess(MessageCategoryType.LOYALTY, body.getResponse());
            return body;
        } catch (SessionException e) {
            throw wireFailure(step, MessageCategoryType.LOYALTY, wireRequest,
                    e.getError(), responseReceived);
        }
    }

    private PaymentResponse sendPayment(SettlementStep step, SaleToPOIRequest wireRequest,
                                        BigDecimal requestedAmount,
                                        SaleToPOIResponse recoveredResponse) {
        PaymentResponse body;
        boolean responseReceived = recoveredResponse != null;
        try {
            SaleToPOIResponse response;
            if (recoveredResponse != null) {
                response = recoveredResponse;
            } else {
                response = exchange.sendExpectingBody(
                        MessageCategoryType.PAYMENT, wireRequest);
                responseReceived = true;
            }
            body = response.getPaymentResponse();
            if (body == null) {
                throw Wire.missing("PaymentResponse");
            }
        } catch (SessionException e) {
            throw wireFailure(step, MessageCategoryType.PAYMENT, wireRequest,
                    e.getError(), responseReceived);
        }
        try {
            exchange.requireSuccess(MessageCategoryType.PAYMENT, body.getResponse());
            return body;
        } catch (SessionException e) {
            SessionError error = e.getError();
            // insufficiency normally surfaces as a Partial authorization (the
            // split-tender flow); a declined stored value payment is only
            // relabelled when the response affirmatively reports a balance
            // below the requested amount — hard declines (expired, blocked,
            // invalid cards) stay DECLINED
            if (step == SettlementStep.STORED_VALUE_CHARGE
                    && error.getCode() == SessionErrorCode.DECLINED) {
                BigDecimal balance = parseCardBalance(
                        body.getResponse().getAdditionalResponse());
                if (balance != null && balance.compareTo(requestedAmount) < 0) {
                    error = new SessionError(SessionErrorCode.STORED_VALUE_INSUFFICIENT,
                            "the stored value card balance (" + balance
                                    + ") does not cover the requested " + requestedAmount,
                            error.getNexoErrorCondition(), error.getCause());
                }
            }
            throw wireFailure(step, MessageCategoryType.PAYMENT, wireRequest,
                    error, true);
        }
    }

    private static StepFailure wireFailure(SettlementStep step,
            MessageCategoryType category, SaleToPOIRequest request,
            SessionError error, boolean responseReceived) {
        boolean indeterminate = !responseReceived
                && (error.getCode() == SessionErrorCode.NETWORK
                        || error.getCode() == SessionErrorCode.TIMEOUT
                        || error.getCode() == SessionErrorCode.TERMINAL_ERROR);
        String serviceId = request.getMessageHeader() == null
                ? null : request.getMessageHeader().getServiceID();
        return new StepFailure(error, false, step, category, serviceId,
                indeterminate ? SettlementFailure.OutcomeCertainty.INDETERMINATE
                        : SettlementFailure.OutcomeCertainty.DEFINITIVE);
    }

    private String beforeStep(Request request, SettlementStep step, Basket basket,
                              BigDecimal currentTotal, List<Commit> committed) {
        List<CommittedStep> prior = new ArrayList<>();
        prior.addAll(request.priorSteps);
        for (Commit commit : committed) {
            prior.add(commit.info);
        }
        return SettlementContext.resolveSaleTransactionId(step, basket, currentTotal,
                prior, request.handlers.beforeStep);
    }

    private <R> BigDecimal applyHandlerTotal(Request request, List<Commit> committed,
            List<SettlementMovement> movements, SettlementStep step, Basket basket,
            Function<R, BigDecimal> handler, R stepResult, BigDecimal suggested) {
        if (handler == null) {
            return suggested;
        }
        while (true) {
            try {
                BigDecimal total = handler.apply(stepResult);
                if (total == null || total.signum() < 0) {
                    throw new IllegalArgumentException(
                            "payment step handler returned an invalid total: " + total);
                }
                return total;
            } catch (RuntimeException callbackFailure) {
                SessionError error = new SessionError(SessionErrorCode.UNKNOWN,
                        "unexpected error in " + step + " handler: " + callbackFailure,
                        null, callbackFailure);
                SettlementFailure context = SettlementFailure.builder()
                        .step(step)
                        .error(error)
                        .amountDue(suggested)
                        .committedMovements(movements)
                        .outcomeCertainty(
                                SettlementFailure.OutcomeCertainty.DEFINITIVE)
                        .build();
                SettlementRecovery recovery = consultRecovery(request, committed, context);
                if (recovery == null || recovery.getAction() == SettlementRecovery.Action.ABORT) {
                    throw abort(request, committed, error);
                }
                if (recovery.getAction() == SettlementRecovery.Action.ABANDON) {
                    throw abandon(request, context, movements, suggested);
                }
                if (recovery.getAction() == SettlementRecovery.Action.RETRY) {
                    continue;
                }
                throw invalidRecovery(request, committed, step,
                        "a committed step handler can only be retried, aborted, or abandoned");
            }
        }
    }

    private void checkAbort(Request request, List<Commit> committed) {
        if (request.abortRequested.getAsBoolean()) {
            throw abort(request, committed, new SessionError(SessionErrorCode.ABORTED,
                    "the payment was aborted"));
        }
    }

    private void commit(List<Commit> committed, SettlementStep step, String saleTxnId,
                        com.bilt.pos.nexo.model.POIData poiData, Runnable rollback) {
        TransactionIdentificationType poiTxn = poiData == null
                ? null : poiData.getPoiTransactionID();
        commit(committed, step, saleTxnId,
                poiTxn == null ? null : poiTxn.getTransactionID(),
                poiTxn == null ? null : Wire.instant(poiTxn.getTimeStamp()), rollback);
    }

    private void commit(List<Commit> committed, SettlementStep step, String saleTxnId,
                        String poiTransactionId, Instant poiTransactionTimestamp,
                        Runnable rollback) {
        committed.add(new Commit(
                new CommittedStep(step, saleTxnId,
                        poiTransactionId, poiTransactionTimestamp,
                        true),
                rollback));
    }

    private static SettlementMovement movement(SettlementStep step, BigDecimal amount,
            String saleTxnId, com.bilt.pos.nexo.model.POIData poiData, String memberId,
            Integer points, Integer pointBalance) {
        TransactionIdentificationType poiTxn = poiData == null
                ? null : poiData.getPoiTransactionID();
        return movement(step, SettlementTarget.sales(), amount, saleTxnId,
                poiTxn == null ? null : poiTxn.getTransactionID(),
                poiTxn == null ? null : Wire.instant(poiTxn.getTimeStamp()), memberId,
                points, pointBalance);
    }

    private static SettlementMovement movement(SettlementStep step, SettlementTarget target,
            BigDecimal amount, String saleTxnId, String poiTransactionId,
            Instant poiTransactionTimestamp, String memberId, Integer points,
            Integer pointBalance) {
        return SettlementMovement.builder()
                .step(step)
                .target(target)
                .amount(amount)
                .saleTransactionId(saleTxnId)
                .poiTransactionId(poiTransactionId)
                .poiTransactionTimestamp(poiTransactionTimestamp)
                .memberId(memberId)
                .points(points)
                .pointBalance(pointBalance)
                .build();
    }

    private static void publishMovement(Request request,
                                        List<SettlementMovement> movements,
                                        SettlementMovement movement) {
        movements.add(movement);
        if (request.handlers.onMovement != null) {
            request.handlers.onMovement.accept(movement);
        }
    }

    // ─── Rollback ───

    /**
     * Reverses the committed steps in reverse order, best-effort — one leg
     * failing to reverse never stops the others. Returns every movement
     * that could NOT be reversed and is still standing, with its reversal
     * action; the caller must surface those and must not retry over them.
     */
    private List<StandingMovement> unwind(List<Commit> committed) {
        List<StandingMovement> unreversed = new ArrayList<>();
        for (int i = committed.size() - 1; i >= 0; i--) {
            Commit commit = committed.get(i);
            if (commit.rollback == null) {
                continue;
            }
            try {
                commit.rollback.run();
            } catch (RuntimeException e) {
                LOGGER.log(Level.WARNING, "rollback of " + commit.info.getStep()
                        + " failed; manual reconciliation may be required", e);
                unreversed.add(new StandingMovement(commit.info, commit.rollback));
            }
        }
        return unreversed;
    }

    /**
     * The step failure, annotated with the movements the unwind could not
     * reverse so the register sees the full reconciliation picture.
     */
    private static SessionError withRollbackFailures(SessionError error,
                                                     List<StandingMovement> unreversed) {
        if (unreversed.isEmpty()) {
            return error;
        }
        String standing = unreversed.stream()
                .map(StandingMovement::describe)
                .collect(Collectors.joining(", "));
        return Wire.annotated(error,
                error.getMessage() + "; rollback incomplete — " + standing
                        + (unreversed.size() == 1 ? " is" : " are")
                        + " still standing and may require manual reconciliation",
                error.getCause());
    }

    private Runnable loyaltyRollback(LoyaltyTransactionTypeEnum refundType,
                                     TransactionIdentificationType originalPoiTxn,
                                     String memberId) {
        return () -> {
            SaleToPOIRequest wireRequest = exchange.factory().loyaltyRefundRequest(
                    refundType, Wire.originalTransaction(originalPoiTxn), memberId);
            SaleToPOIResponse response = exchange.sendExpectingBody(
                    MessageCategoryType.LOYALTY, wireRequest);
            if (response.getLoyaltyResponse() != null) {
                exchange.requireSuccess(MessageCategoryType.LOYALTY,
                        response.getLoyaltyResponse().getResponse());
            }
        };
    }

    private Runnable reversalRollback(TransactionIdentificationType originalPoiTxn) {
        return () -> {
            SaleToPOIRequest wireRequest = exchange.factory().reversalRequest(
                    Wire.originalTransaction(originalPoiTxn));
            SaleToPOIResponse response = exchange.sendExpectingBody(
                    MessageCategoryType.REVERSAL, wireRequest);
            if (response.getReversalResponse() != null) {
                exchange.requireSuccess(MessageCategoryType.REVERSAL,
                        response.getReversalResponse().getResponse());
            }
        };
    }

    // ─── Helpers ───

    /** Best-effort processing screen while the cardholder pays. */
    private void showProcessingDisplay(com.bilt.pos.display.DisplayPayload payload) {
        try {
            exchange.send(MessageCategoryType.DISPLAY, exchange.factory().displayRequest(
                    com.bilt.pos.display.DisplayPayloadHelper.toBase64(payload)));
        } catch (jakarta.xml.bind.JAXBException | RuntimeException e) {
            LOGGER.log(Level.WARNING, "payment processing display failed", e);
        }
    }

    private static Basket applyRebates(Basket basket, List<RedeemedRebate> rebates,
                                       BigDecimal totalRebate) {
        // cart-level rebates (null itemId) are prorated across lines by
        // weight so the adjusted sale items sent to the card and award steps
        // reflect the discount; the last weighted line absorbs the rounding
        // remainder so the shares sum exactly to the cart-level amount
        BigDecimal cartLevel = BigDecimal.ZERO;
        String cartLevelLabel = null;
        for (RedeemedRebate rebate : rebates) {
            if (rebate.getItemId() == null) {
                cartLevel = cartLevel.add(rebate.getAmount());
                cartLevelLabel = rebate.getLabel();
            }
        }
        List<BigDecimal> itemAttributed = new ArrayList<>(basket.getItemCount());
        List<String> itemLabels = new ArrayList<>(basket.getItemCount());
        for (BasketLineItem line : basket.getItems()) {
            BigDecimal attributed = BigDecimal.ZERO;
            String label = null;
            for (RedeemedRebate rebate : rebates) {
                if (line.getItemId().equals(rebate.getItemId())) {
                    attributed = attributed.add(rebate.getAmount());
                    label = rebate.getLabel();
                }
            }
            itemAttributed.add(attributed);
            itemLabels.add(label);
        }
        List<BigDecimal> cartShares = prorate(basket, itemAttributed, cartLevel);

        List<BasketLineItem> items = new ArrayList<>(basket.getItemCount());
        for (int i = 0; i < basket.getItemCount(); i++) {
            BasketLineItem line = basket.getItems().get(i);
            BigDecimal rebateAmount = itemAttributed.get(i).add(cartShares.get(i));
            String rebateLabel = itemLabels.get(i) != null || cartShares.get(i).signum() <= 0
                    ? itemLabels.get(i) : cartLevelLabel;
            items.add(BasketLineItem.builder()
                    .itemId(line.getItemId())
                    .reference(line.getReference())
                    .sku(line.getSku())
                    .description(line.getDescription())
                    .category(line.getCategory())
                    .quantity(line.getQuantity())
                    .unitPrice(line.getUnitPrice())
                    .discounts(line.getDiscounts())
                    .discountTotal(line.getDiscountTotal())
                    .subtotal(line.getSubtotal())
                    .type(line.getType())
                    .originalTotal(line.getOriginalTotal())
                    .rebateAmount(rebateAmount)
                    .rebateLabel(rebateLabel)
                    .adjustedTotal(line.getSubtotal().subtract(rebateAmount))
                    .taxRate(line.getTaxRate())
                    .taxAmount(line.getTaxAmount())
                    .metadata(line.getMetadata())
                    .build());
        }
        return Basket.builder()
                .cartId(basket.getCartId())
                .items(items)
                .originalTotal(basket.getOriginalTotal())
                .discountTotal(basket.getDiscountTotal())
                .subtotal(basket.getSubtotal())
                .taxTotal(basket.getTaxTotal())
                .grandTotal(basket.getGrandTotal())
                .rebateTotal(totalRebate)
                .build();
    }

    /** Weighted shares of {@code cartLevel} per line, summing exactly. */
    private static List<BigDecimal> prorate(Basket basket, List<BigDecimal> itemAttributed,
                                            BigDecimal cartLevel) {
        List<BigDecimal> shares = new ArrayList<>(basket.getItemCount());
        for (int i = 0; i < basket.getItemCount(); i++) {
            shares.add(BigDecimal.ZERO);
        }
        if (cartLevel.signum() <= 0 || basket.isEmpty()) {
            return shares;
        }
        // weights are each line's remaining (post-item-rebate) total; lines
        // already rebated to zero or below carry no share
        List<BigDecimal> weights = new ArrayList<>(basket.getItemCount());
        BigDecimal weightSum = BigDecimal.ZERO;
        int lastWeighted = -1;
        for (int i = 0; i < basket.getItemCount(); i++) {
            BigDecimal weight = basket.getItems().get(i).getSubtotal()
                    .subtract(itemAttributed.get(i));
            weights.add(weight);
            if (weight.signum() > 0) {
                weightSum = weightSum.add(weight);
                lastWeighted = i;
            }
        }
        if (lastWeighted < 0) {
            // nothing carries weight (all lines fully discounted): put the
            // cart-level amount on the last line rather than dropping it
            shares.set(basket.getItemCount() - 1, cartLevel);
            return shares;
        }
        // running-remainder allocation: each share is computed against the
        // REMAINING amount and weight, never the full cart level — rounded
        // pennies therefore cannot accumulate past the total (a share is
        // capped by what remains, and the last weighted line's ratio is
        // exactly 1, absorbing the exact remainder). Rounding against the
        // full total instead can overshoot with many lines and push the
        // last share negative.
        BigDecimal remainingAmount = cartLevel;
        BigDecimal remainingWeight = weightSum;
        for (int i = 0; i < basket.getItemCount(); i++) {
            BigDecimal weight = weights.get(i);
            if (weight.signum() <= 0) {
                continue;
            }
            BigDecimal share = remainingAmount.multiply(weight)
                    .divide(remainingWeight, 2, RoundingMode.HALF_UP)
                    .min(remainingAmount);
            shares.set(i, share);
            remainingAmount = remainingAmount.subtract(share);
            remainingWeight = remainingWeight.subtract(weight);
        }
        return shares;
    }

    private static Basket withPaymentTotals(Basket basket, BigDecimal rebateTotal,
                                            BigDecimal pointsValue, BigDecimal storedValue,
                                            BigDecimal cardPayment, BigDecimal externalPayment,
                                            BigDecimal cashback) {
        // step handlers may recompute tax on discounted amounts, and that
        // recalculated total is what the tenders actually charged. The final
        // basket must reconcile with the money moved, so the effective tax is
        // derived from the charges: netCharged = (goods − rebates) + tax −
        // points. With no handler adjustments this is exactly the original
        // taxTotal. Cashback rides on the card authorization but is not part
        // of the sale.
        BigDecimal netCharged = storedValue.add(cardPayment).add(externalPayment);
        if (cashback != null) {
            netCharged = netCharged.subtract(cashback);
        }
        BigDecimal effectiveTax = netCharged
                .subtract(basket.getSubtotal().subtract(rebateTotal))
                .add(pointsValue);
        return Basket.builder()
                .cartId(basket.getCartId())
                .items(basket.getItems())
                .originalTotal(basket.getOriginalTotal())
                .discountTotal(basket.getDiscountTotal())
                .subtotal(basket.getSubtotal())
                .taxTotal(effectiveTax)
                .grandTotal(basket.getSubtotal().add(effectiveTax))
                .rebateTotal(rebateTotal)
                .pointDiscountTotal(pointsValue)
                .storedValueTotal(storedValue)
                .cardPaymentTotal(cardPayment)
                .externalPaymentTotal(externalPayment)
                .build();
    }

    private static BigDecimal parseCardBalance(String additionalResponse) {
        if (additionalResponse == null) {
            return null;
        }
        for (String pair : additionalResponse.split("&")) {
            int eq = pair.indexOf('=');
            if (eq > 0 && pair.substring(0, eq).equals("currentBalance")) {
                try {
                    return new BigDecimal(pair.substring(eq + 1));
                } catch (NumberFormatException e) {
                    return null;
                }
            }
        }
        return null;
    }

    private static List<String> parsePromotionMessages(String additionalResponse) {
        List<String> messages = new ArrayList<>();
        if (additionalResponse == null || additionalResponse.isEmpty()) {
            return messages;
        }
        for (String pair : additionalResponse.split("&")) {
            int eq = pair.indexOf('=');
            if (eq > 0 && pair.substring(0, eq).equals("promotionalMessage")) {
                try {
                    messages.add(java.net.URLDecoder.decode(
                            pair.substring(eq + 1), java.nio.charset.StandardCharsets.UTF_8.name()));
                } catch (java.io.UnsupportedEncodingException ignored) {
                    messages.add(pair.substring(eq + 1));
                }
            }
        }
        return messages;
    }
}
