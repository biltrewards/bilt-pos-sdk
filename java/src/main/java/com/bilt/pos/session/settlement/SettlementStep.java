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

/** A step of the settlement sequence. */
public enum SettlementStep {

    /** Terminal commits applicable offers/coupons. */
    REBATE_REDEMPTION,

    /** Terminal redeems points/rewards for monetary value. */
    POINT_REDEMPTION,

    /** Terminal charges the registered stored value card. */
    STORED_VALUE_CHARGE,

    /** Terminal processes the card payment for the remaining amount. */
    CARD_CHARGE,

    /** Terminal submits the loyalty award. */
    AWARD,

    /** Terminal returns money to a payment card. */
    CARD_REFUND,

    /** Terminal restores funds to a stored value card. */
    STORED_VALUE_REFUND,

    /** Register records a refund fulfilled outside the terminal. */
    EXTERNAL_REFUND,

    /** Terminal reverses a prior point/reward redemption. */
    POINT_REDEMPTION_REFUND,

    /** Terminal reverses a prior rebate/coupon redemption. */
    REBATE_REFUND,

    /** Terminal reverses a prior loyalty award. */
    AWARD_REFUND
}
