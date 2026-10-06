/*
 *    ____  _ _ _
 *   | __ )(_) | |_
 *   |  _ \| | | __|
 *   | |_) | | | |_
 *   |____/|_|_|\__|
 *
 *   Bilt POS SDK
 */
package com.bilt.pos.host;

import java.time.Duration;
import java.util.Objects;

/**
 * How long a settlement or reversal waits for the register to answer a step before the documented
 * default applies.
 *
 * <p>Where the Java SDK blocks a flow thread on a register callback, the protocol publishes an
 * {@code operation.step} event and parks the flow until {@code POST .../operations/{id}/reply}
 * arrives or the deadline passes. The defaults mirror the design: 30 seconds for the totals and
 * {@code BEFORE_STEP} questions, which a register answers from local state, and two minutes for a
 * recovery or reversal decision, which usually needs a human.
 */
public final class StepDeadlines {

  private static final StepDeadlines DEFAULTS =
      new StepDeadlines(
          Duration.ofSeconds(30),
          Duration.ofSeconds(30),
          Duration.ofSeconds(120),
          Duration.ofSeconds(120));

  private final Duration beforeStep;
  private final Duration total;
  private final Duration recovery;
  private final Duration reversalDecision;

  private StepDeadlines(
      Duration beforeStep, Duration total, Duration recovery, Duration reversalDecision) {
    this.beforeStep = requirePositive(beforeStep, "beforeStep");
    this.total = requirePositive(total, "total");
    this.recovery = requirePositive(recovery, "recovery");
    this.reversalDecision = requirePositive(reversalDecision, "reversalDecision");
  }

  /** 30 s for totals and {@code BEFORE_STEP}, 120 s for recovery and reversal decisions. */
  public static StepDeadlines defaults() {
    return DEFAULTS;
  }

  /** The same deadline for every step kind; handy for tests that want steps to time out fast. */
  public static StepDeadlines uniform(Duration deadline) {
    return new StepDeadlines(deadline, deadline, deadline, deadline);
  }

  public StepDeadlines withBeforeStep(Duration deadline) {
    return new StepDeadlines(deadline, total, recovery, reversalDecision);
  }

  public StepDeadlines withTotal(Duration deadline) {
    return new StepDeadlines(beforeStep, deadline, recovery, reversalDecision);
  }

  public StepDeadlines withRecovery(Duration deadline) {
    return new StepDeadlines(beforeStep, total, deadline, reversalDecision);
  }

  public StepDeadlines withReversalDecision(Duration deadline) {
    return new StepDeadlines(beforeStep, total, recovery, deadline);
  }

  /** Deadline for {@code BEFORE_STEP}, the sale transaction ID question. */
  public Duration beforeStep() {
    return beforeStep;
  }

  /** Deadline for {@code TOTAL_REQUIRED} after rebates, points or a gift card payment. */
  public Duration total() {
    return total;
  }

  /** Deadline for {@code RECOVERY_REQUIRED} after a charge-side failure. */
  public Duration recovery() {
    return recovery;
  }

  /** Deadline for {@code REVERSAL_DECISION_REQUIRED} after a failed reversal leg. */
  public Duration reversalDecision() {
    return reversalDecision;
  }

  private static Duration requirePositive(Duration duration, String name) {
    Objects.requireNonNull(duration, name);
    if (duration.isNegative() || duration.isZero()) {
      throw new IllegalArgumentException(name + " must be positive");
    }
    return duration;
  }

  @Override
  public String toString() {
    return "StepDeadlines{beforeStep="
        + beforeStep
        + ", total="
        + total
        + ", recovery="
        + recovery
        + ", reversalDecision="
        + reversalDecision
        + "}";
  }
}
