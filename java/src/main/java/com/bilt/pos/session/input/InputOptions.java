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

/** Options for the plain terminal input prompts. */
public final class InputOptions {

    private final Integer maxLength;
    private final Integer minLength;
    private final Duration timeout;
    private final String additionalText;
    private final String additionalText2;

    private InputOptions(Builder builder) {
        this.maxLength = builder.maxLength;
        this.minLength = builder.minLength;
        this.timeout = builder.timeout;
        this.additionalText = builder.additionalText;
        this.additionalText2 = builder.additionalText2;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static InputOptions defaults() {
        return builder().build();
    }

    public Integer getMaxLength() {
        return maxLength;
    }

    public Integer getMinLength() {
        return minLength;
    }

    /** Maximum time the customer has to respond, or {@code null} for the terminal default. */
    public Duration getTimeout() {
        return timeout;
    }

    /** Extra text line shown under the prompt. */
    public String getAdditionalText() {
        return additionalText;
    }

    /** Second extra text line. */
    public String getAdditionalText2() {
        return additionalText2;
    }

    /** Builder for {@link InputOptions}. */
    public static final class Builder {

        private Integer maxLength;
        private Integer minLength;
        private Duration timeout;
        private String additionalText;
        private String additionalText2;

        private Builder() {
        }

        public Builder maxLength(Integer maxLength) {
            this.maxLength = maxLength;
            return this;
        }

        public Builder minLength(Integer minLength) {
            this.minLength = minLength;
            return this;
        }

        public Builder timeout(Duration timeout) {
            this.timeout = timeout;
            return this;
        }

        public Builder additionalText(String additionalText) {
            this.additionalText = additionalText;
            return this;
        }

        public Builder additionalText2(String additionalText2) {
            this.additionalText2 = additionalText2;
            return this;
        }

        public InputOptions build() {
            return new InputOptions(this);
        }
    }
}
