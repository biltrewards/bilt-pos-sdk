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

/** Resolution returned from {@code SettlementFlow.onError}. */
public final class SettlementRecovery {

    private final boolean abort;
    private final Boolean disableRebates;
    private final Boolean disablePoints;
    private final Boolean disableAward;

    private SettlementRecovery(boolean abort, Boolean disableRebates,
                               Boolean disablePoints, Boolean disableAward) {
        this.abort = abort;
        this.disableRebates = disableRebates;
        this.disablePoints = disablePoints;
        this.disableAward = disableAward;
    }

    /** Unwind committed charge-side steps and fail the settlement. */
    public static SettlementRecovery abort() {
        return new SettlementRecovery(true, null, null, null);
    }

    /** Unwind committed charge-side steps and retry with the same settlement options. */
    public static SettlementRecovery retry() {
        return new SettlementRecovery(false, null, null, null);
    }

    /** Retry with the same settlement options but all loyalty steps disabled. */
    public static SettlementRecovery retryWithoutLoyalty() {
        return new SettlementRecovery(false, true, true, true);
    }

    public boolean isAbort() {
        return abort;
    }

    /** Nullable override; {@code null} preserves the current attempt setting. */
    public Boolean getDisableRebates() {
        return disableRebates;
    }

    /** Nullable override; {@code null} preserves the current attempt setting. */
    public Boolean getDisablePoints() {
        return disablePoints;
    }

    /** Nullable override; {@code null} preserves the current attempt setting. */
    public Boolean getDisableAward() {
        return disableAward;
    }
}
