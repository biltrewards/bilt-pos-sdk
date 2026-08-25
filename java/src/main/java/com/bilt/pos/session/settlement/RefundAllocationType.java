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

/** The destination/source being restored for a returned item during settlement. */
public enum RefundAllocationType {

    /** Return money to a payment card using a Nexo PaymentRequest(Refund). */
    CARD,

    /** Restore money to a stored value card with a StoredValue Load. */
    STORED_VALUE,

    /** Reverse a prior point/reward redemption by its original transaction reference. */
    POINT_REDEMPTION,

    /** Reverse a prior rebate/coupon redemption by its original transaction reference. */
    REBATE,

    /** Reverse a prior loyalty award by its original transaction reference. */
    AWARD
}
