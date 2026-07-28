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
import com.bilt.pos.nexo.model.PaymentInstrumentData;
import com.bilt.pos.nexo.model.PaymentInstrumentTypeEnum;
import com.bilt.pos.nexo.model.PaymentRequest;
import com.bilt.pos.nexo.model.PaymentResponse;
import com.bilt.pos.nexo.model.PaymentTransaction;
import com.bilt.pos.nexo.model.Rebates;
import com.bilt.pos.nexo.model.ReversalReasonEnum;
import com.bilt.pos.nexo.model.ReversalRequest;
import com.bilt.pos.nexo.model.SaleData;
import com.bilt.pos.nexo.model.SaleItem;
import com.bilt.pos.nexo.model.SaleItemRebate;
import com.bilt.pos.nexo.model.SaleToPOIRequest;
import com.bilt.pos.nexo.model.SaleToPOIResponse;
import com.bilt.pos.nexo.model.TransactionIdentificationType;
import com.bilt.pos.session.Receipt;
import com.bilt.pos.session.SessionError;
import com.bilt.pos.session.SessionErrorCode;
import com.bilt.pos.session.SessionException;
import com.bilt.pos.session.basket.Basket;
import com.bilt.pos.session.basket.BasketLineItem;
import com.bilt.pos.session.identity.IdentifyResult;
import com.bilt.pos.session.identity.Reward;
import com.bilt.pos.session.payment.CheckoutResult;
import com.bilt.pos.session.payment.CommittedStep;
import com.bilt.pos.session.payment.GiftCardPaymentResult;
import com.bilt.pos.session.payment.PaymentOptions;
import com.bilt.pos.session.payment.PointRedemptionResult;
import com.bilt.pos.session.payment.RebateRedemptionResult;
import com.bilt.pos.session.payment.RedeemedRebate;
import com.bilt.pos.session.payment.TransactionContext;
import com.bilt.pos.session.payment.TransactionStep;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Runs the payment sequence — rebate → points → stored value → card → award —
 * with commit tracking, best-effort rollback, and {@code onError}-driven
 * retry. All steps and handlers run on the calling thread.
 */
public final class PaymentOrchestrator {

    private static final Logger LOGGER = Logger.getLogger(PaymentOrchestrator.class.getName());
    private static final int MAX_ERROR_RESOLUTIONS = 3;

    /** Handlers captured from the payment flow. All may be {@code null}. */
    public static final class Handlers {
        public Function<TransactionContext, String> beforeStep;
        public Function<RebateRedemptionResult, BigDecimal> onRebatesRedeemed;
        public Function<PointRedemptionResult, BigDecimal> onPointsRedeemed;
        public Function<GiftCardPaymentResult, BigDecimal> onGiftCardPayment;
        public Function<SessionError, PaymentOptions> onError;
    }

    /** Inputs to a payment run. */
    public static final class Request {
        public Basket basket;
        public IdentifyResult member;
        public com.bilt.pos.session.storedvalue.StoredValueCard storedValueCard;
        public PaymentOptions options;
        public Handlers handlers = new Handlers();
        public BooleanSupplier abortRequested = () -> false;
        public Consumer<Basket> finalDisplay = basket -> { };
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

    private final NexoExchange exchange;
    private final String currency;

    public PaymentOrchestrator(NexoExchange exchange, String currency) {
        this.exchange = exchange;
        this.currency = currency;
    }

    /**
     * Runs the payment. On unrecoverable failure the committed steps are
     * reversed and a {@link SessionException} is thrown — with
     * {@link SessionErrorCode#ABORTED} when the run was aborted.
     */
    public CheckoutResult run(Request request) {
        PaymentOptions options = request.options;
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
                unwind(committed);
                if (failure.aborted) {
                    throw new SessionException(failure.error);
                }
                resolutions++;
                PaymentOptions resolution = request.handlers.onError == null
                        ? PaymentOptions.voidAndAbort()
                        : request.handlers.onError.apply(failure.error);
                if (resolution == null || resolution.isVoidAndAbort()
                        || resolutions >= MAX_ERROR_RESOLUTIONS) {
                    if (resolutions >= MAX_ERROR_RESOLUTIONS && resolution != null
                            && !resolution.isVoidAndAbort()) {
                        LOGGER.warning("payment retry limit reached; failing the payment");
                    }
                    throw new SessionException(failure.error);
                }
                options = resolution;
            }
        }
    }

    // ─── The sequence ───

    private CheckoutResult runSequence(Request request, PaymentOptions options,
                                       List<Commit> committed) {
        Basket workingBasket = request.basket;
        BigDecimal currentTotal = workingBasket.getGrandTotal();
        boolean loyalty = request.member != null;

        CheckoutResult.Builder result = CheckoutResult.builder().success(true);
        List<RedeemedRebate> redeemedRebates = new ArrayList<>();
        BigDecimal rebateTotal = BigDecimal.ZERO;
        int pointsRedeemed = 0;
        BigDecimal pointsValue = BigDecimal.ZERO;
        BigDecimal storedValueCharged = BigDecimal.ZERO;
        BigDecimal cardCharged = BigDecimal.ZERO;

        // 1. Rebate redemption
        if (loyalty && !options.isDisableRebates()) {
            checkAbort(request);
            String saleTxnId = beforeStep(request, TransactionStep.REBATE,
                    workingBasket, currentTotal, committed);
            RebateOutcome outcome = rebateStep(request, workingBasket, currentTotal,
                    saleTxnId, committed);
            redeemedRebates = outcome.rebates;
            rebateTotal = outcome.totalRebate;
            workingBasket = outcome.updatedBasket;
            currentTotal = applyHandlerTotal(request.handlers.onRebatesRedeemed,
                    outcome.result, outcome.result.getSuggestedTotal());
        }

        // 2. Point / reward redemption
        if (loyalty && !options.isDisablePoints() && !request.member.getRewards().isEmpty()) {
            checkAbort(request);
            String saleTxnId = beforeStep(request, TransactionStep.POINTS,
                    workingBasket, currentTotal, committed);
            PointRedemptionResult points = pointsStep(request, workingBasket, currentTotal,
                    saleTxnId, committed);
            pointsRedeemed = points.getPointsUsed();
            pointsValue = points.getMonetaryValue();
            result.pointsBalance(points.getRemainingPointBalance());
            currentTotal = applyHandlerTotal(request.handlers.onPointsRedeemed,
                    points, points.getSuggestedTotal());
        }

        // 3. Stored value
        if (request.storedValueCard != null && currentTotal.signum() > 0) {
            checkAbort(request);
            String saleTxnId = beforeStep(request, TransactionStep.STORED_VALUE,
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
            if (options.getPaymentProcessingDisplay() != null) {
                showProcessingDisplay(options.getPaymentProcessingDisplay());
            }
            String saleTxnId = beforeStep(request, TransactionStep.CARD_PAYMENT,
                    workingBasket, currentTotal, committed);
            cardCharged = cardStep(request, workingBasket, currentTotal, options,
                    saleTxnId, committed, result);
        }

        // 5. Award (best-effort — never fails the checkout; terminal SAFs)
        if (loyalty) {
            checkAbort(request);
            String saleTxnId = beforeStep(request, TransactionStep.AWARD,
                    workingBasket, storedValueCharged.add(cardCharged), committed);
            awardStep(request, workingBasket, saleTxnId, committed, result);
        }

        // An abort that landed during the award must still unwind the money
        // movements — the award's own failures are non-fatal, but an abort is
        // a rollback request, not an award failure.
        checkAbort(request);

        Basket finalBasket = withPaymentTotals(workingBasket, rebateTotal, pointsValue,
                storedValueCharged, cardCharged);

        // 6. Final display (best-effort)
        try {
            request.finalDisplay.accept(finalBasket);
        } catch (RuntimeException e) {
            LOGGER.log(Level.WARNING, "final display failed", e);
        }

        // last look before success is declared; later aborts are too late
        // and the completed payment stands (void it explicitly instead)
        checkAbort(request);

        return result
                .finalBasket(finalBasket)
                .authorizedAmount(storedValueCharged.add(cardCharged))
                .storedValueAmountUsed(storedValueCharged)
                .cardAmountCharged(cardCharged)
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
                                     String saleTxnId, List<Commit> committed) {
        LoyaltyResponse body = sendLoyalty(LoyaltyTransactionTypeEnum.REBATE, request, basket,
                saleTxnId, null);

        List<RedeemedRebate> rebates = new ArrayList<>();
        BigDecimal totalRebate = BigDecimal.ZERO;
        Rebates wireRebates = firstResult(body) == null ? null : firstResult(body).getRebates();
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
            totalRebate = wireRebates.getTotalRebate() != null
                    ? Wire.money(wireRebates.getTotalRebate())
                    : rebates.stream().map(RedeemedRebate::getAmount)
                            .reduce(BigDecimal.ZERO, BigDecimal::add);
        }

        commit(committed, TransactionStep.REBATE, saleTxnId, body.getPoiData(),
                totalRebate.signum() > 0
                        ? loyaltyRollback(LoyaltyTransactionTypeEnum.REBATE_REFUND,
                                poiRef(body.getPoiData()), request.member.getMemberId(), null)
                        : null);

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
                                             List<Commit> committed) {
        List<String> rewardRefs = new ArrayList<>();
        for (Reward reward : request.member.getRewards()) {
            if (reward.getRewardRef() != null) {
                rewardRefs.add(reward.getRewardRef());
            }
        }
        String rewardRefsPayload = LoyaltyPayloadCodec.encodeRewardRefs(rewardRefs);
        LoyaltyResponse body = sendLoyalty(LoyaltyTransactionTypeEnum.REDEMPTION, request, basket,
                saleTxnId, rewardRefsPayload);

        LoyaltyResult first = firstResult(body);
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

        commit(committed, TransactionStep.POINTS, saleTxnId, body.getPoiData(),
                monetaryValue.signum() > 0
                        ? loyaltyRollback(LoyaltyTransactionTypeEnum.REDEMPTION_REFUND,
                                poiRef(body.getPoiData()), request.member.getMemberId(),
                                rewardRefsPayload)
                        : null);

        return new PointRedemptionResult(pointsUsed, monetaryValue, currentTotal,
                currentTotal.subtract(monetaryValue), balance);
    }

    private GiftCardPaymentResult storedValueStep(Request request, BigDecimal currentTotal,
                                                  String saleTxnId, List<Commit> committed,
                                                  CheckoutResult.Builder result) {
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

        PaymentResponse body = sendPayment(TransactionStep.STORED_VALUE, wireRequest,
                currentTotal);
        BigDecimal charged = body.getPaymentResult() != null
                && body.getPaymentResult().getAmountsResp() != null
                ? Wire.money(body.getPaymentResult().getAmountsResp().getAuthorizedAmount())
                : currentTotal;
        BigDecimal remainingBalance = parseCardBalance(
                body.getResponse().getAdditionalResponse());

        commit(committed, TransactionStep.STORED_VALUE, saleTxnId, body.getPoiData(),
                reversalRollback(poiRef(body.getPoiData())));

        // a gift-card-only checkout has no card step: this payment's
        // references/receipts must reach the result so the session can void
        // or refund it (the card step overwrites them when it runs)
        copyPaymentArtifacts(body, result);
        // the split-tender leg keeps its own reference so a session void can
        // reverse BOTH legs, not just the card payment
        TransactionIdentificationType storedValuePoiTxn = poiRef(body.getPoiData());
        if (storedValuePoiTxn != null) {
            result.storedValuePoiTransactionId(storedValuePoiTxn.getTransactionID());
            result.storedValuePoiTransactionTimestamp(
                    Wire.instant(storedValuePoiTxn.getTimeStamp()));
        }

        return new GiftCardPaymentResult(charged, remainingBalance, currentTotal,
                currentTotal.subtract(charged));
    }

    private BigDecimal cardStep(Request request, Basket basket, BigDecimal currentTotal,
                                PaymentOptions options, String saleTxnId,
                                List<Commit> committed, CheckoutResult.Builder result) {
        AmountsReq.Builder amounts = AmountsReq.builder()
                .currency(currency)
                .requestedAmount(currentTotal.doubleValue());
        if (options.getCashback() != null) {
            amounts.cashBackAmount(options.getCashback().doubleValue());
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

        PaymentResponse body = sendPayment(TransactionStep.CARD_PAYMENT, wireRequest,
                currentTotal);
        BigDecimal charged = body.getPaymentResult() != null
                && body.getPaymentResult().getAmountsResp() != null
                ? Wire.money(body.getPaymentResult().getAmountsResp().getAuthorizedAmount())
                : currentTotal;

        // commit BEFORE validating the amount so an under-authorization is
        // reversed by the unwind like any other committed step
        commit(committed, TransactionStep.CARD_PAYMENT, saleTxnId, body.getPoiData(),
                reversalRollback(poiRef(body.getPoiData())));

        // a partial authorization on the stored value step is the split
        // tender mechanism, but the card step is the FINAL tender — an
        // under-authorization here would complete a short-paid sale
        if (charged.compareTo(currentTotal) < 0) {
            throw new StepFailure(new SessionError(SessionErrorCode.DECLINED,
                    "the card payment authorized only " + charged + " of the requested "
                            + currentTotal + "; the partial authorization is reversed"),
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
    private static void copyPaymentArtifacts(PaymentResponse body, CheckoutResult.Builder result) {
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
        TransactionIdentificationType poiTxn = poiRef(body.getPoiData());
        if (poiTxn != null) {
            result.poiTransactionId(poiTxn.getTransactionID());
            result.poiTransactionTimestamp(Wire.instant(poiTxn.getTimeStamp()));
        }
    }

    private void awardStep(Request request, Basket basket,
                           String saleTxnId, List<Commit> committed,
                           CheckoutResult.Builder result) {
        try {
            LoyaltyResponse body = sendLoyalty(LoyaltyTransactionTypeEnum.AWARD, request, basket,
                    saleTxnId, null);
            // an abort observed after this point unwinds the tender — the
            // credited points must be reversed with it, like void does
            TransactionIdentificationType awardPoiTxn = poiRef(body.getPoiData());
            commit(committed, TransactionStep.AWARD, saleTxnId, body.getPoiData(),
                    awardPoiTxn != null
                            ? loyaltyRollback(LoyaltyTransactionTypeEnum.AWARD_REFUND,
                                    awardPoiTxn, request.member.getMemberId(), null)
                            : null);
            if (awardPoiTxn != null) {
                // kept on the result so a later void/refund can reverse the
                // award by its own reference, per the reverse-award contract
                result.awardPoiTransactionId(awardPoiTxn.getTransactionID());
                result.awardPoiTransactionTimestamp(Wire.instant(awardPoiTxn.getTimeStamp()));
            }
            LoyaltyResult first = firstResult(body);
            if (first != null && first.getLoyaltyAmount() != null) {
                result.totalPointsEarned((int) Math.round(
                        first.getLoyaltyAmount().getAmountValue()));
            }
            if (first != null && first.getCurrentBalance() != null) {
                result.pointsBalance((int) Math.round(first.getCurrentBalance()));
            }
            result.promotionMessages(parsePromotionMessages(
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
        SaleData.Builder saleData = SaleData.builder()
                .saleTransactionID(TransactionIdentificationType.builder()
                        .transactionID(saleTxnId)
                        .timeStamp(Instant.now().toString())
                        .build());
        if (saleToPoiData != null) {
            saleData.saleToPOIData(saleToPoiData);
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
                .loyaltyAccountID(LoyaltyAccountID.builder()
                        .loyaltyID(request.member.getMemberId())
                        .identificationType(IdentificationTypeEnum.PAN)
                        .entryMode(new EntryModeType[] {EntryModeType.KEYED})
                        .build());
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
                .saleData(saleData.build())
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

    private PaymentResponse sendPayment(TransactionStep step, SaleToPOIRequest wireRequest,
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
            if (step == TransactionStep.STORED_VALUE
                    && error.getCode() == SessionErrorCode.DECLINED
                    && requestedAmount != null) {
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

    private String beforeStep(Request request, TransactionStep step, Basket basket,
                              BigDecimal currentTotal, List<Commit> committed) {
        String defaultId = UUID.randomUUID().toString();
        if (request.handlers.beforeStep == null) {
            return defaultId;
        }
        List<CommittedStep> prior = new ArrayList<>();
        for (Commit commit : committed) {
            prior.add(commit.info);
        }
        String id = request.handlers.beforeStep.apply(new TransactionContext(
                step, basket, currentTotal, defaultId, prior));
        return id != null && !id.isEmpty() ? id : defaultId;
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

    private void commit(List<Commit> committed, TransactionStep step, String saleTxnId,
                        com.bilt.pos.nexo.model.POIData poiData, Runnable rollback) {
        TransactionIdentificationType poiTxn = poiData == null
                ? null : poiData.getPoiTransactionID();
        committed.add(new Commit(
                new CommittedStep(step, saleTxnId,
                        poiTxn == null ? null : poiTxn.getTransactionID(),
                        poiTxn == null ? null : Wire.instant(poiTxn.getTimeStamp()),
                        true),
                rollback));
    }

    // ─── Rollback ───

    private void unwind(List<Commit> committed) {
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
            }
        }
    }

    private Runnable loyaltyRollback(LoyaltyTransactionTypeEnum refundType,
                                     TransactionIdentificationType originalPoiTxn,
                                     String memberId, String saleToPoiData) {
        return () -> {
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
                            .originalPOITransaction(original(originalPoiTxn))
                            .build());
            if (memberId != null) {
                loyaltyRequest.loyaltyData(new LoyaltyData[] {LoyaltyData.builder()
                        .loyaltyAccountID(LoyaltyAccountID.builder()
                                .loyaltyID(memberId)
                                .identificationType(IdentificationTypeEnum.PAN)
                                .entryMode(new EntryModeType[] {EntryModeType.KEYED})
                                .build())
                        .build()});
            }
            SaleToPOIRequest wireRequest = SaleToPOIRequest.builder()
                    .messageHeader(exchange.factory().header(
                            MessageClassType.SERVICE, MessageCategoryType.LOYALTY))
                    .loyaltyRequest(loyaltyRequest.build())
                    .build();
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
            SaleToPOIRequest wireRequest = SaleToPOIRequest.builder()
                    .messageHeader(exchange.factory().header(
                            MessageClassType.SERVICE, MessageCategoryType.REVERSAL))
                    .reversalRequest(ReversalRequest.builder()
                            .saleData(exchange.factory().saleData())
                            .originalPOITransaction(original(originalPoiTxn))
                            .reversalReason(ReversalReasonEnum.MERCHANT_CANCEL)
                            .build())
                    .build();
            SaleToPOIResponse response = exchange.sendExpectingBody(
                    MessageCategoryType.REVERSAL, wireRequest);
            if (response.getReversalResponse() != null) {
                exchange.requireSuccess(MessageCategoryType.REVERSAL,
                        response.getReversalResponse().getResponse());
            }
        };
    }

    private static OriginalPOITransaction original(TransactionIdentificationType poiTxn) {
        return OriginalPOITransaction.builder()
                .poiTransactionID(poiTxn)
                .build();
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

    private static LoyaltyResult firstResult(LoyaltyResponse body) {
        return body.getLoyaltyResult() != null && body.getLoyaltyResult().length > 0
                ? body.getLoyaltyResult()[0] : null;
    }

    private static TransactionIdentificationType poiRef(com.bilt.pos.nexo.model.POIData poiData) {
        return poiData == null ? null : poiData.getPoiTransactionID();
    }

    private static Basket applyRebates(Basket basket, List<RedeemedRebate> rebates,
                                       BigDecimal totalRebate) {
        List<BasketLineItem> items = new ArrayList<>(basket.getItemCount());
        for (BasketLineItem line : basket.getItems()) {
            BigDecimal rebateAmount = BigDecimal.ZERO;
            String rebateLabel = null;
            for (RedeemedRebate rebate : rebates) {
                if (line.getItemId().equals(rebate.getItemId())) {
                    rebateAmount = rebateAmount.add(rebate.getAmount());
                    rebateLabel = rebate.getLabel();
                }
            }
            items.add(BasketLineItem.builder()
                    .itemId(line.getItemId())
                    .sku(line.getSku())
                    .description(line.getDescription())
                    .category(line.getCategory())
                    .quantity(line.getQuantity())
                    .unitPrice(line.getUnitPrice())
                    .originalTotal(line.getOriginalTotal())
                    .rebateAmount(rebateAmount)
                    .rebateLabel(rebateLabel)
                    .adjustedTotal(line.getOriginalTotal().subtract(rebateAmount))
                    .taxRate(line.getTaxRate())
                    .taxAmount(line.getTaxAmount())
                    .metadata(line.getMetadata())
                    .build());
        }
        return Basket.builder()
                .cartId(basket.getCartId())
                .items(items)
                .originalTotal(basket.getOriginalTotal())
                .taxTotal(basket.getTaxTotal())
                .grandTotal(basket.getGrandTotal())
                .rebateTotal(totalRebate)
                .build();
    }

    private static Basket withPaymentTotals(Basket basket, BigDecimal rebateTotal,
                                            BigDecimal pointsValue, BigDecimal storedValue,
                                            BigDecimal cardPayment) {
        return Basket.builder()
                .cartId(basket.getCartId())
                .items(basket.getItems())
                .originalTotal(basket.getOriginalTotal())
                .taxTotal(basket.getTaxTotal())
                .grandTotal(basket.getGrandTotal())
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
