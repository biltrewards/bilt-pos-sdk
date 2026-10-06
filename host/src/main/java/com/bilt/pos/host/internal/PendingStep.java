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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;

/**
 * A question the settlement or reversal engine asked the register, parked until the register
 * answers or the deadline passes.
 *
 * <p>The SDK's flow thread blocks inside a handler; the host turns that into an {@code
 * operation.step} event and waits on this future. {@code POST .../operations/{id}/reply} completes
 * it with the register's answer, an abort completes it with the abort answer, and the deadline
 * completes it with the documented default. Answers stay JSON ({@code StepReply}) and are decoded
 * by the step's own {@code decode}, so each step kind validates its reply where it is defined.
 *
 * @param <T> what the SDK handler must return
 */
public final class PendingStep<T> {

  /** The step kinds the protocol knows. */
  public enum Kind {
    BEFORE_STEP,
    TOTAL_REQUIRED,
    RECOVERY_REQUIRED,
    REVERSAL_DECISION_REQUIRED
  }

  private final String operationId;
  private final String stepId = UUID.randomUUID().toString();
  private final Kind kind;
  private final ObjectNode payload;
  private final ObjectNode defaultReply;
  private final ObjectNode abortReply;
  private final Instant deadlineAt;
  private final Duration deadline;
  private final Function<JsonNode, T> decode;
  private final CompletableFuture<JsonNode> reply = new CompletableFuture<>();

  PendingStep(
      String operationId,
      Kind kind,
      ObjectNode payload,
      ObjectNode defaultReply,
      ObjectNode abortReply,
      Duration deadline,
      Function<JsonNode, T> decode) {
    this.operationId = operationId;
    this.kind = kind;
    this.payload = payload;
    this.defaultReply = defaultReply.put("stepId", stepId);
    this.abortReply = abortReply.put("stepId", stepId);
    this.deadline = deadline;
    this.deadlineAt = Instant.now().plus(deadline);
    this.decode = decode;
  }

  public String stepId() {
    return stepId;
  }

  public Kind kind() {
    return kind;
  }

  /** Validates and records the register's answer; false when the step was already settled. */
  public boolean reply(JsonNode answer) {
    decode.apply(answer);
    return reply.complete(answer);
  }

  /** Settles the step the way an abort would: ABORT for decisions, the default otherwise. */
  public void abort() {
    reply.complete(abortReply);
  }

  /**
   * Blocks the flow thread until an answer arrives, falling back to the default at the deadline.
   */
  public T await() {
    JsonNode answer;
    try {
      answer = reply.get(deadline.toMillis(), TimeUnit.MILLISECONDS);
    } catch (TimeoutException e) {
      // a reply or abort can land on the deadline; complete() then loses and the winner stands
      reply.complete(defaultReply);
      answer = reply.getNow(defaultReply);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      answer = abortReply;
    } catch (ExecutionException e) {
      answer = defaultReply;
    }
    return decode.apply(answer);
  }

  public boolean isSettled() {
    return reply.isDone();
  }

  /**
   * The {@code OperationStep}: the {@code pendingStep} view and the {@code operation.step} payload.
   */
  public ObjectNode toJson() {
    ObjectNode node = Json.object();
    node.put("operationId", operationId);
    node.put("stepId", stepId);
    node.put("kind", kind.name());
    node.put("deadlineAt", deadlineAt.toString());
    node.set("default", defaultReply);
    payload.fields().forEachRemaining(entry -> node.set(entry.getKey(), entry.getValue()));
    return node;
  }
}
