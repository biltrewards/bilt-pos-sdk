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
  private final String defaultTransactionId;
  private final List<CommittedStep> priorSteps;

  public SettlementContext(
      SettlementStep step,
      Basket currentBasket,
      BigDecimal currentTotal,
      String defaultTransactionId,
      List<CommittedStep> priorSteps) {
    this.step = step;
    this.currentBasket = currentBasket;
    this.currentTotal = currentTotal;
    this.defaultTransactionId = defaultTransactionId;
    this.priorSteps =
        Collections.unmodifiableList(
            new ArrayList<>(Objects.requireNonNull(priorSteps, "priorSteps")));
  }

  /**
   * Resolves the {@code SaleTransactionID} for a settlement step using the same contract as {@code
   * SettlementFlow.beforeStep}: the basket's own sale transaction is the default — every step,
   * retry, and reversal of one checkout shares it — the handler is called when registered, and an
   * override returning a different ID is stamped with the send time. The basket's transaction —
   * including its timestamp — is kept unchanged when the handler returns {@code null}, an empty
   * string, or the default ID itself, since nexo treats ID and timestamp as one identity.
   */
  public static TransactionIdentificationType resolveSaleTransactionId(
      SettlementStep step,
      Basket currentBasket,
      BigDecimal currentTotal,
      List<CommittedStep> priorSteps,
      Function<SettlementContext, String> handler) {
    TransactionIdentificationType defaultTransaction = currentBasket.getSaleTransactionID();
    if (handler == null) {
      return defaultTransaction;
    }
    String transactionId =
        handler.apply(
            new SettlementContext(
                step,
                currentBasket,
                currentTotal,
                defaultTransaction.getTransactionID(),
                priorSteps));
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

  /** The running total the step will be sent with. */
  public BigDecimal getCurrentTotal() {
    return currentTotal;
  }

  /** The {@code SaleTransactionID} used if the handler returns nothing else. */
  public String getDefaultTransactionId() {
    return defaultTransactionId;
  }

  /** Steps committed so far in this payment. */
  public List<CommittedStep> getPriorSteps() {
    return priorSteps;
  }
}
