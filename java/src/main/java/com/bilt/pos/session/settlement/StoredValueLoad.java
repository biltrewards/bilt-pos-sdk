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

import com.bilt.pos.session.storedvalue.StoredValueCard;

import java.util.Objects;

/**
 * Settlement-time stored value fulfillment for a referenced sale line. The
 * card is loaded with the line's original, pre-discount value. Register
 * discounts reduce only the amount charged to the customer, so a fully
 * discounted line still loads the card's full face value.
 */
public final class StoredValueLoad {

    public enum Type {
        /** Activate a new card and load the basket line's pre-discount value. */
        ACTIVATE,

        /** Add the basket line's pre-discount value to an already active card. */
        RELOAD
    }

    private final SettlementTarget target;
    private final Type type;
    private final StoredValueCard card;

    private StoredValueLoad(String basketReference, Type type, StoredValueCard card) {
        this.target = SettlementTarget.basketLine(basketReference);
        this.type = Objects.requireNonNull(type, "type");
        this.card = Objects.requireNonNull(card, "card");
    }

    public static StoredValueLoad activate(String basketReference, StoredValueCard card) {
        return new StoredValueLoad(basketReference, Type.ACTIVATE, card);
    }

    public static StoredValueLoad reload(String basketReference, StoredValueCard card) {
        return new StoredValueLoad(basketReference, Type.RELOAD, card);
    }

    public SettlementTarget getTarget() {
        return target;
    }

    public String getBasketReference() {
        return target.getBasketReference();
    }

    public Type getType() {
        return type;
    }

    public StoredValueCard getCard() {
        return card;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof StoredValueLoad)) {
            return false;
        }
        StoredValueLoad that = (StoredValueLoad) other;
        return target.equals(that.target) && type == that.type && card.equals(that.card);
    }

    @Override
    public int hashCode() {
        return Objects.hash(target, type, card);
    }
}
