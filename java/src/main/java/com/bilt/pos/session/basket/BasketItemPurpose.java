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

/** What completing a basket line requires beyond collecting or returning money. */
public enum BasketItemPurpose {

    /** Ordinary merchandise or service; no additional terminal fulfillment. */
    MERCHANDISE,

    /** Value purchased for activation or loading onto a stored value card. */
    STORED_VALUE_LOAD
}
