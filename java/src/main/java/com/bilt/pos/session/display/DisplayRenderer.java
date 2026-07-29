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

import com.bilt.pos.display.DisplayPayload;
import com.bilt.pos.session.basket.Basket;

/**
 * Renders a basket into the display payload shown on the customer display.
 *
 * <p>The session ships with a default renderer that produces the standard
 * itemised virtual receipt; supply a custom implementation via
 * {@code CheckoutSession.Builder#displayRenderer} to override it. Rarely
 * necessary.</p>
 */
@FunctionalInterface
public interface DisplayRenderer {

    /** Produces the payload to display for the given basket state. */
    DisplayPayload render(Basket basket, DisplayContext context);
}
