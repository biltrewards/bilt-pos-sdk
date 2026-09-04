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

/** Resolution returned from {@code SettlementFlow.onError} to direct recovery. */
public final class SettlementRecovery {

    /** The action the settlement orchestrator takes after a failed step. */
    public enum Action {
        /** Retry only the failed step, preserving earlier committed steps. */
        RETRY,
        /** Skip an optional failed step and continue. */
        SKIP,
        /** Satisfy the outstanding tender through the register. */
        EXTERNAL,
        /** Reverse committed reversible steps and fail the settlement. */
        ABORT,
        /** Stop recovery and transfer the partial settlement to the register. */
        ABANDON
    }

    private final Action action;
    private final ExternalPayment externalPayment;

    private SettlementRecovery(Action action, ExternalPayment externalPayment) {
        this.action = action;
        this.externalPayment = externalPayment;
    }

    /** Unwind committed charge-side steps and fail the settlement. */
    public static SettlementRecovery abort() {
        return new SettlementRecovery(Action.ABORT, null);
    }

    /**
     * Retry only the failed step, preserving every earlier committed step.
     * When TransactionStatus could not resolve an indeterminate terminal request,
     * retry checks its status again and never resends the original request.
     */
    public static SettlementRecovery retry() {
        return new SettlementRecovery(Action.RETRY, null);
    }

    /**
     * Skip the failed step. Valid only when all basket obligations remain
     * satisfiable; the final card tender and stored-value fulfillment cannot skip.
     */
    public static SettlementRecovery skip() {
        return new SettlementRecovery(Action.SKIP, null);
    }

    /**
     * Records a register-managed tender and continues after the failed card step.
     * The payment amount must exactly equal the outstanding amount, and the
     * failure outcome must be definitive. The SDK attempts to resolve an
     * indeterminate original request before asking the register for a recovery
     * decision; external replacement remains invalid unless resolution is
     * definitive, preventing it from replacing a committed card payment.
     */
    public static SettlementRecovery external(ExternalPayment payment) {
        if (payment == null) {
            throw new NullPointerException("payment");
        }
        return new SettlementRecovery(Action.EXTERNAL, payment);
    }

    /**
     * Stop without further status recovery, retry, or unwind and transfer
     * responsibility for all committed movements to the register. Automatic
     * TransactionStatus recovery may already have occurred before {@code onError}.
     * The basket remains usable and SDK recovery guards are released, so a later
     * settlement can duplicate those movements unless the register reconciles them.
     */
    public static SettlementRecovery abandon() {
        return new SettlementRecovery(Action.ABANDON, null);
    }

    public Action getAction() {
        return action;
    }

    /** Register-managed tender; non-null only for {@link Action#EXTERNAL}. */
    public ExternalPayment getExternalPayment() {
        return externalPayment;
    }

    public boolean isAbort() {
        return action == Action.ABORT;
    }
}
