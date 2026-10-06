/*
 *    ____  _ _ _
 *   | __ )(_) | |_
 *   |  _ \| | | __|
 *   | |_) | | | |_
 *   |____/|_|_|\__|
 *
 *   Bilt POS SDK
 */
package com.bilt.pos.host.internal;

import com.bilt.pos.display.DisplayPayload;
import com.bilt.pos.host.StepDeadlines;
import com.bilt.pos.session.ReversalDecision;
import com.bilt.pos.session.ReversalFlow;
import com.bilt.pos.session.ReversalStep;
import com.bilt.pos.session.SessionError;
import com.bilt.pos.session.SessionErrorCode;
import com.bilt.pos.session.SessionException;
import com.bilt.pos.session.SessionResult;
import com.bilt.pos.session.SettlementFlow;
import com.bilt.pos.session.TerminalShopperSession;
import com.bilt.pos.session.basket.Basket;
import com.bilt.pos.session.identity.Member;
import com.bilt.pos.session.settlement.OriginalSaleRecord;
import com.bilt.pos.session.settlement.SettlementContext;
import com.bilt.pos.session.settlement.SettlementFailure;
import com.bilt.pos.session.settlement.SettlementOptions;
import com.bilt.pos.session.settlement.SettlementRecovery;
import com.bilt.pos.session.storedvalue.StoredValueCard;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.math.BigDecimal;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Turns {@code POST .../operations} bodies into the SDK's lazy operations and runs them.
 *
 * <p>Each operation is parsed up front, so a bad request is a 400 before anything is queued, and
 * launched only when it reaches the head of the session's lane. The launch creates the SDK
 * operation, registers the handlers that bridge callbacks to steps and events, calls {@code
 * execute()} so the SDK's own ordering and callback rules hold, and then waits on {@code get()}
 * from a host worker thread to learn the outcome: that is the one place the SDK reports every
 * failure path, aborted settlements included.
 *
 * <p>Step handlers run on the SDK's flow thread (the session has no callback executor, so the SDK
 * dispatches inline). They publish {@code operation.step}, park on a {@link PendingStep} and return
 * whatever {@code /reply}, an abort or the deadline resolved. Only the step kinds the request
 * declared in {@code handledSteps} are asked; the rest take the SDK default at once, which is what
 * Java does when no handler is registered.
 */
public final class OperationRunner {

  private final StepDeadlines deadlines;

  public OperationRunner(StepDeadlines deadlines) {
    this.deadlines = deadlines;
  }

  /** Parses the body, registers the operation on the session and returns it. */
  public HostedOperation submit(HostedSession hosted, ObjectNode body) {
    String type = Json.requireText(body, "type");
    if ("end".equals(type) || "forceEnd".equals(type)) {
      throw HostError.badRequest(type + " has its own endpoint and is not an operation request");
    }
    boolean ordered = !"updateInputDisplay".equals(type);
    HostedOperation operation = new HostedOperation(type, ordered);
    Runnable launch = launch(hosted, operation, type, body);
    hosted.submit(operation, launch);
    return operation;
  }

  /**
   * Answers a parked step. 422 when the operation is not waiting, the step ID is stale or the step
   * was already answered; 400 when the answer does not fit the step kind.
   */
  public void reply(HostedOperation operation, ObjectNode body) {
    String stepId = Json.requireText(body, "stepId");
    PendingStep<?> step = operation.pendingStep();
    if (step == null || operation.status() != HostedOperation.Status.AWAITING_REPLY) {
      throw HostError.notPending("operation " + operation.id() + " is not awaiting a reply");
    }
    if (!step.stepId().equals(stepId)) {
      throw HostError.notPending("step " + stepId + " is not the pending step of this operation");
    }
    if (!step.reply(body)) {
      throw HostError.notPending("step " + stepId + " was already answered");
    }
  }

  /**
   * Aborts an operation: a queued one is dropped, a parked step is answered the way an abort would,
   * and a terminal session's in-flight exchange is cancelled through the SDK's unordered {@code
   * abort()}. Returns false when the operation had already completed and nothing was done.
   */
  public boolean abort(HostedSession hosted, HostedOperation operation) {
    if (operation.isTerminal()) {
      return false;
    }
    if (!operation.isAbortable()) {
      throw HostError.conflict(operation.type() + " is never the abort's target");
    }
    if (operation.status() == HostedOperation.Status.QUEUED && hosted.dequeue(operation)) {
      operation.failed(
          HostError.of(
              new SessionError(
                  SessionErrorCode.ABORTED, "the operation was aborted before it started")),
          true);
      hosted.completed(operation);
      return true;
    }
    TerminalShopperSession terminal = hosted.terminal();
    if (terminal != null && operation.ordered()) {
      // execute() only queues the abort; the step is released below, so wait for the flag to be
      // raised first or the settlement can run on and charge the card
      SessionResult<Void> terminalAbort = terminal.abort();
      terminalAbort.execute();
      terminalAbort.isSuccess();
    }
    PendingStep<?> step = operation.pendingStep();
    if (step != null) {
      step.abort();
    }
    return true;
  }

  // ─── Launch construction ───

  private Runnable launch(
      HostedSession hosted, HostedOperation operation, String type, ObjectNode body) {
    TerminalShopperSession terminal = hosted.terminal();
    if (terminal == null) {
      throw HostError.unsupported(
          "operation '" + type + "' needs a terminal session; this session is local");
    }
    JsonNode options = body.get("options");
    switch (type) {
      case "identifyMember":
        {
          Member pending =
              Json.has(body, "resolver") ? Parsers.pendingMember(body.get("resolver")) : null;
          var identifyOptions = Parsers.identifyOptions(options);
          return result(
              hosted,
              operation,
              () ->
                  pending == null
                      ? terminal.identifyMember(identifyOptions)
                      : terminal.identifyMember(pending),
              Views::identifyResult);
        }
      case "acquireCard":
        {
          var acquisitionOptions = Parsers.cardAcquisitionOptions(options);
          return result(
              hosted,
              operation,
              () -> terminal.acquireCard(acquisitionOptions),
              Views::cardAcquisition);
        }
      case "requestDigitString":
        {
          String prompt = Json.requireText(body, "prompt");
          var inputOptions = Parsers.inputOptions(options);
          return result(
              hosted,
              operation,
              () -> terminal.requestDigitString(prompt, inputOptions),
              Views::text);
        }
      case "requestDecimalString":
        {
          String prompt = Json.requireText(body, "prompt");
          var inputOptions = Parsers.inputOptions(options);
          return result(
              hosted,
              operation,
              () -> terminal.requestDecimalString(prompt, inputOptions),
              Views::money);
        }
      case "requestTextString":
        {
          String prompt = Json.requireText(body, "prompt");
          var inputOptions = Parsers.inputOptions(options);
          return result(
              hosted,
              operation,
              () -> terminal.requestTextString(prompt, inputOptions),
              Views::text);
        }
      case "requestConfirmation":
        {
          String prompt = Json.requireText(body, "prompt");
          var confirmationOptions = Parsers.confirmationOptions(options);
          return result(
              hosted,
              operation,
              () -> terminal.requestConfirmation(prompt, confirmationOptions),
              Views::bool);
        }
      case "requestMenuEntry":
        {
          String prompt = Json.requireText(body, "prompt");
          List<String> entries = Json.strings(body, "entries");
          if (entries == null || entries.isEmpty()) {
            throw HostError.badRequest("entries must not be empty");
          }
          var menuOptions = Parsers.menuOptions(options);
          return result(
              hosted,
              operation,
              () -> terminal.requestMenuEntry(prompt, entries, menuOptions),
              Views::menuSelection);
        }
      case "requestSignature":
        {
          String prompt = Json.requireText(body, "prompt");
          return result(
              hosted, operation, () -> terminal.requestSignature(prompt), Views::signature);
        }
      case "requestAmountConfirmation":
        {
          BigDecimal amount = Json.requireDecimal(body, "amount");
          String prompt = Json.requireText(body, "prompt");
          return result(
              hosted,
              operation,
              () -> terminal.requestAmountConfirmation(amount, prompt),
              Views::bool);
        }
      case "requestPinEntry":
        {
          var pinOptions = Parsers.pinOptions(options);
          return result(
              hosted, operation, () -> terminal.requestPinEntry(pinOptions), Views::pinResult);
        }
      case "requestPinVerify":
        {
          var pinOptions = Parsers.pinOptions(options);
          return result(
              hosted, operation, () -> terminal.requestPinVerify(pinOptions), Views::pinResult);
        }
      case "requestPinVerifyOnly":
        {
          var pinOptions = Parsers.pinOptions(options);
          return result(
              hosted, operation, () -> terminal.requestPinVerifyOnly(pinOptions), Views::pinResult);
        }
      case "setStoredValueCard":
        {
          if (!body.has("card")) {
            throw HostError.badRequest("card is required (null clears the tender)");
          }
          StoredValueCard card =
              body.get("card").isNull() ? null : Parsers.storedValueCard(body.get("card"), "card");
          return immediate(hosted, operation, () -> terminal.setStoredValueCard(card));
        }
      case "storedValueBalance":
        {
          StoredValueCard card = Parsers.storedValueCard(body.get("card"), "card");
          return result(
              hosted,
              operation,
              () -> terminal.storedValueBalance(card),
              Views::storedValueBalance);
        }
      case "storedValueActivate":
        {
          StoredValueCard card = Parsers.storedValueCard(body.get("card"), "card");
          BigDecimal amount = Json.requireDecimal(body, "amount");
          return result(
              hosted,
              operation,
              () -> terminal.storedValueActivate(card, amount),
              Views::storedValueOperation);
        }
      case "storedValueLoad":
        {
          StoredValueCard card = Parsers.storedValueCard(body.get("card"), "card");
          BigDecimal amount = Json.requireDecimal(body, "amount");
          return result(
              hosted,
              operation,
              () -> terminal.storedValueLoad(card, amount),
              Views::storedValueOperation);
        }
      case "storedValueUnload":
        {
          StoredValueCard card = Parsers.storedValueCard(body.get("card"), "card");
          BigDecimal amount = Json.requireDecimal(body, "amount");
          return result(
              hosted,
              operation,
              () -> terminal.storedValueUnload(card, amount),
              Views::storedValueOperation);
        }
      case "storedValueReserve":
        {
          StoredValueCard card = Parsers.storedValueCard(body.get("card"), "card");
          BigDecimal amount = Json.requireDecimal(body, "amount");
          return result(
              hosted,
              operation,
              () -> terminal.storedValueReserve(card, amount),
              Views::storedValueOperation);
        }
      case "storedValueDeactivate":
        {
          StoredValueCard card = Parsers.storedValueCard(body.get("card"), "card");
          return result(
              hosted,
              operation,
              () -> terminal.storedValueDeactivate(card),
              Views::storedValueOperation);
        }
      case "storedValueDuplicate":
        {
          StoredValueCard card = Parsers.storedValueCard(body.get("card"), "card");
          return result(
              hosted,
              operation,
              () -> terminal.storedValueDuplicate(card),
              Views::storedValueOperation);
        }
      case "storedValueReverse":
        {
          String poiTransactionId = Json.requireText(body, "originalPoiTransactionId");
          var timestamp = Json.instant(body, "originalPoiTransactionTimestamp");
          return result(
              hosted,
              operation,
              () -> terminal.storedValueReverse(poiTransactionId, timestamp),
              Views::storedValueOperation);
        }
      case "settle":
        {
          SettlementOptions settlementOptions = Parsers.settlementOptions(options);
          Set<PendingStep.Kind> steps = handledSteps(body);
          return settlement(hosted, operation, () -> terminal.settle(settlementOptions), steps);
        }
      case "refund":
        {
          BigDecimal amount = Json.decimal(body, "amount");
          Set<PendingStep.Kind> steps = handledSteps(body);
          return reversal(
              hosted,
              operation,
              () -> amount == null ? terminal.refund() : terminal.refund(amount),
              Views::refundResult,
              steps,
              true);
        }
      case "refundUnlinked":
        {
          BigDecimal amount = Json.requireDecimal(body, "amount");
          Set<PendingStep.Kind> steps = handledSteps(body);
          return reversal(
              hosted,
              operation,
              () -> terminal.refundUnlinked(amount),
              Views::refundResult,
              steps,
              true);
        }
      case "voidTransaction":
        {
          OriginalSaleRecord originalSale =
              Json.has(body, "originalSale")
                  ? Parsers.originalSale(body.get("originalSale"))
                  : null;
          Set<PendingStep.Kind> steps = handledSteps(body);
          return reversal(
              hosted,
              operation,
              () ->
                  originalSale == null
                      ? terminal.voidTransaction()
                      : terminal.voidTransaction(originalSale),
              Views::voidResult,
              steps,
              moneyAnchored(originalSale));
        }
      case "getTransactionStatus":
        {
          String serviceId = Json.requireText(body, "originalServiceId");
          var statusOptions = Parsers.transactionStatusOptions(options);
          return result(
              hosted,
              operation,
              () -> terminal.getTransactionStatus(serviceId, statusOptions),
              Views::transactionStatus);
        }
      case "updateDisplay":
        {
          boolean hasBasket = Json.has(body, "basket");
          boolean hasDisplay = Json.has(body, "display");
          if (hasBasket == hasDisplay) {
            throw HostError.badRequest("updateDisplay takes exactly one of basket or display");
          }
          if (hasBasket) {
            Basket basket = Parsers.basket(body.get("basket"));
            return result(hosted, operation, () -> terminal.updateDisplay(basket), v -> null);
          }
          DisplayPayload display = Parsers.displayPayload(body.get("display"), "display");
          return result(hosted, operation, () -> terminal.updateDisplay(display), v -> null);
        }
      case "updateInputDisplay":
        {
          DisplayPayload display = Parsers.displayPayload(body.get("display"), "display");
          return result(hosted, operation, () -> terminal.updateInputDisplay(display), v -> null);
        }
      default:
        throw HostError.badRequest("unknown operation type '" + type + "'");
    }
  }

  private static Set<PendingStep.Kind> handledSteps(ObjectNode body) {
    List<String> names = Json.strings(body, "handledSteps");
    EnumSet<PendingStep.Kind> steps = EnumSet.noneOf(PendingStep.Kind.class);
    if (names != null) {
      for (String name : names) {
        steps.add(Json.enumValue(name, "handledSteps", PendingStep.Kind.class));
      }
    }
    return steps;
  }

  /**
   * Whether a void's loyalty legs ride along with a money leg, which is what decides the SDK's
   * default reversal decision. A prior-sale record says so itself; a same-session void is taken to
   * be money-anchored, which is the common case.
   */
  private static boolean moneyAnchored(OriginalSaleRecord originalSale) {
    if (originalSale == null) {
      return true;
    }
    return originalSale.getCardPoiTransactionId() != null
        || originalSale.getStoredValuePoiTransactionId() != null
        || !originalSale.getStoredValueLoads().isEmpty();
  }

  // ─── Launches ───

  /** A {@code SessionResult} operation: execute, then learn the outcome from {@code get()}. */
  private <T> Runnable result(
      HostedSession hosted,
      HostedOperation operation,
      Supplier<SessionResult<T>> factory,
      Function<T, JsonNode> view) {
    return () -> {
      try {
        SessionResult<T> result = factory.get();
        result.execute();
        operation.succeeded(view.apply(result.get()));
      } catch (Throwable failure) {
        fail(operation, failure);
      } finally {
        hosted.completed(operation);
      }
    };
  }

  /** An SDK call that is synchronous in Java but still an operation on the wire. */
  private Runnable immediate(HostedSession hosted, HostedOperation operation, Runnable work) {
    return () -> {
      try {
        work.run();
        operation.succeeded(null);
      } catch (Throwable failure) {
        fail(operation, failure);
      } finally {
        hosted.completed(operation);
      }
    };
  }

  private Runnable settlement(
      HostedSession hosted,
      HostedOperation operation,
      Supplier<SettlementFlow> factory,
      Set<PendingStep.Kind> steps) {
    return () -> {
      try {
        SettlementFlow flow = factory.get();
        if (steps.contains(PendingStep.Kind.BEFORE_STEP)) {
          flow.beforeStep(context -> ask(hosted, operation, beforeStep(operation, context)));
        }
        if (steps.contains(PendingStep.Kind.TOTAL_REQUIRED)) {
          flow.onRebatesRedeemed(
              rebates ->
                  ask(
                      hosted,
                      operation,
                      totalStep(
                          operation,
                          "REBATE_REDEMPTION",
                          "rebates",
                          Views.rebatesRedeemed(rebates),
                          rebates.getSuggestedTotal())));
          flow.onPointsRedeemed(
              points ->
                  ask(
                      hosted,
                      operation,
                      totalStep(
                          operation,
                          "POINT_REDEMPTION",
                          "points",
                          Views.pointsRedeemed(points),
                          points.getSuggestedTotal())));
          flow.onGiftCardPayment(
              giftCard ->
                  ask(
                      hosted,
                      operation,
                      totalStep(
                          operation,
                          "STORED_VALUE_CHARGE",
                          "giftCard",
                          Views.giftCardPayment(giftCard),
                          giftCard.getSuggestedTotal())));
        }
        flow.onMovement(
            movement -> {
              ObjectNode payload = Json.object();
              payload.put("operationId", operation.id());
              payload.set("movement", Views.movement(movement));
              hosted.publish("operation.movement", payload);
            });
        if (steps.contains(PendingStep.Kind.RECOVERY_REQUIRED)) {
          // a failure without a step is the SDK's final notification after the fact, not a
          // question; only charge-side failures mid-flight ask for a recovery
          flow.onError(
              failure ->
                  failure.getStep() == null
                      ? SettlementRecovery.abort()
                      : ask(hosted, operation, recoveryStep(operation, failure)));
        }
        flow.onAbandoned(record -> operation.abandoned(Views.abandonedSettlement(record)));
        flow.execute();
        operation.succeeded(Views.settlementResult(flow.get()));
      } catch (Throwable failure) {
        fail(operation, failure);
      } finally {
        hosted.completed(operation);
      }
    };
  }

  private <T> Runnable reversal(
      HostedSession hosted,
      HostedOperation operation,
      Supplier<ReversalFlow<T>> factory,
      Function<T, JsonNode> view,
      Set<PendingStep.Kind> steps,
      boolean moneyAnchored) {
    return () -> {
      try {
        ReversalFlow<T> flow = factory.get();
        if (steps.contains(PendingStep.Kind.REVERSAL_DECISION_REQUIRED)) {
          // a null step is the SDK's final notification, where the decision is ignored; the
          // flow is not held up for an answer nobody acts on
          flow.onError(
              (step, error) ->
                  step == null
                      ? ReversalDecision.ABORT
                      : ask(
                          hosted, operation, reversalStep(operation, step, error, moneyAnchored)));
        }
        flow.execute();
        operation.succeeded(view.apply(flow.get()));
      } catch (Throwable failure) {
        fail(operation, failure);
      } finally {
        hosted.completed(operation);
      }
    };
  }

  private static void fail(HostedOperation operation, Throwable failure) {
    HostError error = HostError.from(failure);
    if (failure instanceof SessionException
        && ((SessionException) failure).getAbandonedSettlement() != null) {
      operation.abandoned(
          Views.abandonedSettlement(((SessionException) failure).getAbandonedSettlement()));
    }
    boolean aborted = SessionErrorCode.ABORTED.name().equals(error.code());
    operation.failed(error, aborted);
  }

  // ─── Steps ───

  private <T> T ask(HostedSession hosted, HostedOperation operation, PendingStep<T> step) {
    operation.awaiting(step);
    hosted.publish("operation.step", step.toJson());
    try {
      return step.await();
    } finally {
      operation.resumed();
    }
  }

  private PendingStep<String> beforeStep(HostedOperation operation, SettlementContext context) {
    ObjectNode payload = Json.object();
    payload.set("context", Views.settlementContext(context));
    ObjectNode defaults = Json.object();
    defaults.put("saleTransactionId", context.getDefaultTransactionId());
    return new PendingStep<>(
        operation.id(),
        PendingStep.Kind.BEFORE_STEP,
        payload,
        defaults,
        defaults.deepCopy(),
        deadlines.beforeStep(),
        answer -> Json.text(answer, "saleTransactionId"));
  }

  private PendingStep<BigDecimal> totalStep(
      HostedOperation operation,
      String settlementStep,
      String field,
      ObjectNode result,
      BigDecimal suggested) {
    ObjectNode payload = Json.object();
    payload.put("step", settlementStep);
    payload.set(field, result);
    ObjectNode defaults = Json.object();
    defaults.put("total", suggested == null ? "0" : suggested.toPlainString());
    return new PendingStep<>(
        operation.id(),
        PendingStep.Kind.TOTAL_REQUIRED,
        payload,
        defaults,
        defaults.deepCopy(),
        deadlines.total(),
        answer -> {
          BigDecimal total = Json.decimal(answer, "total");
          if (total == null) {
            throw HostError.badRequest("a TOTAL_REQUIRED reply needs total");
          }
          if (total.signum() < 0) {
            throw HostError.badRequest("total must not be negative");
          }
          return total;
        });
  }

  private PendingStep<SettlementRecovery> recoveryStep(
      HostedOperation operation, SettlementFailure failure) {
    ObjectNode payload = Json.object();
    payload.set("failure", Views.settlementFailure(failure));
    ObjectNode defaults = Json.object();
    defaults.putObject("recovery").put("action", "ABORT");
    return new PendingStep<>(
        operation.id(),
        PendingStep.Kind.RECOVERY_REQUIRED,
        payload,
        defaults,
        defaults.deepCopy(),
        deadlines.recovery(),
        answer -> {
          JsonNode recovery = answer.get("recovery");
          if (recovery == null || recovery.isNull()) {
            throw HostError.badRequest("a RECOVERY_REQUIRED reply needs recovery");
          }
          return Parsers.recovery(recovery);
        });
  }

  private PendingStep<ReversalDecision> reversalStep(
      HostedOperation operation, ReversalStep step, SessionError error, boolean moneyAnchored) {
    ObjectNode payload = Json.object();
    payload.put("step", step.name());
    payload.set("error", Views.error(error));
    boolean loyalty =
        step == ReversalStep.REDEMPTION
            || step == ReversalStep.REBATE
            || step == ReversalStep.AWARD;
    ReversalDecision policy =
        loyalty && moneyAnchored ? ReversalDecision.SKIP : ReversalDecision.ABORT;
    ObjectNode defaults = Json.object();
    defaults.put("decision", policy.name());
    ObjectNode onAbort = Json.object();
    onAbort.put("decision", ReversalDecision.ABORT.name());
    return new PendingStep<>(
        operation.id(),
        PendingStep.Kind.REVERSAL_DECISION_REQUIRED,
        payload,
        defaults,
        onAbort,
        deadlines.reversalDecision(),
        answer -> {
          ReversalDecision decision = Json.enumValue(answer, "decision", ReversalDecision.class);
          if (decision == null) {
            throw HostError.badRequest("a REVERSAL_DECISION_REQUIRED reply needs decision");
          }
          return decision;
        });
  }
}
