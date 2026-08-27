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
import com.bilt.pos.session.SessionError;
import com.bilt.pos.session.SessionErrorCode;
import com.bilt.pos.session.SessionException;
import com.bilt.pos.session.SessionState;
import com.bilt.pos.session.basket.Basket;
import com.bilt.pos.session.display.DisplayContext;
import com.bilt.pos.session.display.DisplayRenderer;
import com.bilt.pos.session.display.DisplayTarget;

import jakarta.xml.bind.JAXBException;

/**
 * The customer-display side of a session: renders basket snapshots through
 * the configured {@link DisplayRenderer} and sends the payload as a Nexo
 * {@code DisplayRequest}. Sale lines and return credit lines go through the
 * same pipeline; credit lines simply render negative.
 *
 * <p>Failures — renderer, serialization, or wire — surface as
 * {@link SessionException}; whether a display update is best-effort is the
 * caller's policy, not this class's.</p>
 */
public final class BasketDisplay {

    private final NexoExchange exchange;
    private final DisplayRenderer renderer;
    private final String currency;

    public BasketDisplay(NexoExchange exchange, DisplayRenderer renderer, String currency) {
        this.exchange = exchange;
        this.renderer = renderer;
        this.currency = currency;
    }

    /**
     * Renders the basket and sends it to the customer display. A renderer
     * returning {@code null} means "nothing to show" and skips the send.
     */
    public void show(Basket basket, SessionState state) {
        DisplayContext context = new DisplayContext(state,
                exchange.router().hasExternalDisplay()
                        ? DisplayTarget.EXTERNAL : DisplayTarget.TERMINAL,
                currency);
        DisplayPayload payload;
        try {
            payload = renderer.render(basket, context);
        } catch (RuntimeException e) {
            throw new SessionException(new SessionError(SessionErrorCode.UNKNOWN,
                    "the display renderer failed: " + e.getMessage(), null, e));
        }
        if (payload != null) {
            send(payload);
        }
    }

    /** Sends a custom display payload to the customer display. */
    public void send(DisplayPayload payload) {
        String base64;
        try {
            base64 = DisplayPayloadHelper.toBase64(payload);
        } catch (JAXBException e) {
            throw new SessionException(new SessionError(SessionErrorCode.UNKNOWN,
                    "failed to serialize the display payload", null, e));
        }
        exchange.send(MessageCategoryType.DISPLAY,
                exchange.factory().displayRequest(base64));
    }
}
