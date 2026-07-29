/*
 *    ____  _ _ _
 *   | __ )(_) | |_
 *   |  _ \| | | __|
 *   | |_) | | | |_
 *   |____/|_|_|\__|
 *
 *   Bilt POS SDK
 */
package com.bilt.pos.session.identity;

/** Outcome of a member identification attempt. */
public enum IdentifyStatus {

    /** A member was identified; the result carries their data. */
    FOUND,

    /** No member matched the captured identifier. */
    NOT_FOUND,

    /** The member exists but is suspended or inactive. */
    SUSPENDED,

    /** The customer cancelled the lookup on the terminal. */
    CANCELLED,

    /** The lookup failed for another reason. */
    ERROR
}
