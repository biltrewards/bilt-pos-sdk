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
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

/**
 * The operation resource: one lazy SDK operation as the protocol sees it, from {@code queued}
 * through {@code running} and possibly {@code awaitingReply} to a terminal status with a result or
 * an error. The runner mutates it; handlers only read it through {@link #toJson()}.
 */
public final class HostedOperation {

  public enum Status {
    QUEUED,
    RUNNING,
    AWAITING_REPLY,
    SUCCEEDED,
    FAILED,
    ABORTED;

    /** The wire spelling: lower camel case. */
    public String wire() {
      return this == AWAITING_REPLY ? "awaitingReply" : name().toLowerCase(Locale.ROOT);
    }

    public static Status fromWire(String wire) {
      for (Status status : values()) {
        if (status.wire().equals(wire)) {
          return status;
        }
      }
      throw HostError.badRequest("unknown operation status '" + wire + "'");
    }
  }

  private final String id = UUID.randomUUID().toString();
  private final String type;
  private final boolean ordered;
  private final Instant createdAt = Instant.now();
  private volatile Status status = Status.QUEUED;
  private volatile Instant startedAt;
  private volatile Instant completedAt;
  private volatile JsonNode result;
  private volatile ObjectNode error;
  private volatile ObjectNode abandonedSettlement;
  private final java.util.concurrent.atomic.AtomicBoolean completionPublished =
      new java.util.concurrent.atomic.AtomicBoolean();

  /** True the first time only, so {@code operation.completed} is published exactly once. */
  boolean markCompletionPublished() {
    return completionPublished.compareAndSet(false, true);
  }

  private volatile PendingStep<?> pendingStep;

  HostedOperation(String type, boolean ordered) {
    this.type = type;
    this.ordered = ordered;
  }

  public String id() {
    return id;
  }

  public String type() {
    return type;
  }

  /** Whether the operation takes its turn on the session's ordered lane. */
  public boolean ordered() {
    return ordered;
  }

  public Status status() {
    return status;
  }

  public Instant createdAt() {
    return createdAt;
  }

  public boolean isTerminal() {
    Status now = status;
    return now == Status.SUCCEEDED || now == Status.FAILED || now == Status.ABORTED;
  }

  /** Voids and the lifecycle signals are never an abort's target. */
  public boolean isAbortable() {
    return !"voidTransaction".equals(type) && !"end".equals(type) && !"forceEnd".equals(type);
  }

  public PendingStep<?> pendingStep() {
    return pendingStep;
  }

  void started() {
    startedAt = Instant.now();
    status = Status.RUNNING;
  }

  void awaiting(PendingStep<?> step) {
    pendingStep = step;
    status = Status.AWAITING_REPLY;
  }

  void resumed() {
    pendingStep = null;
    status = Status.RUNNING;
  }

  void succeeded(JsonNode value) {
    pendingStep = null;
    result = value;
    completedAt = Instant.now();
    status = Status.SUCCEEDED;
  }

  void failed(HostError failure, boolean aborted) {
    pendingStep = null;
    error = failure.toJson();
    completedAt = Instant.now();
    status = aborted ? Status.ABORTED : Status.FAILED;
  }

  void abandoned(ObjectNode record) {
    abandonedSettlement = record;
  }

  public ObjectNode toJson() {
    ObjectNode node = Json.object();
    node.put("id", id);
    node.put("type", type);
    node.put("status", status.wire());
    node.put("createdAt", createdAt.toString());
    Json.putInstant(node, "startedAt", startedAt);
    Json.putInstant(node, "completedAt", completedAt);
    if (result != null && !result.isNull()) {
      node.set("result", result);
    }
    if (error != null) {
      node.set("error", error);
    }
    if (abandonedSettlement != null) {
      node.set("abandonedSettlement", abandonedSettlement);
    }
    PendingStep<?> step = pendingStep;
    if (step != null && !step.isSettled()) {
      node.set("pendingStep", step.toJson());
    }
    return node;
  }
}
