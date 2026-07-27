/*
 *    ____  _ _ _
 *   | __ )(_) | |_
 *   |  _ \| | | __|
 *   | |_) | | | |_
 *   |____/|_|_|\__|
 *
 *   Bilt POS SDK
 */
package com.bilt.pos.session.input;

import java.time.Duration;

/**
 * Options for {@code requestConfirmation}.
 *
 * <p>The terminal confirmation screen has two buttons; custom labels replace
 * the default confirm/cancel texts. For three or more choices use
 * {@code requestMenuEntry} instead.</p>
 */
public final class ConfirmationOptions {

    private final String confirmButton;
    private final String cancelButton;
    private final Duration timeout;

    private ConfirmationOptions(String confirmButton, String cancelButton, Duration timeout) {
        this.confirmButton = confirmButton;
        this.cancelButton = cancelButton;
        this.timeout = timeout;
    }

    public static ConfirmationOptions defaults() {
        return new ConfirmationOptions(null, null, null);
    }

    /**
     * Custom button labels: the first is the confirm button, the optional
     * second the cancel button.
     *
     * @throws IllegalArgumentException for more than two labels — use
     *         {@code requestMenuEntry} for multi-choice prompts
     */
    public static ConfirmationOptions withButtons(String... buttons) {
        if (buttons.length == 0 || buttons.length > 2) {
            throw new IllegalArgumentException("expected 1 or 2 button labels; for "
                    + buttons.length + " choices use requestMenuEntry");
        }
        return new ConfirmationOptions(buttons[0],
                buttons.length > 1 ? buttons[1] : null, null);
    }

    /** Returns a copy with a response timeout. */
    public ConfirmationOptions withTimeout(Duration timeout) {
        return new ConfirmationOptions(confirmButton, cancelButton, timeout);
    }

    public String getConfirmButton() {
        return confirmButton;
    }

    public String getCancelButton() {
        return cancelButton;
    }

    public Duration getTimeout() {
        return timeout;
    }
}
