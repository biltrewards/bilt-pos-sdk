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

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Options for terminal-prompted member identification. */
public final class IdentifyOptions {

    private final List<ForceEntryMode> forceEntryModes;
    private final List<String> allowedLoyaltyBrands;
    private final boolean requireMember;
    private final Duration timeout;

    private IdentifyOptions(Builder builder) {
        this.forceEntryModes = Collections.unmodifiableList(new ArrayList<>(builder.forceEntryModes));
        this.allowedLoyaltyBrands = Collections.unmodifiableList(new ArrayList<>(builder.allowedLoyaltyBrands));
        this.requireMember = builder.requireMember;
        this.timeout = builder.timeout;
    }

    public static Builder builder() {
        return new Builder();
    }

    /** Defaults: any entry mode, any brand, member required. */
    public static IdentifyOptions defaults() {
        return builder().build();
    }

    /** How the identifier may be captured; empty allows any method. */
    public List<ForceEntryMode> getForceEntryModes() {
        return forceEntryModes;
    }

    /** Loyalty programs to accept; empty accepts any. */
    public List<String> getAllowedLoyaltyBrands() {
        return allowedLoyaltyBrands;
    }

    /**
     * Whether the lookup fails when no member is found
     * ({@code LoyaltyHandling=Required}) or continues without one
     * ({@code Proposed}). Default {@code true}.
     */
    public boolean isRequireMember() {
        return requireMember;
    }

    /** Terminal-side response timeout override, or {@code null}. */
    public Duration getTimeout() {
        return timeout;
    }

    /** Builder for {@link IdentifyOptions}. */
    public static final class Builder {

        private List<ForceEntryMode> forceEntryModes = new ArrayList<>();
        private List<String> allowedLoyaltyBrands = new ArrayList<>();
        private boolean requireMember = true;
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

        public Builder allowedLoyaltyBrand(String brand) {
            this.allowedLoyaltyBrands.add(brand);
            return this;
        }

        public Builder requireMember(boolean requireMember) {
            this.requireMember = requireMember;
            return this;
        }

        public Builder timeout(Duration timeout) {
            this.timeout = timeout;
            return this;
        }

        public IdentifyOptions build() {
            return new IdentifyOptions(this);
        }
    }
}
