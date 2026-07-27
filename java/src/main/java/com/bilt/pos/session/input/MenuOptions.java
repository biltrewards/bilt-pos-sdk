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

/** Options for {@code requestMenuEntry}. */
public final class MenuOptions {

    private final boolean multiSelect;
    private final String additionalText;
    private final Duration timeout;

    private MenuOptions(Builder builder) {
        this.multiSelect = builder.multiSelect;
        this.additionalText = builder.additionalText;
        this.timeout = builder.timeout;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static MenuOptions defaults() {
        return builder().build();
    }

    /** Whether the customer may pick several entries. Default {@code false}. */
    public boolean isMultiSelect() {
        return multiSelect;
    }

    /** Extra text line shown under the prompt. */
    public String getAdditionalText() {
        return additionalText;
    }

    public Duration getTimeout() {
        return timeout;
    }

    /** Builder for {@link MenuOptions}. */
    public static final class Builder {

        private boolean multiSelect;
        private String additionalText;
        private Duration timeout;

        private Builder() {
        }

        public Builder multiSelect(boolean multiSelect) {
            this.multiSelect = multiSelect;
            return this;
        }

        public Builder additionalText(String additionalText) {
            this.additionalText = additionalText;
            return this;
        }

        public Builder timeout(Duration timeout) {
            this.timeout = timeout;
            return this;
        }

        public MenuOptions build() {
            return new MenuOptions(this);
        }
    }
}
