/*
 *    ____  _ _ _
 *   | __ )(_) | |_
 *   |  _ \| | | __|
 *   | |_) | | | |_
 *   |____/|_|_|\__|
 *
 *   Bilt POS SDK
 */
package com.bilt.pos.session.identity;

import com.bilt.pos.nexo.model.PaymentTypeEnum;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Options for {@code acquireCard()}. */
public final class CardAcquisitionOptions {

    private final List<ForceEntryMode> forceEntryModes;
    private final PaymentTypeEnum paymentType;
    private final Duration timeout;

    private CardAcquisitionOptions(Builder builder) {
        this.forceEntryModes = Collections.unmodifiableList(new ArrayList<>(builder.forceEntryModes));
        this.paymentType = builder.paymentType;
        this.timeout = builder.timeout;
    }

    public static Builder builder() {
        return new Builder();
    }

    /** Defaults: any entry mode, no routing hint, client default timeout. */
    public static CardAcquisitionOptions defaults() {
        return builder().build();
    }

    /** How the card may be read; empty allows any method. */
    public List<ForceEntryMode> getForceEntryModes() {
        return forceEntryModes;
    }

    /** Optional hint for card routing, or {@code null}. */
    public PaymentTypeEnum getPaymentType() {
        return paymentType;
    }

    /** Terminal-side response timeout override, or {@code null}. */
    public Duration getTimeout() {
        return timeout;
    }

    /** Builder for {@link CardAcquisitionOptions}. */
    public static final class Builder {

        private List<ForceEntryMode> forceEntryModes = new ArrayList<>();
        private PaymentTypeEnum paymentType;
        private Duration timeout;

        private Builder() {
        }

        public Builder forceEntryMode(ForceEntryMode mode) {
            this.forceEntryModes.add(mode);
            return this;
        }

        public Builder forceEntryModes(List<ForceEntryMode> modes) {
            this.forceEntryModes = new ArrayList<>(modes);
            return this;
        }

        public Builder paymentType(PaymentTypeEnum paymentType) {
            this.paymentType = paymentType;
            return this;
        }

        public Builder timeout(Duration timeout) {
            this.timeout = timeout;
            return this;
        }

        public CardAcquisitionOptions build() {
            return new CardAcquisitionOptions(this);
        }
    }
}
