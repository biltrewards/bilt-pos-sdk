/*
 *    ____  _ _ _
 *   | __ )(_) | |_
 *   |  _ \| | | __|
 *   | |_) | | | |_
 *   |____/|_|_|\__|
 *
 *   Bilt POS SDK
 */
package com.bilt.pos.session.storedvalue;

import java.math.BigDecimal;

/** Outcome of a stored value balance inquiry. */
public final class StoredValueBalance {

    private final BigDecimal balance;
    private final String currency;

    public StoredValueBalance(BigDecimal balance, String currency) {
        this.balance = balance;
        this.currency = currency;
    }

    /** Available balance on the card. */
    public BigDecimal getBalance() {
        return balance;
    }

    public String getCurrency() {
        return currency;
    }
}
