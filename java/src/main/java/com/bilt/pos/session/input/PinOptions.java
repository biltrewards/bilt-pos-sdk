/*
 *    ____  _ _ _
 *   | __ )(_) | |_
 *   |  _ \| | | __|
 *   | |_) | | | |_
 *   |____/|_|_|\__|
 *
 *   Bilt POS SDK
 */
package com.bilt.pos.session.input;

import java.time.Duration;

/**
 * Options for PIN entry and verification.
 *
 * <p>For the verify modes, the reference the terminal verifies against is
 * addressed by {@link #getKeyReference()} / {@link #getPinVerificationMethod()}
 * — PIN blocks themselves never travel in the clear.</p>
 */
public final class PinOptions {

    private final Integer maxLength;
    private final Duration timeout;
    private final String keyReference;
    private final String pinVerificationMethod;

    private PinOptions(Builder builder) {
        this.maxLength = builder.maxLength;
        this.timeout = builder.timeout;
        this.keyReference = builder.keyReference;
        this.pinVerificationMethod = builder.pinVerificationMethod;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static PinOptions defaults() {
        return builder().build();
    }

    public Integer getMaxLength() {
        return maxLength;
    }

    public Duration getTimeout() {
        return timeout;
    }

    /** Reference of the key the terminal uses for the PIN block, or {@code null}. */
    public String getKeyReference() {
        return keyReference;
    }

    /** Verification method identifier for the verify modes, or {@code null}. */
    public String getPinVerificationMethod() {
        return pinVerificationMethod;
    }

    /** Builder for {@link PinOptions}. */
    public static final class Builder {

        private Integer maxLength;
        private Duration timeout;
        private String keyReference;
        private String pinVerificationMethod;

        private Builder() {
        }

        public Builder maxLength(Integer maxLength) {
            this.maxLength = maxLength;
            return this;
        }

        public Builder timeout(Duration timeout) {
            this.timeout = timeout;
            return this;
        }

        public Builder keyReference(String keyReference) {
            this.keyReference = keyReference;
            return this;
        }

        public Builder pinVerificationMethod(String pinVerificationMethod) {
            this.pinVerificationMethod = pinVerificationMethod;
            return this;
        }

        public PinOptions build() {
            return new PinOptions(this);
        }
    }
}
