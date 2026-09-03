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

import com.bilt.pos.session.SessionError;
import com.bilt.pos.session.SessionErrorCode;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/** Context supplied to {@code SettlementFlow.onError} before recovery begins. */
public final class SettlementFailure {

    /** Whether the failed request is known not to have committed. */
    public enum OutcomeCertainty {
        DEFINITIVE,
        INDETERMINATE
    }

    private final SettlementStep step;
    private final SessionError error;
    private final BigDecimal amountDue;
    private final List<SettlementMovement> committedMovements;
    private final OutcomeCertainty outcomeCertainty;
    private final String messageCategory;
    private final String serviceId;

    public SettlementFailure(SettlementStep step, SessionError error, BigDecimal amountDue,
                             List<SettlementMovement> committedMovements,
                             OutcomeCertainty outcomeCertainty, String messageCategory,
                             String serviceId) {
        this.step = step;
        this.error = Objects.requireNonNull(error, "error");
        this.amountDue = amountDue == null ? BigDecimal.ZERO : amountDue;
        this.committedMovements = Collections.unmodifiableList(new ArrayList<>(
                committedMovements == null ? List.of() : committedMovements));
        this.outcomeCertainty = outcomeCertainty == null
                ? OutcomeCertainty.DEFINITIVE : outcomeCertainty;
        this.messageCategory = messageCategory;
        this.serviceId = serviceId;
    }

    public SettlementStep getStep() {
        return step;
    }

    /** Underlying operation error. */
    public SessionError getError() {
        return error;
    }

    /** Amount the failed step was responsible for resolving. */
    public BigDecimal getAmountDue() {
        return amountDue;
    }

    /** Immutable ledger snapshot taken before the register's recovery decision. */
    public List<SettlementMovement> getCommittedMovements() {
        return committedMovements;
    }

    public OutcomeCertainty getOutcomeCertainty() {
        return outcomeCertainty;
    }

    public boolean isIndeterminate() {
        return outcomeCertainty == OutcomeCertainty.INDETERMINATE;
    }

    public String getMessageCategory() {
        return messageCategory;
    }

    /** Original request's Nexo {@code ServiceID}, when a request was sent. */
    public String getServiceId() {
        return serviceId;
    }

    // Convenience delegates keep simple handlers as compact as SessionError handlers.
    public SessionErrorCode getCode() {
        return error.getCode();
    }

    public String getMessage() {
        return error.getMessage();
    }

    public String getNexoErrorCondition() {
        return error.getNexoErrorCondition();
    }

    public Throwable getCause() {
        return error.getCause();
    }
}
