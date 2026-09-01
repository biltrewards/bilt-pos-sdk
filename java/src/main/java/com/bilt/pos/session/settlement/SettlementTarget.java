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

import java.util.Objects;

/** The basket obligation resolved by a settlement instruction or movement. */
public final class SettlementTarget {

    public enum Type {
        /** The basket's charge-side value after register-originated credits. */
        SALES,

        /** The basket value restored for return lines. */
        REFUNDS,

        /** One referenced basket line requiring fulfillment. */
        BASKET_LINE
    }

    private static final SettlementTarget SALES = new SettlementTarget(Type.SALES, null);
    private static final SettlementTarget REFUNDS = new SettlementTarget(Type.REFUNDS, null);

    private final Type type;
    private final String basketReference;

    private SettlementTarget(Type type, String basketReference) {
        this.type = type;
        this.basketReference = basketReference;
    }

    public static SettlementTarget sales() {
        return SALES;
    }

    public static SettlementTarget refunds() {
        return REFUNDS;
    }

    public static SettlementTarget basketLine(String reference) {
        Objects.requireNonNull(reference, "reference");
        if (reference.isEmpty()) {
            throw new IllegalArgumentException("reference must not be empty");
        }
        return new SettlementTarget(Type.BASKET_LINE, reference);
    }

    public Type getType() {
        return type;
    }

    /** Register reference for {@link Type#BASKET_LINE}, otherwise {@code null}. */
    public String getBasketReference() {
        return basketReference;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof SettlementTarget)) {
            return false;
        }
        SettlementTarget that = (SettlementTarget) other;
        return type == that.type && Objects.equals(basketReference, that.basketReference);
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, basketReference);
    }

    @Override
    public String toString() {
        return basketReference == null ? type.name() : type + "(" + basketReference + ")";
    }
}
