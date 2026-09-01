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

/** Settlement treatment of a basket line. */
public enum BasketItemType {

    /** A line sold to the customer. */
    SALE,

    /** A merchandise return or trade-in whose totals subtract from the basket. */
    RETURN,

    /** A register-originated credit whose totals subtract from the basket. */
    CREDIT
}
