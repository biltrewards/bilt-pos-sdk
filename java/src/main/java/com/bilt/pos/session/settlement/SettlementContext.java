/*
 *    ____  _ _ _
 *   | __ )(_) | |_
 *   |  _ \| | | __|
 *   | |_) | | | |_
 *   |____/|_|_|\__|
 *
 *   Bilt POS SDK
 */
package com.bilt.pos.session.settlement;

import com.bilt.pos.nexo.model.TransactionIdentificationType;
import com.bilt.pos.session.basket.Basket;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

/** Context handed to the {@code beforeStep} handler before each payment step. */
public final class SettlementContext {

  private final SettlementStep step;
  private final Basket currentBasket;
  private final BigDecimal currentTotal;
  private final BigDecimal stepAmount;
  private final String defaultTransactionId;
  private final List<CommittedStep> priorSteps;
  private final String serviceId;

  public SettlementContext(
      SettlementStep step,
      Basket currentBasket,
      BigDecimal currentTotal,
      String defaultTransactionId,
      List<CommittedStep> priorSteps,
      String serviceId) {
    this(
        step,
        currentBasket,
        currentTotal,
        currentTotal,
        defaultTransactionId,
        priorSteps,
        serviceId);
  }

  /**
   * A context whose step charges {@code stepAmount}, a share of {@code currentTotal}: a card
   * payment of a split settlement.
   */
  public SettlementContext(
      SettlementStep step,
      Basket currentBasket,
      BigDecimal currentTotal,
      BigDecimal stepAmount,
      String defaultTransactionId,
      List<CommittedStep> priorSteps,
      String serviceId) {
    this.step = step;
    this.currentBasket = currentBasket;
    this.currentTotal = currentTotal;
    this.stepAmount = stepAmount;
    this.defaultTransactionId = defaultTransactionId;
    this.priorSteps =
        Collections.unmodifiableList(
            new ArrayList<>(Objects.requireNonNull(priorSteps, "priorSteps")));
    this.serviceId = serviceId;
  }

  /**
   * Resolves the {@code SaleTransactionID} for a settlement step using the same contract as {@code
   * SettlementFlow.beforeStep}: the basket's own sale transaction is the default — every step,
   * retry, and reversal of one checkout shares it — the handler is called when registered, and an
   * override returning a different ID is stamped with the send time. The basket's transaction —
   * including its timestamp — is kept unchanged when the handler returns {@code null}, an empty
   * string, or the default ID itself, since nexo treats ID and timestamp as one identity.
   *
   * @param serviceId the {@code ServiceID} the step's request will be sent with, or {@code null}
   *     when the step sends no terminal request
   */
  public static TransactionIdentificationType resolveSaleTransactionId(
      SettlementStep step,
      Basket currentBasket,
      BigDecimal currentTotal,
      List<CommittedStep> priorSteps,
      String serviceId,
      Function<SettlementContext, String> handler) {
    return resolveSaleTransactionId(
        step,
        currentBasket,
        currentTotal,
        currentTotal,
        priorSteps,
        serviceId,
        handler,
        currentBasket.getSaleTransactionID());
  }

  /**
   * {@link #resolveSaleTransactionId(SettlementStep, Basket, BigDecimal, List, String, Function)}
   * for a step that charges {@code stepAmount} out of {@code currentTotal} (see {@link
   * #getStepAmount()}), with an explicit default transaction instead of the basket's: the handler
   * sees its ID as {@link #getDefaultTransactionId()}, and {@code null}, an empty string, or that
   * ID keep it, timestamp included. A split card payment passes its own share and the first card
   * payment's transaction, which nexo requires every split payment of a sale to share.
   */
  public static TransactionIdentificationType resolveSaleTransactionId(
      SettlementStep step,
      Basket currentBasket,
      BigDecimal currentTotal,
      BigDecimal stepAmount,
      List<CommittedStep> priorSteps,
      String serviceId,
      Function<SettlementContext, String> handler,
      TransactionIdentificationType defaultTransaction) {
    Objects.requireNonNull(defaultTransaction, "defaultTransaction");
    if (handler == null) {
      return defaultTransaction;
    }
    String transactionId =
        handler.apply(
            new SettlementContext(
                step,
                currentBasket,
                currentTotal,
                stepAmount,
                defaultTransaction.getTransactionID(),
                priorSteps,
                serviceId));
    return transactionId == null
            || transactionId.isEmpty()
            || transactionId.equals(defaultTransaction.getTransactionID())
        ? defaultTransaction
        : TransactionIdentificationType.builder()
            .transactionID(transactionId)
            .timeStamp(Instant.now().toString())
            .build();
  }

  /** The step about to run. */
  public SettlementStep getStep() {
    return step;
  }

  /** The basket as of this step (rebates applied once committed). */
  public Basket getCurrentBasket() {
    return currentBasket;
  }

  /**
   * The running total still due as of this step. For a card payment of a split settlement ({@code
   * SettlementOptions.cardPayments}), everything the card phase still owes, the same amount a
   * failure of the payment reports as {@code SettlementFailure.getAmountDue()}; {@link
   * #getStepAmount()} is that payment's own share.
   */
  public BigDecimal getCurrentTotal() {
    return currentTotal;
  }

  /**
   * What this step's request is about to charge. For a card payment of a split settlement, that
   * payment's own share of {@link #getCurrentTotal()}; for every other step, the same as {@link
   * #getCurrentTotal()}. Requested cashback comes on top of either.
   */
  public BigDecimal getStepAmount() {
    return stepAmount;
  }

  /** The {@code SaleTransactionID} used if the handler returns nothing else. */
  public String getDefaultTransactionId() {
    return defaultTransactionId;
  }

  /** Steps committed so far in this payment. */
  public List<CommittedStep> getPriorSteps() {
    return priorSteps;
  }

  /**
   * The Nexo {@code ServiceID} this step's terminal request will be sent with — minted for this
   * attempt before the handler runs, so the register can record it before anything is sent and
   * resolve the request with Transaction Status after a crash. Each retry of a step mints a new id
   * and calls the handler again. {@code null} for steps that send no terminal request ({@code
   * EXTERNAL_PAYMENT}, {@code EXTERNAL_REFUND}).
   */
  public String getServiceId() {
    return serviceId;
  }
}
