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

import java.math.BigDecimal;
import java.util.Objects;

/**
 * A payment collected and owned by the register rather than the terminal.
 * Settlement accepts it only as a replacement for a failed final card tender,
 * and its amount must exactly equal the outstanding balance.
 */
public final class ExternalPayment {

    private final String tenderType;
    private final BigDecimal amount;
    private final String reference;

    private ExternalPayment(String tenderType, BigDecimal amount, String reference) {
        if (tenderType == null || tenderType.trim().isEmpty()) {
            throw new IllegalArgumentException("tenderType must not be blank");
        }
        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException("amount must be positive");
        }
        this.tenderType = tenderType.trim();
        this.amount = amount;
        this.reference = reference;
    }

    /** Register-managed cash payment. */
    public static ExternalPayment cash(BigDecimal amount) {
        return cash(amount, null);
    }

    /** Register-managed cash payment with a register transaction reference. */
    public static ExternalPayment cash(BigDecimal amount, String reference) {
        return new ExternalPayment("CASH", amount, reference);
    }

    /** Register-managed payment using an integrator-defined tender name. */
    public static ExternalPayment of(String tenderType, BigDecimal amount, String reference) {
        return new ExternalPayment(tenderType, amount, reference);
    }

    public String getTenderType() {
        return tenderType;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getReference() {
        return reference;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof ExternalPayment)) {
            return false;
        }
        ExternalPayment that = (ExternalPayment) other;
        return Objects.equals(tenderType, that.tenderType)
                && Objects.equals(amount, that.amount)
                && Objects.equals(reference, that.reference);
    }

    @Override
    public int hashCode() {
        return Objects.hash(tenderType, amount, reference);
    }
}
