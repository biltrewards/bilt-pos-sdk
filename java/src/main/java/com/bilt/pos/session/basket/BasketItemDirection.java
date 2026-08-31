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

/** Commercial direction of a basket line. */
public enum BasketItemDirection {

    /** A line sold to the customer. */
    SALE,

    /** A merchandise return or trade-in whose totals subtract from the basket. */
    RETURN,

    /** A register-originated credit whose totals subtract from the basket. */
    CREDIT
}
