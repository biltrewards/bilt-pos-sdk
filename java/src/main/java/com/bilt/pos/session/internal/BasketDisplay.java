/*
 *    ____  _ _ _
 *   | __ )(_) | |_
 *   |  _ \| | | __|
 *   | |_) | | | |_
 *   |____/|_|_|\__|
 *
 *   Bilt POS SDK
 *
 *   Internal API — subject to change without notice.
 */
package com.bilt.pos.session.internal;

import com.bilt.pos.display.DisplayPayload;
import com.bilt.pos.display.DisplayPayloadHelper;
import com.bilt.pos.nexo.model.MessageCategoryType;
import com.bilt.pos.session.SessionException;
import com.bilt.pos.session.SessionState;
import com.bilt.pos.session.basket.Basket;
import com.bilt.pos.session.display.DisplayContext;
import com.bilt.pos.session.display.DisplayRenderer;
import com.bilt.pos.session.display.DisplayTarget;

import jakarta.xml.bind.JAXBException;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The customer-display side of a session: renders basket snapshots through
 * the configured {@link DisplayRenderer} and sends the payload as a Nexo
 * {@code DisplayRequest}. Shared by the session types that own a basket —
 * a checkout's sale basket and a reversal's refund cart go through the
 * same pipeline; a refund cart's credit lines simply render negative.
 *
 * <p>Display is best-effort throughout: renderer and wire failures are
 * logged and never interrupt the session, and an ended session skips the
 * send quietly.</p>
 */
public final class BasketDisplay {

    private static final Logger LOGGER = Logger.getLogger(BasketDisplay.class.getName());

    private final NexoExchange exchange;
    private final DisplayRenderer renderer;
    private final String currency;

    public BasketDisplay(NexoExchange exchange, DisplayRenderer renderer, String currency) {
        this.exchange = exchange;
        this.renderer = renderer;
        this.currency = currency;
    }

    /** Renders the basket and sends it to the customer display. */
    public void show(Basket basket, SessionState state) {
        DisplayContext context = new DisplayContext(state,
                exchange.router().hasExternalDisplay()
                        ? DisplayTarget.EXTERNAL : DisplayTarget.TERMINAL,
                currency);
        DisplayPayload payload;
        try {
            payload = renderer.render(basket, context);
        } catch (RuntimeException e) {
            LOGGER.log(Level.WARNING, "display renderer failed", e);
            return;
        }
        if (payload != null) {
            send(payload, state);
        }
    }

    /** Sends a custom display payload to the customer display. */
    public void send(DisplayPayload payload, SessionState state) {
        String base64;
        try {
            base64 = DisplayPayloadHelper.toBase64(payload);
        } catch (JAXBException e) {
            LOGGER.log(Level.WARNING, "failed to serialize display payload", e);
            return;
        }
        // display is best-effort and never throws, so an ended session skips
        // quietly instead of failing like the SessionResult operations do
        if (state == SessionState.ENDED) {
            LOGGER.warning("display update skipped: the session has ended");
            return;
        }
        try {
            exchange.send(MessageCategoryType.DISPLAY,
                    exchange.factory().displayRequest(base64));
        } catch (SessionException e) {
            LOGGER.log(Level.WARNING, "display update failed: " + e.getError(), e);
        }
    }
}
