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

/** Context supplied when {@code SettlementFlow.onError} must direct recovery. */
public final class SettlementFailure {

    /** Whether the failed request's terminal outcome is known. */
    public enum OutcomeCertainty {
        /** The terminal outcome is known, including an authoritative not-found result. */
        DEFINITIVE,
        /** TransactionStatus could not establish the terminal outcome. */
        INDETERMINATE
    }

    private final SettlementStep step;
    private final SessionError error;
    private final BigDecimal amountDue;
    private final List<SettlementMovement> committedMovements;
    private final OutcomeCertainty outcomeCertainty;
    private final String messageCategory;
    private final String serviceId;

    private SettlementFailure(Builder builder) {
        this.step = builder.step;
        this.error = Objects.requireNonNull(builder.error, "error");
        this.amountDue = Objects.requireNonNull(builder.amountDue, "amountDue");
        this.committedMovements = Collections.unmodifiableList(new ArrayList<>(
                Objects.requireNonNull(builder.committedMovements, "committedMovements")));
        this.outcomeCertainty = Objects.requireNonNull(
                builder.outcomeCertainty, "outcomeCertainty");
        this.messageCategory = builder.messageCategory;
        this.serviceId = builder.serviceId;
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * The failed step, or {@code null} when the failure did not occur inside a
     * specific settlement step, such as a pre-sequence rejection or refused recovery.
     */
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

    /** Certainty established before the register is asked to direct recovery. */
    public OutcomeCertainty getOutcomeCertainty() {
        return outcomeCertainty;
    }

    public boolean isIndeterminate() {
        return outcomeCertainty == OutcomeCertainty.INDETERMINATE;
    }

    /**
     * Original request's Nexo message category, or {@code null} when no request
     * category is available.
     */
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

    /** Builder for {@link SettlementFailure}. */
    public static final class Builder {

        private SettlementStep step;
        private SessionError error;
        private BigDecimal amountDue;
        private List<SettlementMovement> committedMovements;
        private OutcomeCertainty outcomeCertainty;
        private String messageCategory;
        private String serviceId;

        private Builder() {
        }

        public Builder step(SettlementStep step) {
            this.step = step;
            return this;
        }

        public Builder error(SessionError error) {
            this.error = error;
            return this;
        }

        public Builder amountDue(BigDecimal amountDue) {
            this.amountDue = amountDue;
            return this;
        }

        public Builder committedMovements(List<SettlementMovement> committedMovements) {
            this.committedMovements = committedMovements;
            return this;
        }

        public Builder outcomeCertainty(OutcomeCertainty outcomeCertainty) {
            this.outcomeCertainty = outcomeCertainty;
            return this;
        }

        public Builder messageCategory(String messageCategory) {
            this.messageCategory = messageCategory;
            return this;
        }

        public Builder serviceId(String serviceId) {
            this.serviceId = serviceId;
            return this;
        }

        public SettlementFailure build() {
            return new SettlementFailure(this);
        }
    }
}
