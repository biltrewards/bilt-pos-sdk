/*
 *    ____  _ _ _
 *   | __ )(_) | |_
 *   |  _ \| | | __|
 *   | |_) | | | |_
 *   |____/|_|_|\__|
 *
 *   Bilt POS SDK
 */
package com.bilt.pos.session.display;

/** The device a display payload is rendered on. */
public enum DisplayTarget {

    /** The payment terminal's built-in customer display. */
    TERMINAL,

    /** A separate external display device driven by its own client. */
    EXTERNAL
}
