/*
 *    ____  _ _ _
 *   | __ )(_) | |_
 *   |  _ \| | | __|
 *   | |_) | | | |_
 *   |____/|_|_|\__|
 *
 *   Bilt POS SDK
 */
package com.bilt.pos.session.payment;

/** A step of the payment orchestration sequence. */
public enum TransactionStep {

    /** Terminal commits applicable offers/coupons. */
    REBATE,

    /** Terminal redeems points/rewards for monetary value. */
    POINTS,

    /** Terminal charges the registered stored value card. */
    STORED_VALUE,

    /** Terminal processes the card payment for the remaining amount. */
    CARD_PAYMENT,

    /** Terminal submits the loyalty award. */
    AWARD
}
