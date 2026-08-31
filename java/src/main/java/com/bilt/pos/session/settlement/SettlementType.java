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

/** How a settlement containing sale, credit, and return lines moves money. */
public enum SettlementType {

    /**
     * Execute return refund allocations first, then charge sales less credits.
     * This is the default and preserves the original settlement behavior.
     */
    REFUND_THEN_CHARGE,

    /**
     * Move only the difference: charge when the signed basket total is
     * positive, refund when it is negative, and move no money when it is zero.
     */
    NET
}
