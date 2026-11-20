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
import com.bilt.pos.nexo.model.TransactionIdentificationType;
import com.bilt.pos.session.Receipt;
import com.bilt.pos.session.RefundResult;
import com.bilt.pos.session.ReversalContext;
import com.bilt.pos.session.ReversalDecision;
import com.bilt.pos.session.ReversalStep;
import com.bilt.pos.session.ReversedMovement;
import com.bilt.pos.session.SessionError;
import com.bilt.pos.session.SessionErrorCode;
import com.bilt.pos.session.SessionException;
import com.bilt.pos.session.TenderAmount;
import com.bilt.pos.session.TenderMovement;
import com.bilt.pos.session.TenderType;
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
 * Refund and void flows. A void is a list of {@link ReversalMovement}s executed in order — money
 * legs as Nexo {@code ReversalRequest}s, loyalty legs as the matching {@code LoyaltyRequest} refund
 * types — and a refund is a {@code PaymentRequest(Refund)} followed by an award reversal when the
 * award reference is known. When a step fails, the caller-supplied {@link StepDecider} chooses
 * between retrying it, leaving it standing, or aborting the flow; already-reversed steps always
 * stand. A {@code null} decider selects the default policy (see {@link #defaultPolicy}).
 */
public final class ReversalManager {

  private static final Logger LOGGER = Logger.getLogger(ReversalManager.class.getName());

  private static final Map<ReversalStep, LoyaltyTransactionTypeEnum> LOYALTY_REFUND_TYPES =
      Map.of(
          ReversalStep.REDEMPTION,
          LoyaltyTransactionTypeEnum.REDEMPTION_REFUND,
          ReversalStep.REBATE,
          LoyaltyTransactionTypeEnum.REBATE_REFUND,
          ReversalStep.AWARD,
          LoyaltyTransactionTypeEnum.AWARD_REFUND);

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

  public ReversalManager(
      NexoExchange exchange, String currency, StoredValueManager storedValueManager) {
    this.exchange = exchange;
    this.currency = currency;
    this.storedValueManager = Objects.requireNonNull(storedValueManager, "storedValueManager");
  }

  // ─── Refund ───

  /**
   * Linked or unlinked refund: the tender refund, then — when the award reference of a linked
   * original is known — the award reversal.
   *
   * @param amount the amount to refund, or {@code null} for a full linked refund
   * @param saleItems the refunded items, attached to the refund's {@code PaymentTransaction}
   *     (magnitudes only — the payment type carries the direction), or {@code null} for an
   *     amount-only refund
   * @param originalPoiTxnId original transaction reference; {@code null} for an unlinked refund
   * @param awardPoiTxnId the original sale's award reference (linked refunds only), or {@code null}
   *     when no award is to be reversed
   * @param decider per-step failure resolution; {@code null} for the default policy (tender aborts,
   *     award is best-effort)
   * @param beforeStep told each leg's freshly minted {@code ServiceID} before it is sent, or {@code
   *     null}; not called for a first tender attempt sent with {@code refundServiceId}
   * @param onRefunded invoked the moment the tender refund completes, before the award reversal —
   *     the caller's void guard must rise as soon as money moves, even if a later step aborts the
   *     flow — and only then: a tender skipped by decision leaves the sale voidable
   * @param onAwardReversed invoked when the award reversal completes, so the caller records it as
   *     reversal progress — a later void must not re-credit it
   * @param refundServiceId the {@code ServiceID} the first tender attempt is sent with, already
   *     minted by {@link NexoMessageFactory#mintServiceId}, or {@code null}; every other attempt
   *     mints its own and tells {@code beforeStep}
   */
  public RefundResult refund(
      BigDecimal amount,
      List<SaleItem> saleItems,
      String originalPoiTxnId,
      Instant originalPoiTimestamp,
      String awardPoiTxnId,
      Instant awardPoiTimestamp,
      String memberId,
      TransactionIdentificationType saleTransaction,
      StepDecider decider,
      Consumer<ReversalContext> beforeStep,
      Runnable onRefunded,
      Runnable onAwardReversed,
      String refundServiceId) {
    return refund(
        amount,
        saleItems,
        List.of(
            new ReversalMovement(
                ReversalStep.CARD,
                originalPoiTxnId,
                originalPoiTimestamp,
                amount,
                TenderType.CARD)),
        awardPoiTxnId,
        awardPoiTimestamp,
        memberId,
        saleTransaction,
        decider,
        beforeStep,
        original -> onRefunded.run(),
        onAwardReversed,
        refundServiceId);
  }

  /**
   * Refund of several tender payments of one original sale — each its own {@code
   * PaymentRequest(Refund)}, sent in the given order — then the award reversal. A tender refund
   * that fails is resolved by {@code decider} like the single refund; one aborted after others
   * completed reports those in its error, and a skipped one leaves the others standing refunded.
   * The result lists every tender refund that completed, with its receipts and its amount by
   * tender; its single reference, approval code, and receipts describe the first, and its amount
   * sums every one.
   *
   * @param originals the original payments to refund, in the order to send them, each with the
   *     amount a full refund returns when known and its tender; an entry without a POI transaction
   *     ID is an unlinked refund. Must not be empty unless an award is to be reversed.
   * @param onRefunded told each original's POI transaction ID ({@code null} for an unlinked refund)
   *     the moment its tender refund completes
   * @see #refund(BigDecimal, List, String, Instant, String, Instant, String,
   *     TransactionIdentificationType, StepDecider, Consumer, Runnable, Runnable, String)
   */
  public RefundResult refund(
      BigDecimal amount,
      List<SaleItem> saleItems,
      List<ReversalMovement> originals,
      String awardPoiTxnId,
      Instant awardPoiTimestamp,
      String memberId,
      TransactionIdentificationType saleTransaction,
      StepDecider decider,
      Consumer<ReversalContext> beforeStep,
      Consumer<String> onRefunded,
      Runnable onAwardReversed,
      String refundServiceId) {
    // the tender refund anchors the flow, so the award is best-effort
    StepDecider effective = decider != null ? decider : defaultPolicy(true);
    SessionException[] lastFailure = new SessionException[1];
    String[] presetServiceId = {refundServiceId};
    List<PaymentResponse> refunded = new ArrayList<>();
    List<String> refundedOriginals = new ArrayList<>();
    List<TenderMovement> cardRefunds = new ArrayList<>();
    List<TenderAmount> byTender = new ArrayList<>();
    // the first refund's receipts, parsed once for its entry and the single-value fields
    Receipt[] firstReceipts = null;

    for (ReversalMovement original : originals) {
      String originalPoiTxnId = original.poiTransactionId;
      Instant originalPoiTimestamp = original.poiTransactionTimestamp;
      String[] sentServiceId = new String[1];
      PaymentResponse body =
          runStep(
              ReversalStep.CARD,
              effective,
              decider == null,
              () -> {
                String serviceId = presetServiceId[0];
                presetServiceId[0] = null;
                sentServiceId[0] =
                    serviceId != null
                        ? serviceId
                        : mintServiceId(ReversalStep.CARD, MessageCategoryType.PAYMENT, beforeStep);
                return sendRefund(
                    amount,
                    saleItems,
                    originalPoiTxnId,
                    originalPoiTimestamp,
                    saleTransaction,
                    sentServiceId[0]);
              },
              e -> {
                lastFailure[0] = e;
                LOGGER.warning(
                    "the tender refund"
                        + (originalPoiTxnId == null ? "" : " of " + originalPoiTxnId)
                        + " was skipped by decision; no money moved: "
                        + e.getError());
              },
              e ->
                  refundedOriginals.isEmpty()
                      ? e
                      : new SessionException(
                          Wire.annotated(
                              e.getError(),
                              "the tender refund of "
                                  + String.join(" and ", refundedOriginals)
                                  + " completed but "
                                  + originalPoiTxnId
                                  + " was not refunded: "
                                  + e.getError().getMessage(),
                              e)));
      if (body != null) {
        BigDecimal legAmount = refundedAmount(body, original.amount);
        Receipt customerReceipt = ReceiptMapper.customerReceipt(body.getPaymentReceipt());
        Receipt merchantReceipt = ReceiptMapper.merchantReceipt(body.getPaymentReceipt());
        if (firstReceipts == null) {
          firstReceipts = new Receipt[] {customerReceipt, merchantReceipt};
        }
        refunded.add(body);
        refundedOriginals.add(String.valueOf(originalPoiTxnId));
        byTender.add(new TenderAmount(original.tender, originalPoiTxnId, legAmount));
        if (original.tender == TenderType.CARD) {
          cardRefunds.add(
              TenderMovement.builder()
                  .tenderType(original.tender)
                  .originalPoiTransactionId(originalPoiTxnId)
                  .amount(legAmount)
                  .poiTransactionId(Wire.txnId(body.getPoiData()))
                  .poiTransactionTimestamp(Wire.txnTimestamp(body.getPoiData()))
                  .approvalCode(Wire.approvalCode(body))
                  .serviceId(sentServiceId[0])
                  .customerReceipt(customerReceipt)
                  .merchantReceipt(merchantReceipt)
                  .build());
        }
        onRefunded.accept(originalPoiTxnId);
      }
    }

    LoyaltyReversal loyalty = NO_REVERSAL;
    boolean awardReversed = false;
    if (awardPoiTxnId != null) {
      LoyaltyResponse awardLeg =
          runStep(
              ReversalStep.AWARD,
              effective,
              decider == null,
              () ->
                  loyaltyRefund(
                      LoyaltyTransactionTypeEnum.AWARD_REFUND,
                      awardPoiTxnId,
                      awardPoiTimestamp,
                      memberId,
                      saleTransaction,
                      mintServiceId(ReversalStep.AWARD, MessageCategoryType.LOYALTY, beforeStep)),
              e -> {
                lastFailure[0] = e;
                LOGGER.warning(
                    "AwardRefund failed (terminal may retry via " + "SAF): " + e.getError());
              },
              e ->
                  refunded.isEmpty()
                      ? e
                      : new SessionException(
                          Wire.annotated(
                              e.getError(),
                              "the tender refund completed but the award "
                                  + awardPoiTxnId
                                  + " was not reversed: "
                                  + e.getError().getMessage(),
                              e)));
      if (awardLeg != null) {
        loyalty = parseReversal(awardLeg);
        awardReversed = true;
        onAwardReversed.run();
      }
    }

    if (refunded.isEmpty()) {
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
    PaymentResponse first = refunded.get(0);
    return RefundResult.builder()
        .success(true)
        .refundedAmount(sum(byTender))
        .cardRefunds(cardRefunds)
        .refundedAmountByTender(byTender)
        .approvalCode(Wire.approvalCode(first))
        .poiTransactionId(Wire.txnId(first.getPoiData()))
        .poiTransactionTimestamp(Wire.txnTimestamp(first.getPoiData()))
        .customerReceipt(firstReceipts[0])
        .merchantReceipt(firstReceipts[1])
        .pointsReversed(loyalty.pointsReversed)
        .remainingPointBalance(loyalty.remainingBalance)
        .build();
  }

  /**
   * What one tender refund returned: the amount the terminal echoed, else {@code fallback} (the
   * requested amount, or for a full refund the original payment's), else {@code null}.
   */
  private static BigDecimal refundedAmount(PaymentResponse body, BigDecimal fallback) {
    Double authorized =
        body.getPaymentResult() != null && body.getPaymentResult().getAmountsResp() != null
            ? body.getPaymentResult().getAmountsResp().getAuthorizedAmount()
            : null;
    return authorized != null ? Wire.money(authorized) : fallback;
  }

  /**
   * The sum of the per-tender amounts; {@code null} when any is unknown, since a partial sum would
   * understate what was returned.
   */
  private static BigDecimal sum(List<TenderAmount> byTender) {
    BigDecimal sum = BigDecimal.ZERO;
    for (TenderAmount leg : byTender) {
      if (leg.getAmount() == null) {
        return null;
      }
      sum = sum.add(leg.getAmount());
    }
    return sum;
  }

  private PaymentResponse sendRefund(
      BigDecimal amount,
      List<SaleItem> saleItems,
      String originalPoiTxnId,
      Instant originalPoiTimestamp,
      TransactionIdentificationType saleTransaction,
      String serviceId) {
    AmountsReq.Builder amounts = AmountsReq.builder().currency(currency);
    if (amount != null) {
      amounts.requestedAmount(amount.doubleValue());
    }
    PaymentTransaction.Builder transaction =
        PaymentTransaction.builder().amountsReq(amounts.build());
    if (saleItems != null && !saleItems.isEmpty()) {
      transaction.saleItem(saleItems.toArray(new SaleItem[0]));
    }
    if (originalPoiTxnId != null) {
      transaction.originalPOITransaction(
          Wire.originalTransaction(originalPoiTxnId, originalPoiTimestamp));
    }
    SaleToPOIRequest request =
        SaleToPOIRequest.builder()
            .messageHeader(
                exchange
                    .factory()
                    .header(MessageClassType.SERVICE, MessageCategoryType.PAYMENT, serviceId))
            .paymentRequest(
                PaymentRequest.builder()
                    .saleData(exchange.factory().saleData(saleTransaction))
                    .paymentData(PaymentData.builder().paymentType(PaymentTypeEnum.REFUND).build())
                    .paymentTransaction(transaction.build())
                    .build())
            .build();

    SaleToPOIResponse response = exchange.sendExpectingBody(MessageCategoryType.PAYMENT, request);
    PaymentResponse body = response.getPaymentResponse();
    if (body == null) {
      throw Wire.missing("PaymentResponse");
    }
    exchange.requireSuccess(MessageCategoryType.PAYMENT, body.getResponse());
    return body;
  }

  /**
   * Runs a single loyalty refund movement, sent with {@code serviceId} (minted by {@link
   * NexoMessageFactory#mintServiceId}), and returns its parsed reversal result.
   */
  public VoidResult refundLoyalty(
      ReversalStep step,
      String originalPoiTxnId,
      Instant originalPoiTimestamp,
      String memberId,
      TransactionIdentificationType saleTransaction,
      String serviceId) {
    LoyaltyTransactionTypeEnum refundType = LOYALTY_REFUND_TYPES.get(step);
    if (refundType == null || isMoneyLeg(step)) {
      throw new IllegalArgumentException(step + " is not a loyalty refund movement");
    }
    LoyaltyResponse body =
        loyaltyRefund(
            refundType,
            originalPoiTxnId,
            originalPoiTimestamp,
            memberId,
            saleTransaction,
            serviceId);
    LoyaltyReversal reversal = parseReversal(body);
    return VoidResult.builder()
        .success(true)
        .reversedAmount(sumLoyaltyAmounts(body))
        .poiTransactionId(Wire.txnId(body.getPoiData()))
        .serviceId(serviceId)
        .poiTransactionTimestamp(Wire.txnTimestamp(body.getPoiData()))
        .pointsReversed(reversal.pointsReversed)
        .remainingPointBalance(reversal.remainingBalance)
        .build();
  }

  // ─── Void ───

  /**
   * Reverses the sale's movements in order, skipping the steps already recorded in {@code
   * reversedMovements} so a retried void resumes at the movements still standing. Each failed step
   * is resolved by the decider — retry, leave standing, or abort. A skipped money leg is honored
   * (the remaining steps still run) but leaves the void incomplete: after the loop the failure is
   * rethrown, since a standing money leg has no store-and-forward retry and a completed void would
   * strand the charge beyond a retry's reach. A step that reverses is added to {@code
   * reversedMovements}, and on abort the failure is annotated with what this attempt reversed so
   * the register can retry or escalate. If nothing of the sale was ever reversed — this attempt
   * skipped every step and no prior attempt made progress — the last failure is thrown rather than
   * reporting a void that changed nothing; once any movement stands reversed, an attempt that only
   * skips the remainder is a success, like a skip in a single-attempt void. The returned {@link
   * VoidResult} is built from THIS attempt's responses only — a resumed void does not restate the
   * amounts, references, or receipts of movements earlier attempts reversed. With nothing left
   * standing (empty target, or every movement already reversed) the void is a success.
   *
   * <p>{@code movements} must be the sale's WHOLE void target, not the filtered remainder: the
   * default policy judges "money-anchored" against it, so a retry that already reversed the money
   * leg is still the reversal of a money-anchored sale and its loyalty legs stay best-effort.
   */
  public VoidResult voidMovements(
      List<ReversalMovement> movements,
      String memberId,
      TransactionIdentificationType saleTransaction,
      StepDecider decider,
      Consumer<ReversalContext> beforeStep,
      Set<ReversalMovement.Key> reversedMovements) {
    StepDecider effective =
        decider != null
            ? decider
            : defaultPolicy(
                movements.stream().anyMatch(movement -> isMoneyLeg(movement.getStep())));
    boolean priorProgress = !reversedMovements.isEmpty();
    List<ReversalMovement> remaining =
        movements.stream()
            .filter(movement -> !reversedMovements.contains(movement.key()))
            .collect(Collectors.toList());
    if (remaining.isEmpty()) {
      return VoidResult.builder().success(true).build();
    }
    List<MoneyLeg> money = new ArrayList<>();
    Map<ReversalStep, LoyaltyResponse> loyalty = new EnumMap<>(ReversalStep.class);
    List<StoredValueOperationResult> storedValueLoads = new ArrayList<>();
    List<ReversedMovement> reversed = new ArrayList<>();
    List<ReversalMovement> skippedMoneyLegs = new ArrayList<>();
    SessionException[] lastFailure = new SessionException[1];
    SessionException[] moneyFailure = new SessionException[1];
    StepDecider progressAware =
        (step, error) ->
            effective.decide(step, Wire.withReversedMovements(error, List.copyOf(reversed)));

    for (ReversalMovement movement : remaining) {
      String sentServiceId =
          runStep(
              movement.getStep(),
              progressAware,
              decider == null,
              () -> {
                String serviceId =
                    mintServiceId(movement.getStep(), category(movement.getStep()), beforeStep);
                execute(
                    movement,
                    memberId,
                    saleTransaction,
                    money,
                    loyalty,
                    storedValueLoads,
                    serviceId);
                return serviceId;
              },
              e -> {
                lastFailure[0] = e;
                if (isMoneyLeg(movement.getStep())) {
                  skippedMoneyLegs.add(movement);
                  moneyFailure[0] = e;
                }
                LOGGER.warning(
                    movement.getStep()
                        + " reversal of "
                        + movement.poiTransactionId
                        + " skipped, the movement "
                        + "is still standing"
                        + (isMoneyLeg(movement.getStep())
                            ? ""
                            : " (terminal may retry loyalty via SAF)")
                        + ": "
                        + e.getError());
              },
              e -> abortError(reversed, movement, e));
      if (sentServiceId != null) {
        reversed.add(
            new ReversedMovement(movement.getStep(), movement.poiTransactionId, sentServiceId));
        reversedMovements.add(movement.key());
      }
    }
    if (!skippedMoneyLegs.isEmpty()) {
      // the skip was honored — the remaining legs ran — but a
      // standing money leg has no store-and-forward retry, and a
      // completed void would strand the charge, so the void stays
      // incomplete and retryable
      throw standingMoneyError(skippedMoneyLegs, reversed, moneyFailure[0]);
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
    return buildVoidResult(money, loyalty, storedValueLoads, reversed);
  }

  /**
   * A void whose money leg was skipped and remains charged: the legs reversed this attempt stand
   * recorded, and the retry sends only what is still standing.
   */
  private static SessionException standingMoneyError(
      List<ReversalMovement> skipped, List<ReversedMovement> reversed, SessionException cause) {
    String legs =
        skipped.stream()
            .map(movement -> label(movement.getStep()) + " " + movement.poiTransactionId)
            .collect(Collectors.joining(" and "));
    return new SessionException(
        Wire.annotated(
            cause.getError(),
            legs
                + (skipped.size() == 1 ? " was skipped and remains" : " were skipped and remain")
                + " charged — the void is incomplete; retry voidTransaction() to "
                + "reverse "
                + (skipped.size() == 1 ? "it" : "them")
                + ": "
                + cause.getError().getMessage(),
            cause,
            List.copyOf(reversed)));
  }

  private void execute(
      ReversalMovement movement,
      String memberId,
      TransactionIdentificationType saleTransaction,
      List<MoneyLeg> money,
      Map<ReversalStep, LoyaltyResponse> loyalty,
      List<StoredValueOperationResult> storedValueLoads,
      String serviceId) {
    ReversalStep step = movement.getStep();
    if (step == ReversalStep.STORED_VALUE_LOAD) {
      storedValueLoads.add(
          storedValueManager.operation(
              StoredValueTransactionTypeEnum.REVERSE,
              null,
              null,
              movement.poiTransactionId,
              movement.poiTransactionTimestamp,
              saleTransaction,
              serviceId));
      return;
    }
    if (isMoneyLeg(step)) {
      ReversalResponse response =
          reverse(
              movement.poiTransactionId,
              movement.poiTransactionTimestamp,
              saleTransaction,
              serviceId);
      // a sale may hold several card payments, each its own reversal
      money.add(new MoneyLeg(movement, response, serviceId));
      return;
    }
    LoyaltyTransactionTypeEnum refundType = LOYALTY_REFUND_TYPES.get(step);
    if (refundType == null) {
      throw new IllegalArgumentException(step + " is not a void movement");
    }
    loyalty.put(
        step,
        loyaltyRefund(
            refundType,
            movement.poiTransactionId,
            movement.poiTransactionTimestamp,
            memberId,
            saleTransaction,
            serviceId));
  }

  /** One tender reversal this attempt sent: the movement, the terminal's answer, its ServiceID. */
  private static final class MoneyLeg {
    final ReversalMovement movement;
    final ReversalResponse response;
    final String serviceId;
    // parsed once: the leg's entry and the result's single-value fields share them
    final Receipt customerReceipt;
    final Receipt merchantReceipt;

    MoneyLeg(ReversalMovement movement, ReversalResponse response, String serviceId) {
      this.movement = movement;
      this.response = response;
      this.serviceId = serviceId;
      this.customerReceipt = ReceiptMapper.customerReceipt(response.getPaymentReceipt());
      this.merchantReceipt = ReceiptMapper.merchantReceipt(response.getPaymentReceipt());
    }

    /** What the reversal returned: as echoed, else the original movement's amount, else null. */
    BigDecimal amount() {
      return response.getReversedAmount() != null
          ? Wire.money(response.getReversedAmount())
          : movement.amount;
    }
  }

  /**
   * The void's outcome. Every tender leg reversed is listed by tender, and each card payment with
   * its receipts; the amount sums the tender legs. The single reference and receipts describe the
   * first tender leg this attempt reversed — the newest card payment, else the stored value leg.
   */
  private static VoidResult buildVoidResult(
      List<MoneyLeg> money,
      Map<ReversalStep, LoyaltyResponse> loyalty,
      List<StoredValueOperationResult> storedValueLoads,
      List<ReversedMovement> reversed) {
    LoyaltyResponse awardLeg = loyalty.get(ReversalStep.AWARD);
    LoyaltyReversal points = awardLeg == null ? NO_REVERSAL : parseReversal(awardLeg);

    List<TenderAmount> byTender = new ArrayList<>();
    List<TenderMovement> cardReversals = new ArrayList<>();
    TenderMovement storedValueReversal = null;
    for (MoneyLeg leg : money) {
      BigDecimal amount = leg.amount();
      byTender.add(new TenderAmount(leg.movement.tender, leg.movement.poiTransactionId, amount));
      TenderMovement movement =
          TenderMovement.builder()
              .tenderType(leg.movement.tender)
              .originalPoiTransactionId(leg.movement.poiTransactionId)
              .amount(amount)
              .poiTransactionId(Wire.txnId(leg.response.getPoiData()))
              .poiTransactionTimestamp(Wire.txnTimestamp(leg.response.getPoiData()))
              .serviceId(leg.serviceId)
              .customerReceipt(leg.customerReceipt)
              .merchantReceipt(leg.merchantReceipt)
              .build();
      if (leg.movement.tender == TenderType.CARD) {
        cardReversals.add(movement);
      } else {
        // a sale has at most one stored value payment
        storedValueReversal = movement;
      }
    }

    // the reversed amount is what the tender legs return; a void with no
    // tender legs reports the monetary value of the loyalty refunds
    BigDecimal loyaltyAmount =
        sumLoyaltyAmounts(loyalty.get(ReversalStep.REDEMPTION), loyalty.get(ReversalStep.REBATE));
    BigDecimal reversedAmount =
        !money.isEmpty()
            ? sum(byTender)
            : loyaltyAmount != null && loyaltyAmount.signum() != 0
                ? loyaltyAmount
                : sumStoredValueAmounts(storedValueLoads);

    VoidResult.Builder result =
        VoidResult.builder()
            .success(true)
            .reversedAmount(reversedAmount)
            .cardReversals(cardReversals)
            .storedValueReversal(storedValueReversal)
            .reversedAmountByTender(byTender)
            .pointsReversed(points.pointsReversed)
            .remainingPointBalance(points.remainingBalance);

    if (!money.isEmpty()) {
      MoneyLeg primary = money.get(0);
      result
          .poiTransactionId(Wire.txnId(primary.response.getPoiData()))
          .poiTransactionTimestamp(Wire.txnTimestamp(primary.response.getPoiData()))
          .serviceId(primary.serviceId)
          .customerReceipt(primary.customerReceipt)
          .merchantReceipt(primary.merchantReceipt);
    } else if (!loyalty.isEmpty()) {
      ReversalStep primaryStep = loyalty.keySet().iterator().next();
      LoyaltyResponse primary = loyalty.get(primaryStep);
      result
          .poiTransactionId(Wire.txnId(primary.getPoiData()))
          .poiTransactionTimestamp(Wire.txnTimestamp(primary.getPoiData()))
          .serviceId(serviceIdOf(reversed, primaryStep));
    } else if (!storedValueLoads.isEmpty()) {
      StoredValueOperationResult primary = storedValueLoads.get(0);
      result
          .poiTransactionId(primary.getPoiTransactionId())
          .poiTransactionTimestamp(primary.getPoiTransactionTimestamp())
          .serviceId(serviceIdOf(reversed, ReversalStep.STORED_VALUE_LOAD));
    }
    return result.build();
  }

  /** The {@code ServiceID} of this attempt's first reversed movement of {@code step}. */
  private static String serviceIdOf(List<ReversedMovement> reversed, ReversalStep step) {
    for (ReversedMovement movement : reversed) {
      if (movement.getStep() == step) {
        return movement.getServiceId();
      }
    }
    return null;
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
   * Runs one reversal step under the decider: {@code RETRY} re-sends, {@code SKIP} invokes {@code
   * onSkip} and returns {@code null}, {@code ABORT} (or a {@code null} decision) throws via {@code
   * onAbort}. Under the default policy ({@code defaulted}) a leg that failed before it was sent
   * aborts instead of skipping: the terminal never saw it, so store-and-forward cannot finish it.
   */
  private static <T> T runStep(
      ReversalStep step,
      StepDecider decider,
      boolean defaulted,
      Supplier<T> action,
      Consumer<SessionException> onSkip,
      UnaryOperator<SessionException> onAbort) {
    while (true) {
      SessionException failure;
      boolean sent = true;
      try {
        return action.get();
      } catch (NotSent e) {
        failure = e.failure;
        sent = false;
      } catch (SessionException e) {
        failure = e;
      }
      ReversalDecision decision = decider.decide(step, failure.getError());
      if (decision == ReversalDecision.RETRY) {
        continue;
      }
      if (decision == ReversalDecision.SKIP && (sent || !defaulted)) {
        onSkip.accept(failure);
        return null;
      }
      throw onAbort.apply(failure);
    }
  }

  /** A leg's failure before its request was sent (minting or {@code beforeStep}). */
  private static final class NotSent extends RuntimeException {
    private static final long serialVersionUID = 1L;
    final transient SessionException failure;

    NotSent(SessionException failure) {
      super(failure);
      this.failure = failure;
    }
  }

  /**
   * The default decision policy, stated once beside the mechanism: money legs abort; loyalty
   * movements are best-effort when a money leg anchors the reversal (the terminal can retry them
   * via store-and-forward), strict when they are its substance.
   */
  private static StepDecider defaultPolicy(boolean moneyAnchored) {
    return (step, error) -> {
      switch (step) {
        case REDEMPTION:
        case REBATE:
        case AWARD:
          return moneyAnchored ? ReversalDecision.SKIP : ReversalDecision.ABORT;
        default: // Stored value load, card, or stored value tender
          return ReversalDecision.ABORT;
      }
    };
  }

  /**
   * Mints the leg's {@code ServiceID} and hands it to {@code beforeStep} before anything is sent. A
   * throwing handler fails the leg like a terminal failure, so the decider resolves it.
   */
  private String mintServiceId(
      ReversalStep step, MessageCategoryType category, Consumer<ReversalContext> beforeStep) {
    try {
      String serviceId = exchange.factory().mintServiceId(category);
      if (beforeStep != null) {
        beforeStep.accept(new ReversalContext(step, serviceId));
      }
      return serviceId;
    } catch (SessionException e) {
      throw new NotSent(e);
    } catch (RuntimeException e) {
      throw new NotSent(
          new SessionException(
              new SessionError(
                  SessionErrorCode.UNKNOWN, "beforeStep failed for " + step + ": " + e, null, e)));
    }
  }

  /** The category of the wire request a void leg sends. */
  private static MessageCategoryType category(ReversalStep step) {
    switch (step) {
      case STORED_VALUE_LOAD:
        return MessageCategoryType.STORED_VALUE;
      case CARD:
      case STORED_VALUE:
        return MessageCategoryType.REVERSAL;
      default: // Redemption, rebate, or award refund
        return MessageCategoryType.LOYALTY;
    }
  }

  private static boolean isMoneyLeg(ReversalStep step) {
    return step == ReversalStep.STORED_VALUE_LOAD
        || step == ReversalStep.CARD
        || step == ReversalStep.STORED_VALUE;
  }

  /**
   * A reversal aborted between steps: surface exactly what was reversed and what remains standing
   * so the register can retry (reversed steps are skipped on retry) or escalate.
   */
  private static SessionException abortError(
      List<ReversedMovement> reversed, ReversalMovement failed, SessionException cause) {
    if (reversed.isEmpty()) {
      return cause;
    }
    String done =
        reversed.stream()
            .map(movement -> label(movement.getStep()) + " " + movement.getPoiTransactionId())
            .collect(Collectors.joining(" and "));
    return new SessionException(
        Wire.annotated(
            cause.getError(),
            done
                + (reversed.size() == 1 ? " was" : " were")
                + " reversed but "
                + label(failed.getStep())
                + " "
                + failed.poiTransactionId
                + " was not: "
                + cause.getError().getMessage(),
            cause,
            List.copyOf(reversed)));
  }

  private static String label(ReversalStep step) {
    switch (step) {
      case STORED_VALUE_LOAD:
        return "the stored value load";
      case STORED_VALUE:
        return "the stored value leg";
      case REDEMPTION:
        return "the redemption";
      case REBATE:
        return "the rebate";
      case AWARD:
        return "the award";
      default:
        return "the card leg";
    }
  }

  // ─── Wire verbs ───

  /** A loyalty refund against the movement's own POI reference, throwing on failure. */
  private LoyaltyResponse loyaltyRefund(
      LoyaltyTransactionTypeEnum refundType,
      String originalPoiTxnId,
      Instant originalPoiTimestamp,
      String memberId,
      TransactionIdentificationType saleTransaction,
      String serviceId) {
    SaleToPOIRequest request =
        exchange
            .factory()
            .loyaltyRefundRequest(
                refundType,
                Wire.originalTransaction(originalPoiTxnId, originalPoiTimestamp),
                memberId,
                saleTransaction,
                serviceId);
    SaleToPOIResponse response = exchange.sendExpectingBody(MessageCategoryType.LOYALTY, request);
    LoyaltyResponse body = response.getLoyaltyResponse();
    if (body == null) {
      throw Wire.missing("LoyaltyResponse");
    }
    exchange.requireSuccess(MessageCategoryType.LOYALTY, body.getResponse());
    return body;
  }

  private ReversalResponse reverse(
      String originalPoiTxnId,
      Instant originalPoiTimestamp,
      TransactionIdentificationType saleTransaction,
      String serviceId) {
    SaleToPOIRequest request =
        exchange
            .factory()
            .reversalRequest(
                Wire.originalTransaction(originalPoiTxnId, originalPoiTimestamp),
                saleTransaction,
                serviceId);
    SaleToPOIResponse response = exchange.sendExpectingBody(MessageCategoryType.REVERSAL, request);
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
    int points =
        result.getLoyaltyAmount() == null
            ? 0
            : (int) Math.round(result.getLoyaltyAmount().getAmountValue());
    int balance =
        result.getCurrentBalance() == null ? 0 : (int) Math.round(result.getCurrentBalance());
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
}
