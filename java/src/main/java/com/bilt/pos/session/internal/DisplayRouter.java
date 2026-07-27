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

import com.bilt.pos.nexo.client.BiltNexoTerminalClient;
import com.bilt.pos.nexo.model.MessageCategoryType;

import java.util.Objects;

/**
 * Selects the client a message category is sent to.
 *
 * <p>When an external display client is configured, {@code Display} and
 * {@code Input} messages are routed to it; everything else — payments,
 * loyalty, stored value, card acquisition, and notably {@code PIN} (the
 * encrypted PIN block must come from the secure PIN pad) — goes to the
 * terminal.</p>
 */
public final class DisplayRouter {

    private final BiltNexoTerminalClient terminalClient;
    private final BiltNexoTerminalClient externalDisplayClient;

    public DisplayRouter(BiltNexoTerminalClient terminalClient,
                         BiltNexoTerminalClient externalDisplayClient) {
        this.terminalClient = Objects.requireNonNull(terminalClient, "terminalClient");
        this.externalDisplayClient = externalDisplayClient;
    }

    /** The client that should receive messages of the given category. */
    public BiltNexoTerminalClient route(MessageCategoryType category) {
        if (externalDisplayClient != null
                && (category == MessageCategoryType.DISPLAY
                    || category == MessageCategoryType.INPUT
                    || category == MessageCategoryType.INPUT_UPDATE)) {
            return externalDisplayClient;
        }
        return terminalClient;
    }

    public BiltNexoTerminalClient terminal() {
        return terminalClient;
    }

    public boolean hasExternalDisplay() {
        return externalDisplayClient != null;
    }
}
