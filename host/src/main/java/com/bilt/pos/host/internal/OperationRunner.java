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
import com.bilt.pos.session.identity.Member;
import com.bilt.pos.session.settlement.SettlementContext;
import com.bilt.pos.session.settlement.SettlementFailure;
import com.bilt.pos.session.settlement.SettlementOptions;
import com.bilt.pos.session.settlement.SettlementRecovery;
import com.bilt.pos.session.storedvalue.StoredValueCard;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.math.BigDecimal;
import java.time.Duration;
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
 * whatever {@code /reply}, an abort or the deadline resolved. A client that does not want to be
 * asked a step kind leaves it out of the request's {@code steps} list and the SDK default applies
 * immediately.
 */
public final class OperationRunner {

  private static final Set<PendingStep.Kind> DEFAULT_SETTLEMENT_STEPS =
      EnumSet.of(PendingStep.Kind.TOTAL_REQUIRED, PendingStep.Kind.RECOVERY_REQUIRED);
  private static final Set<PendingStep.Kind> DEFAULT_REVERSAL_STEPS =
      EnumSet.of(PendingStep.Kind.REVERSAL_DECISION_REQUIRED);

  private final StepDeadlines deadlines;

  public OperationRunner(StepDeadlines deadlines) {
    this.deadlines = deadlines;
  }

  /** Parses the body, registers the operation on the session and returns it (status queued). */
  public HostedOperation submit(HostedSession hosted, ObjectNode body) {
    String type = Json.requireText(body, "type");
    boolean ordered = !"updateInputDisplay".equals(type);
    HostedOperation operation = new HostedOperation(type, ordered);
    Runnable launch = launch(hosted, operation, type, body);
    hosted.submit(operation, launch);
    return operation;
  }

  /**
   * Answers a parked step. 409 when the operation is not waiting or the step ID is stale, 400/422
   * when the answer does not fit the step kind.
   */
  public void reply(HostedOperation operation, ObjectNode body) {
    String stepId = Json.requireText(body, "stepId");
    PendingStep<?> step = operation.pendingStep();
    if (step == null || operation.status() != HostedOperation.Status.AWAITING_REPLY) {
      throw HostError.conflict("operation " + operation.id() + " is not awaiting a reply");
    }
    if (!step.stepId().equals(stepId)) {
      throw HostError.conflict("step " + stepId + " is not the pending step of this operation");
    }
    if (!step.reply(body)) {
      throw HostError.conflict("step " + stepId + " was already answered");
    }
  }

  /**
   * Aborts an operation: a queued one is dropped, a parked step is answered the way an abort
   * would, and a terminal session's in-flight exchange is cancelled through the SDK's unordered
   * {@code abort()}.
   */
  public void abort(HostedSession hosted, HostedOperation operation) {
    if (operation.isTerminal()) {
      throw HostError.conflict("operation " + operation.id() + " has already completed");
    }
    if (operation.status() == HostedOperation.Status.QUEUED && hosted.dequeue(operation)) {
      operation.failed(
          HostError.of(
              new SessionError(SessionErrorCode.ABORTED, "the operation was aborted before it started")),
          true);
      hosted.completed(operation);
      return;
    }
    TerminalShopperSession terminal = hosted.terminal();
    if (terminal != null && operation.ordered()) {
      terminal.abort().execute();
    }
    PendingStep<?> step = operation.pendingStep();
    if (step != null) {
      step.abort();
    }
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
          Member pending = Parsers.member(body.get("member"));
          if (pending != null && pending.isResolved()) {
            throw HostError.badRequest(
                "identifyMember looks up a member by resolver; a resolved member is set with PUT"
                    + " .../member");
          }
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
              hosted, operation, () -> terminal.acquireCard(acquisitionOptions), Views::cardAcquisition);
        }
      case "requestDigitString":
        {
          String prompt = Json.requireText(body, "prompt");
          var inputOptions = Parsers.inputOptions(options);
          return result(
              hosted, operation, () -> terminal.requestDigitString(prompt, inputOptions), Views::value);
        }
      case "requestDecimalString":
        {
          String prompt = Json.requireText(body, "prompt");
          var inputOptions = Parsers.inputOptions(options);
          return result(
              hosted,
              operation,
              () -> terminal.requestDecimalString(prompt, inputOptions),
              Views::decimalValue);
        }
      case "requestTextString":
        {
          String prompt = Json.requireText(body, "prompt");
          var inputOptions = Parsers.inputOptions(options);
          return result(
              hosted, operation, () -> terminal.requestTextString(prompt, inputOptions), Views::value);
        }
      case "requestConfirmation":
        {
          String prompt = Json.requireText(body, "prompt");
          var confirmationOptions = Parsers.confirmationOptions(options);
          return result(
              hosted,
              operation,
              () -> terminal.requestConfirmation(prompt, confirmationOptions),
              Views::confirmed);
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
          return result(hosted, operation, () -> terminal.requestSignature(prompt), Views::signature);
        }
      case "requestAmountConfirmation":
        {
          BigDecimal amount = Json.requireDecimal(body, "amount");
          String prompt = Json.requireText(body, "prompt");
          return result(
              hosted,
              operation,
              () -> terminal.requestAmountConfirmation(amount, prompt),
              Views::confirmed);
        }
      case "requestPinEntry":
        {
          var pinOptions = Parsers.pinOptions(options);
          return result(hosted, operation, () -> terminal.requestPinEntry(pinOptions), Views::pinResult);
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
          StoredValueCard card =
              Json.has(body, "card") ? Parsers.storedValueCard(body.get("card"), "card") : null;
          return immediate(
              hosted,
              operation,
              () -> {
                terminal.setStoredValueCard(card);
                return Json.object();
              });
        }
      case "storedValueBalance":
        {
          StoredValueCard card = Parsers.storedValueCard(body.get("card"), "card");
          return result(
              hosted, operation, () -> terminal.storedValueBalance(card), Views::storedValueBalance);
        }
      case "storedValueActivate":
        {
          StoredValueCard card = Parsers.storedValueCard(body.get("card"), "card");
          BigDecimal amount = Json.requireDecimal(body, "initialAmount");
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
      case "storedValueDeactivate":
        {
          StoredValueCard card = Parsers.storedValueCard(body.get("card"), "card");
          return result(
              hosted,
              operation,
              () -> terminal.storedValueDeactivate(card),
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
      case "storedValueDuplicate":
        {
          StoredValueCard card = Parsers.storedValueCard(body.get("card"), "card");
          return result(
              hosted,
              operation,
              () -> terminal.storedValueDuplicate(card),
              Views::storedValueOperation);
        }
      case "settle":
        {
          SettlementOptions settlementOptions = Parsers.settlementOptions(options);
          Set<PendingStep.Kind> steps = steps(body, DEFAULT_SETTLEMENT_STEPS);
          return settlement(hosted, operation, () -> terminal.settle(settlementOptions), steps);
        }
      case "refund":
        {
          BigDecimal amount = Json.decimal(body, "amount");
          Set<PendingStep.Kind> steps = steps(body, DEFAULT_REVERSAL_STEPS);
          return reversal(
              hosted,
              operation,
              () -> amount == null ? terminal.refund() : terminal.refund(amount),
              Views::refundResult,
              steps);
        }
      case "refundUnlinked":
        {
          BigDecimal amount = Json.requireDecimal(body, "amount");
          Set<PendingStep.Kind> steps = steps(body, DEFAULT_REVERSAL_STEPS);
          return reversal(
              hosted, operation, () -> terminal.refundUnlinked(amount), Views::refundResult, steps);
        }
      case "voidTransaction":
        {
          var originalSale =
              Json.has(body, "originalSale") ? Parsers.originalSale(body.get("originalSale")) : null;
          Set<PendingStep.Kind> steps = steps(body, DEFAULT_REVERSAL_STEPS);
          return reversal(
              hosted,
              operation,
              () ->
                  originalSale == null
                      ? terminal.voidTransaction()
                      : terminal.voidTransaction(originalSale),
              Views::voidResult,
              steps);
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
          DisplayPayload payload = displayPayload(body);
          return result(
              hosted,
              operation,
              () ->
                  payload == null
                      ? terminal.updateDisplay(terminal.basket().snapshot())
                      : terminal.updateDisplay(payload),
              v -> Json.object());
        }
      case "updateInputDisplay":
        {
          DisplayPayload payload = displayPayload(body);
          if (payload == null) {
            throw HostError.badRequest("updateInputDisplay requires a payload");
          }
          return result(hosted, operation, () -> terminal.updateInputDisplay(payload), v -> Json.object());
        }
      default:
        throw HostError.badRequest("unknown operation type '" + type + "'");
    }
  }

  /**
   * The structured display model, read with Jackson's bean conventions from the same field names
   * the XML schema uses; there is no richer protocol vocabulary for it yet.
   */
  private static DisplayPayload displayPayload(ObjectNode body) {
    JsonNode payload = body.get("payload");
    if (payload == null || payload.isNull()) {
      return null;
    }
    try {
      return Json.MAPPER.treeToValue(payload, DisplayPayload.class);
    } catch (RuntimeException | com.fasterxml.jackson.core.JsonProcessingException e) {
      throw HostError.badRequest("payload is not a display payload: " + e.getMessage());
    }
  }

  private static Set<PendingStep.Kind> steps(ObjectNode body, Set<PendingStep.Kind> defaults) {
    List<String> names = Json.strings(body, "steps");
    if (names == null) {
      return defaults;
    }
    EnumSet<PendingStep.Kind> steps = EnumSet.noneOf(PendingStep.Kind.class);
    for (String name : names) {
      steps.add(Json.enumValue(name, "steps", PendingStep.Kind.class));
    }
    return steps;
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
  private Runnable immediate(
      HostedSession hosted, HostedOperation operation, Supplier<JsonNode> work) {
    return () -> {
      try {
        operation.succeeded(work.get());
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
          flow.beforeStep(context -> ask(hosted, operation, beforeStep(context)));
        }
        if (steps.contains(PendingStep.Kind.TOTAL_REQUIRED)) {
          flow.onRebatesRedeemed(
              rebates ->
                  ask(
                      hosted,
                      operation,
                      totalStep(Views.rebatesRedeemed(rebates), rebates.getSuggestedTotal())));
          flow.onPointsRedeemed(
              points ->
                  ask(
                      hosted,
                      operation,
                      totalStep(Views.pointsRedeemed(points), points.getSuggestedTotal())));
          flow.onGiftCardPayment(
              giftCard ->
                  ask(
                      hosted,
                      operation,
                      totalStep(Views.giftCardPayment(giftCard), giftCard.getSuggestedTotal())));
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
                      : ask(hosted, operation, recoveryStep(failure)));
        }
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
      Set<PendingStep.Kind> steps) {
    return () -> {
      try {
        ReversalFlow<T> flow = factory.get();
        if (steps.contains(PendingStep.Kind.REVERSAL_DECISION_REQUIRED)) {
          flow.onError(
              (step, error) ->
                  step == null
                      ? ReversalDecision.ABORT
                      : ask(hosted, operation, reversalStep(step, error)));
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
      ObjectNode details = error.details() instanceof ObjectNode ? (ObjectNode) error.details() : Json.object();
      details.set(
          "abandonedSettlement",
          Views.abandonedSettlement(((SessionException) failure).getAbandonedSettlement()));
      error = new HostError(error.status(), error.code(), error.getMessage(), details, failure);
    }
    boolean aborted = SessionErrorCode.ABORTED.name().equals(error.code());
    operation.failed(error, aborted);
  }

  // ─── Steps ───

  private <T> T ask(HostedSession hosted, HostedOperation operation, PendingStep<T> step) {
    operation.awaiting(step);
    ObjectNode payload = Json.object();
    payload.put("operationId", operation.id());
    payload.set("step", step.toJson());
    hosted.publish("operation.step", payload);
    try {
      return step.await();
    } finally {
      operation.resumed();
    }
  }

  private PendingStep<String> beforeStep(SettlementContext context) {
    ObjectNode defaults = Json.object();
    defaults.put("saleTransactionId", context.getDefaultTransactionId());
    return new PendingStep<>(
        PendingStep.Kind.BEFORE_STEP,
        Views.settlementContext(context),
        defaults,
        defaults,
        deadlines.beforeStep(),
        answer -> Json.text(answer, "saleTransactionId"));
  }

  private PendingStep<BigDecimal> totalStep(ObjectNode payload, BigDecimal suggested) {
    ObjectNode defaults = Json.object();
    defaults.put("total", Json.money(suggested));
    return new PendingStep<>(
        PendingStep.Kind.TOTAL_REQUIRED,
        payload,
        defaults,
        defaults,
        deadlines.total(),
        answer -> {
          BigDecimal total = Json.decimal(answer, "total");
          if (total == null) {
            throw HostError.badRequest("a TOTAL_REQUIRED reply needs total");
          }
          if (total.signum() < 0) {
            throw HostError.unprocessable("total must not be negative");
          }
          return total;
        });
  }

  private PendingStep<SettlementRecovery> recoveryStep(SettlementFailure failure) {
    ObjectNode payload = Json.object();
    payload.set("failure", Views.settlementFailure(failure));
    ObjectNode defaults = Json.object();
    defaults.put("recovery", "ABORT");
    return new PendingStep<>(
        PendingStep.Kind.RECOVERY_REQUIRED,
        payload,
        defaults,
        defaults,
        deadlines.recovery(),
        OperationRunner::recovery);
  }

  /** {@code "RETRY" | "SKIP" | "ABORT" | "ABANDON" | { "external": {...} }}. */
  private static SettlementRecovery recovery(JsonNode answer) {
    JsonNode recovery = answer.get("recovery");
    if (recovery == null || recovery.isNull()) {
      throw HostError.badRequest("a RECOVERY_REQUIRED reply needs recovery");
    }
    if (recovery.isObject()) {
      return SettlementRecovery.external(Parsers.externalPayment(recovery.get("external")));
    }
    if (!recovery.isTextual()) {
      throw HostError.badRequest("recovery must be a string or { external }");
    }
    switch (recovery.asText().toUpperCase(java.util.Locale.ROOT)) {
      case "RETRY":
        return SettlementRecovery.retry();
      case "SKIP":
        return SettlementRecovery.skip();
      case "ABORT":
        return SettlementRecovery.abort();
      case "ABANDON":
        return SettlementRecovery.abandon();
      default:
        throw HostError.badRequest(
            "recovery must be RETRY, SKIP, ABORT, ABANDON or { external }, not '"
                + recovery.asText()
                + "'");
    }
  }

  private PendingStep<ReversalDecision> reversalStep(ReversalStep step, SessionError error) {
    ObjectNode payload = Json.object();
    payload.put("reversalStep", step.name());
    payload.set("error", Views.error(error));
    ObjectNode defaults = Json.object();
    defaults.put("decision", "ABORT");
    return new PendingStep<>(
        PendingStep.Kind.REVERSAL_DECISION_REQUIRED,
        payload,
        defaults,
        defaults,
        deadlines.reversalDecision(),
        answer -> {
          ReversalDecision decision = Json.enumValue(answer, "decision", ReversalDecision.class);
          if (decision == null) {
            throw HostError.badRequest("a REVERSAL_DECISION_REQUIRED reply needs decision");
          }
          return decision;
        });
  }

  /** How long the host waits on each step kind; exposed for the health report. */
  public StepDeadlines deadlines() {
    return deadlines;
  }

  static Duration never() {
    return Duration.ofDays(365);
  }
}
