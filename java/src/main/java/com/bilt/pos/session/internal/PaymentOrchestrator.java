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
import com.bilt.pos.nexo.model.TransactionIdentificationType;
import com.bilt.pos.nexo.model.StoredValueTransactionTypeEnum;
import com.bilt.pos.session.Receipt;
import com.bilt.pos.session.SessionError;
import com.bilt.pos.session.SessionErrorCode;
import com.bilt.pos.session.SessionException;
import com.bilt.pos.session.basket.Basket;
import com.bilt.pos.session.basket.BasketLineItem;
import com.bilt.pos.session.identity.IdentifyResult;
import com.bilt.pos.session.settlement.SettlementResult;
import com.bilt.pos.session.settlement.CommittedStep;
import com.bilt.pos.session.payment.GiftCardPaymentResult;
import com.bilt.pos.session.settlement.SettlementRecovery;
import com.bilt.pos.session.settlement.SettlementOptions;
import com.bilt.pos.session.payment.PointRedemptionResult;
import com.bilt.pos.session.payment.RebateRedemptionResult;
import com.bilt.pos.session.payment.RedeemedRebate;
import com.bilt.pos.session.settlement.SettlementContext;
import com.bilt.pos.session.settlement.SettlementMovement;
import com.bilt.pos.session.settlement.SettlementStep;
import com.bilt.pos.session.settlement.SettlementTarget;
import com.bilt.pos.session.settlement.StoredValueLoad;
import com.bilt.pos.session.storedvalue.StoredValueOperationResult;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
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
    private static final int MAX_ERROR_RESOLUTIONS = 3;

    /** Handlers captured from the payment flow. All may be {@code null}. */
    public static final class Handlers {
        public Function<SettlementContext, String> beforeStep;
        public Function<RebateRedemptionResult, BigDecimal> onRebatesRedeemed;
        public Function<PointRedemptionResult, BigDecimal> onPointsRedeemed;
        public Function<GiftCardPaymentResult, BigDecimal> onGiftCardPayment;
        public Consumer<SettlementMovement> onMovement;
        public Function<SessionError, SettlementRecovery> onError;
    }

    /** Inputs to a payment run. */
    public static final class Request {
        public Basket basket;
        public IdentifyResult member;
        public com.bilt.pos.session.storedvalue.StoredValueCard storedValueCard;
        public SettlementOptions options;
        public Handlers handlers = new Handlers();
        public List<CommittedStep> priorSteps = List.of();
        public BooleanSupplier abortRequested = () -> false;
        /** Receives the movements an incomplete unwind left standing. */
        public Consumer<List<StandingMovement>> onUnreversed = movements -> { };
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

        StepFailure(SessionError error, boolean aborted) {
            super(error.toString());
            this.error = error;
            this.aborted = aborted;
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
        boolean disableRebates;
        boolean disablePoints;
        boolean disableAward;

        AttemptOptions(SettlementOptions request) {
            this.cashback = request.getCashback();
            this.paymentProcessingDisplay = request.getPaymentProcessingDisplay();
            this.disableRebates = request.isDisableRebates();
            this.disablePoints = request.isDisablePoints();
            this.disableAward = request.isDisableAward();
        }

        void apply(SettlementRecovery recovery) {
            if (recovery.getDisableRebates() != null) {
                disableRebates = recovery.getDisableRebates();
            }
            if (recovery.getDisablePoints() != null) {
                disablePoints = recovery.getDisablePoints();
            }
            if (recovery.getDisableAward() != null) {
                disableAward = recovery.getDisableAward();
            }
        }
    }

    private final NexoExchange exchange;
    private final String currency;
    private final StoredValueManager storedValueManager;

    public PaymentOrchestrator(NexoExchange exchange, String currency) {
        this.exchange = exchange;
        this.currency = currency;
        this.storedValueManager = new StoredValueManager(exchange, currency);
    }

    /**
     * Runs the payment. On unrecoverable failure the committed steps are
     * reversed and a {@link SessionException} is thrown — with
     * {@link SessionErrorCode#ABORTED} when the run was aborted.
     */
    public SettlementResult run(Request request) {
        AttemptOptions options = new AttemptOptions(request.options);
        int resolutions = 0;
        while (true) {
            List<Commit> committed = new ArrayList<>();
            try {
                return runSequence(request, options, committed);
            } catch (RuntimeException e) {
                // a register handler (or other bug) throwing must not leave
                // committed tender/loyalty steps standing: treat it like a
                // step failure — unwind, then let onError decide
                StepFailure failure = e instanceof StepFailure
                        ? (StepFailure) e
                        : new StepFailure(new SessionError(SessionErrorCode.UNKNOWN,
                                "unexpected error during payment: " + e, null, e), false);
                List<StandingMovement> unreversed = unwind(committed);
                if (!unreversed.isEmpty()) {
                    request.onUnreversed.accept(unreversed);
                }
                SessionError error = withRollbackFailures(failure.error, unreversed);
                // the abort path bypasses onError. It is taken when checkAbort
                // trips between steps, and also when our own abort() killed
                // the in-flight step (the terminal replies Aborted) — an
                // intentional abort must not surface as a recoverable failure.
                // A terminal-initiated abort without our flag still reaches
                // onError, since the register may want to retry it.
                if (failure.aborted
                        || (request.abortRequested.getAsBoolean()
                                && error.getCode() == SessionErrorCode.ABORTED)) {
                    throw new SessionException(error);
                }
                resolutions++;
                SettlementRecovery resolution = request.handlers.onError == null
                        ? SettlementRecovery.abort()
                        : request.handlers.onError.apply(error);
                boolean wantsRetry = resolution != null && !resolution.isAbort();
                if (!wantsRetry) {
                    throw new SessionException(error);
                }
                // a retry on top of an incomplete unwind would run the
                // sequence again while a previous movement still stands —
                // double-charging the tender or double-redeeming loyalty.
                // Whatever onError asked for, only a clean unwind may retry.
                if (!unreversed.isEmpty()) {
                    LOGGER.warning("onError requested a retry, but the rollback was "
                            + "incomplete; failing the payment instead");
                    throw new SessionException(Wire.annotated(error, error.getMessage()
                            + "; the requested retry was refused — only a clean unwind "
                            + "may retry", error.getCause()));
                }
                if (resolutions >= MAX_ERROR_RESOLUTIONS) {
                    LOGGER.warning("payment retry limit reached; failing the payment");
                    throw new SessionException(Wire.annotated(error, error.getMessage()
                            + "; the retry limit was reached and the payment failed",
                            error.getCause()));
                }
                options.apply(resolution);
            }
        }
    }

    // ─── The sequence ───

    private SettlementResult runSequence(Request request, AttemptOptions options,
                                       List<Commit> committed) {
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
        BigDecimal storedValueLoaded = BigDecimal.ZERO;

        // 1. Rebate redemption
        if (chargeDue && loyalty && !options.disableRebates) {
            checkAbort(request);
            String saleTxnId = beforeStep(request, SettlementStep.REBATE_REDEMPTION,
                    workingBasket, currentTotal, committed);
            RebateOutcome outcome = rebateStep(request, workingBasket, currentTotal,
                    saleTxnId, committed, result);
            redeemedRebates = outcome.rebates;
            rebateTotal = outcome.totalRebate;
            workingBasket = outcome.updatedBasket;
            currentTotal = applyHandlerTotal(request.handlers.onRebatesRedeemed,
                    outcome.result, outcome.result.getSuggestedTotal());
        }

        // 2. Point / reward redemption — like the stored value and card
        // steps, skipped once rebates (or the register's handler) have
        // brought the running total to zero: there is nothing left to pay,
        // so redeeming would take the member's points for no discount
        if (loyalty && !options.disablePoints && !request.member.getRewards().isEmpty()
                && currentTotal.signum() > 0) {
            checkAbort(request);
            String saleTxnId = beforeStep(request, SettlementStep.POINT_REDEMPTION,
                    workingBasket, currentTotal, committed);
            PointRedemptionResult points = pointsStep(request, workingBasket, currentTotal,
                    saleTxnId, committed, result);
            pointsRedeemed = points.getPointsUsed();
            pointsValue = points.getMonetaryValue();
            result.pointsBalance(points.getRemainingPointBalance());
            currentTotal = applyHandlerTotal(request.handlers.onPointsRedeemed,
                    points, points.getSuggestedTotal());
        }

        // 3. Stored value
        if (request.storedValueCard != null && currentTotal.signum() > 0) {
            checkAbort(request);
            String saleTxnId = beforeStep(request, SettlementStep.STORED_VALUE_CHARGE,
                    workingBasket, currentTotal, committed);
            GiftCardPaymentResult giftCard = storedValueStep(request, currentTotal,
                    saleTxnId, committed, result);
            storedValueCharged = giftCard.getAmountCharged();
            currentTotal = applyHandlerTotal(request.handlers.onGiftCardPayment,
                    giftCard, giftCard.getSuggestedTotal());
        }

        // 4. Card payment
        if (currentTotal.signum() > 0) {
            checkAbort(request);
            if (options.paymentProcessingDisplay != null) {
                showProcessingDisplay(options.paymentProcessingDisplay);
            }
            String saleTxnId = beforeStep(request, SettlementStep.CARD_CHARGE,
                    workingBasket, currentTotal, committed);
            cardCharged = cardStep(request, workingBasket, currentTotal, options,
                    saleTxnId, committed, result);
        }

        // 5. Fulfill each stored value line after its funding commits. A
        // failure reverses earlier loads and the charge-side movements.
        for (StoredValueLoad fulfillment : request.options.getFulfillments()) {
            checkAbort(request);
            BasketLineItem line = request.basket.getItemByReference(
                    fulfillment.getBasketReference());
            BigDecimal amount = line.getOriginalTotal();
            String saleTxnId = beforeStep(request, SettlementStep.STORED_VALUE_LOAD,
                    workingBasket, amount, committed);
            storedValueLoaded = storedValueLoaded.add(storedValueLoadStep(request,
                    fulfillment, amount, saleTxnId, committed, result));
        }

        // 6. Award (best-effort — never fails the checkout; terminal SAFs)
        if (chargeDue && loyalty && !options.disableAward) {
            checkAbort(request);
            String saleTxnId = beforeStep(request, SettlementStep.AWARD,
                    workingBasket, storedValueCharged.add(cardCharged), committed);
            awardStep(request, workingBasket, saleTxnId, committed, result);
        }

        // An abort that landed during the award must still unwind the money
        // movements — the award's own failures are non-fatal, but an abort is
        // a rollback request, not an award failure.
        checkAbort(request);

        Basket finalBasket = chargeDue
                ? withPaymentTotals(workingBasket, rebateTotal, pointsValue,
                        storedValueCharged, cardCharged, options.cashback)
                : workingBasket;

        // last look before success is declared; later aborts are too late
        // and the completed payment stands (void it explicitly instead)
        checkAbort(request);

        return result
                .finalBasket(finalBasket)
                .authorizedAmount(storedValueCharged.add(cardCharged))
                .storedValueAmountUsed(storedValueCharged)
                .cardAmountCharged(cardCharged)
                .storedValueLoadedAmount(storedValueLoaded)
                .redeemedRebates(redeemedRebates)
                .totalRebateAmount(rebateTotal)
                .pointsRedeemed(pointsRedeemed)
                .pointsMonetaryValue(pointsValue)
                .build();
    }

    // ─── Steps ───

    private static final class RebateOutcome {
        List<RedeemedRebate> rebates;
        BigDecimal totalRebate;
        Basket updatedBasket;
        RebateRedemptionResult result;
    }

    private RebateOutcome rebateStep(Request request, Basket basket, BigDecimal currentTotal,
                                     String saleTxnId, List<Commit> committed,
                                     SettlementResult.Builder result) {
        LoyaltyResponse body = sendLoyalty(LoyaltyTransactionTypeEnum.REBATE, request, basket,
                saleTxnId, null);

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
        publishMovement(request, result, movement(SettlementStep.REBATE_REDEMPTION,
                totalRebate, saleTxnId, body.getPoiData(), request.member.getMemberId(),
                null, null));
        // kept on the result so a checkout with no payment legs (rewards
        // covered everything) can still be voided by reversing this movement
        TransactionIdentificationType rebatePoiTxn = Wire.poiRef(body.getPoiData());
        if (totalRebate.signum() > 0 && rebatePoiTxn != null) {
            result.rebatePoiTransactionId(rebatePoiTxn.getTransactionID());
            result.rebatePoiTransactionTimestamp(Wire.instant(rebatePoiTxn.getTimeStamp()));
        }

        RebateOutcome outcome = new RebateOutcome();
        outcome.rebates = rebates;
        outcome.totalRebate = totalRebate;
        outcome.updatedBasket = applyRebates(request.basket, rebates, totalRebate);
        outcome.result = new RebateRedemptionResult(rebates, totalRebate, currentTotal,
                currentTotal.subtract(totalRebate), outcome.updatedBasket);
        return outcome;
    }

    private PointRedemptionResult pointsStep(Request request, Basket basket,
                                             BigDecimal currentTotal, String saleTxnId,
                                             List<Commit> committed,
                                             SettlementResult.Builder result) {
        String rewardRefsPayload = LoyaltyPayloadCodec.memberRewardRefs(
                request.member.getRewards());
        LoyaltyResponse body = sendLoyalty(LoyaltyTransactionTypeEnum.REDEMPTION, request, basket,
                saleTxnId, rewardRefsPayload);

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
        publishMovement(request, result, movement(SettlementStep.POINT_REDEMPTION,
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
                                                  SettlementResult.Builder result) {
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
                currentTotal);
        BigDecimal charged = body.getPaymentResult() != null
                && body.getPaymentResult().getAmountsResp() != null
                ? Wire.money(body.getPaymentResult().getAmountsResp().getAuthorizedAmount())
                : currentTotal;
        BigDecimal remainingBalance = parseCardBalance(
                body.getResponse().getAdditionalResponse());

        commit(committed, SettlementStep.STORED_VALUE_CHARGE, saleTxnId, body.getPoiData(),
                reversalRollback(Wire.poiRef(body.getPoiData())));
        publishMovement(request, result, movement(SettlementStep.STORED_VALUE_CHARGE,
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
            SettlementResult.Builder result) {
        StoredValueTransactionTypeEnum type = fulfillment.getType() == StoredValueLoad.Type.ACTIVATE
                ? StoredValueTransactionTypeEnum.ACTIVATE : StoredValueTransactionTypeEnum.LOAD;
        StoredValueOperationResult loaded;
        try {
            loaded = storedValueManager.operation(type, fulfillment.getCard(), amount,
                    null, null, saleTxnId);
        } catch (SessionException e) {
            throw new StepFailure(e.getError(), false);
        }
        BigDecimal actualAmount = loaded.getAmount() == null ? amount : loaded.getAmount();
        Runnable rollback = loaded.getPoiTransactionId() == null ? null : () ->
                storedValueManager.operation(StoredValueTransactionTypeEnum.REVERSE,
                        null, null, loaded.getPoiTransactionId(),
                        loaded.getPoiTransactionTimestamp());
        commit(committed, SettlementStep.STORED_VALUE_LOAD, saleTxnId,
                loaded.getPoiTransactionId(), loaded.getPoiTransactionTimestamp(), rollback);
        publishMovement(request, result, movement(SettlementStep.STORED_VALUE_LOAD,
                fulfillment.getTarget(), actualAmount, saleTxnId,
                loaded.getPoiTransactionId(), loaded.getPoiTransactionTimestamp(),
                null, null, null));
        if (actualAmount.compareTo(amount) != 0) {
            throw new StepFailure(new SessionError(SessionErrorCode.DECLINED,
                    "the stored value fulfillment loaded " + actualAmount
                            + " instead of the basket line's " + amount
                            + "; the partial load is reversed"), false);
        }
        return actualAmount;
    }

    private BigDecimal cardStep(Request request, Basket basket, BigDecimal currentTotal,
                                AttemptOptions options, String saleTxnId,
                                List<Commit> committed, SettlementResult.Builder result) {
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
                grossTotal);
        BigDecimal charged = body.getPaymentResult() != null
                && body.getPaymentResult().getAmountsResp() != null
                ? Wire.money(body.getPaymentResult().getAmountsResp().getAuthorizedAmount())
                : grossTotal;

        // commit BEFORE validating the amount so an under-authorization is
        // reversed by the unwind like any other committed step
        commit(committed, SettlementStep.CARD_CHARGE, saleTxnId, body.getPoiData(),
                reversalRollback(Wire.poiRef(body.getPoiData())));
        publishMovement(request, result, movement(SettlementStep.CARD_CHARGE,
                charged, saleTxnId, body.getPoiData(), null, null, null));

        // a partial authorization on the stored value step is the split
        // tender mechanism, but the card step is the FINAL tender — an
        // under-authorization here would complete a short-paid sale (or
        // hand out cashback the sale never collected)
        if (charged.compareTo(grossTotal) < 0) {
            throw new StepFailure(new SessionError(SessionErrorCode.DECLINED,
                    "the card payment authorized only " + charged + " of the requested "
                            + grossTotal + "; the partial authorization is reversed"),
                    false);
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
                           SettlementResult.Builder result) {
        try {
            LoyaltyResponse body = sendLoyalty(LoyaltyTransactionTypeEnum.AWARD, request, basket,
                    saleTxnId, null);
            // an abort observed after this point unwinds the tender — the
            // credited points must be reversed with it, like void does
            TransactionIdentificationType awardPoiTxn = Wire.poiRef(body.getPoiData());
            commit(committed, SettlementStep.AWARD, saleTxnId, body.getPoiData(),
                    awardPoiTxn != null
                            ? loyaltyRollback(LoyaltyTransactionTypeEnum.AWARD_REFUND,
                                    awardPoiTxn, request.member.getMemberId())
                            : null);
            if (awardPoiTxn != null) {
                // kept on the result so a later void/refund can reverse the
                // award by its own reference, per the reverse-award contract
                result.awardPoiTransactionId(awardPoiTxn.getTransactionID());
                result.awardPoiTransactionTimestamp(Wire.instant(awardPoiTxn.getTimeStamp()));
            }
            LoyaltyResult first = Wire.firstLoyaltyResult(body);
            if (first != null && first.getLoyaltyAmount() != null) {
                result.totalPointsEarned((int) Math.round(
                        first.getLoyaltyAmount().getAmountValue()));
            }
            if (first != null && first.getCurrentBalance() != null) {
                result.pointsBalance((int) Math.round(first.getCurrentBalance()));
            }
            publishMovement(request, result, movement(SettlementStep.AWARD,
                    BigDecimal.ZERO, saleTxnId, body.getPoiData(), request.member.getMemberId(),
                    first != null && first.getLoyaltyAmount() != null
                            ? (int) Math.round(first.getLoyaltyAmount().getAmountValue()) : null,
                    first != null && first.getCurrentBalance() != null
                            ? (int) Math.round(first.getCurrentBalance()) : null));
            result.promotionMessages(parsePromotionMessages(
                    body.getResponse().getAdditionalResponse()));
            result.earnedRewards(LoyaltyPayloadCodec.parseEarnedRewards(
                    body.getResponse().getAdditionalResponse()));
        } catch (SessionException | StepFailure e) {
            // decision: a failed award never reverses a completed payment.
            // Only the award's own wire failure is swallowed here — a
            // cross-thread abort() (which may be what killed the award) is
            // observed by the checkAbort() right after this step and unwinds
            // the committed payment/stored-value/loyalty steps.
            String detail = e instanceof SessionException
                    ? ((SessionException) e).getError().toString() : e.getMessage();
            LOGGER.log(Level.WARNING, "loyalty award failed (terminal may retry via SAF): "
                    + detail, e);
            result.warning("loyalty award failed; points may be credited later: " + detail);
        }
    }

    // ─── Wire plumbing ───

    private LoyaltyResponse sendLoyalty(LoyaltyTransactionTypeEnum type, Request request,
                                        Basket basket, String saleTxnId,
                                        String saleToPoiData) {
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
        try {
            SaleToPOIResponse response = exchange.sendExpectingBody(
                    MessageCategoryType.LOYALTY, wireRequest);
            LoyaltyResponse body = response.getLoyaltyResponse();
            if (body == null) {
                throw Wire.missing("LoyaltyResponse");
            }
            exchange.requireSuccess(MessageCategoryType.LOYALTY, body.getResponse());
            return body;
        } catch (SessionException e) {
            throw new StepFailure(e.getError(), false);
        }
    }

    private PaymentResponse sendPayment(SettlementStep step, SaleToPOIRequest wireRequest,
                                        BigDecimal requestedAmount) {
        PaymentResponse body;
        try {
            SaleToPOIResponse response = exchange.sendExpectingBody(
                    MessageCategoryType.PAYMENT, wireRequest);
            body = response.getPaymentResponse();
            if (body == null) {
                throw Wire.missing("PaymentResponse");
            }
        } catch (SessionException e) {
            throw new StepFailure(e.getError(), false);
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
            throw new StepFailure(error, false);
        }
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

    private <R> BigDecimal applyHandlerTotal(Function<R, BigDecimal> handler, R stepResult,
                                             BigDecimal suggested) {
        BigDecimal total = handler == null ? suggested : handler.apply(stepResult);
        if (total == null || total.signum() < 0) {
            throw new StepFailure(new SessionError(SessionErrorCode.UNKNOWN,
                    "a payment step handler returned an invalid total: " + total), false);
        }
        return total;
    }

    private void checkAbort(Request request) {
        if (request.abortRequested.getAsBoolean()) {
            throw new StepFailure(new SessionError(SessionErrorCode.ABORTED,
                    "the payment was aborted"), true);
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

    private static void publishMovement(Request request, SettlementResult.Builder result,
                                        SettlementMovement movement) {
        result.movement(movement);
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
                    .direction(line.getDirection())
                    .purpose(line.getPurpose())
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
                                            BigDecimal cardPayment, BigDecimal cashback) {
        // step handlers may recompute tax on discounted amounts, and that
        // recalculated total is what the tenders actually charged. The final
        // basket must reconcile with the money moved, so the effective tax is
        // derived from the charges: netCharged = (goods − rebates) + tax −
        // points. With no handler adjustments this is exactly the original
        // taxTotal. Cashback rides on the card authorization but is not part
        // of the sale.
        BigDecimal netCharged = storedValue.add(cardPayment);
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
