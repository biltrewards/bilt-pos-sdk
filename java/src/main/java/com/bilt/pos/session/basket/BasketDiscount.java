/*
 *    ____  _ _ _
 *   | __ )(_) | |_
 *   |  _ \| | | __|
 *   | |_) | | | |_
 *   |____/|_|_|\__|
 *
 *   Bilt POS SDK
 */
package com.bilt.pos.session.basket;

import java.math.BigDecimal;
import java.util.Objects;

/** A register-applied discount on one basket line. */
public final class BasketDiscount {

    private final String reference;
    private final String label;
    private final BigDecimal amount;

    private BasketDiscount(String reference, String label, BigDecimal amount) {
        this.reference = reference;
        this.label = Objects.requireNonNull(label, "label");
        this.amount = Objects.requireNonNull(amount, "amount");
        if (label.isEmpty()) {
            throw new IllegalArgumentException("label must not be empty");
        }
        if (reference != null && reference.isEmpty()) {
            throw new IllegalArgumentException("reference must not be empty");
        }
        if (amount.signum() <= 0) {
            throw new IllegalArgumentException("discount amount must be positive");
        }
    }

    /** A discount not tied to a persisted offer identifier. */
    public static BasketDiscount manual(String label, String amount) {
        return manual(label, new BigDecimal(amount));
    }

    /** A discount not tied to a persisted offer identifier. */
    public static BasketDiscount manual(String label, BigDecimal amount) {
        return new BasketDiscount(null, label, amount);
    }

    /** A discount produced by a register-known offer. */
    public static BasketDiscount offer(String reference, String label, String amount) {
        return offer(reference, label, new BigDecimal(amount));
    }

    /** A discount produced by a register-known offer. */
    public static BasketDiscount offer(String reference, String label, BigDecimal amount) {
        return new BasketDiscount(Objects.requireNonNull(reference, "reference"), label, amount);
    }

    public String getReference() {
        return reference;
    }

    public String getLabel() {
        return label;
    }

    /** Positive discount magnitude. */
    public BigDecimal getAmount() {
        return amount;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof BasketDiscount)) {
            return false;
        }
        BasketDiscount that = (BasketDiscount) other;
        return amount.compareTo(that.amount) == 0
                && Objects.equals(reference, that.reference)
                && label.equals(that.label);
    }

    @Override
    public int hashCode() {
        return Objects.hash(reference, label, amount.stripTrailingZeros());
    }
}
