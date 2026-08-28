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

/** How a settlement containing both sale and return lines moves money. */
public enum SettlementType {

    /**
     * Execute the return allocations first, then charge the sale portion.
     * This is the default and preserves the original settlement behavior.
     */
    REFUND_THEN_CHARGE,

    /**
     * Move only the difference: charge when the signed basket total is
     * positive, refund when it is negative, and move no money when it is zero.
     */
    NET
}
