/*
 *    ____  _ _ _
 *   | __ )(_) | |_
 *   |  _ \| | | __|
 *   | |_) | | | |_
 *   |____/|_|_|\__|
 *
 *   Bilt POS SDK
 */
package com.bilt.pos.session;

import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import java.util.logging.Logger;

/**
 * Tracking for a session's lazy operations, shared by the session types: a
 * forgotten {@code .execute()} is the most common integration mistake with
 * this API, so creating an operation while a previous one is still
 * unexecuted logs a warning.
 */
final class SessionOperations {

    private static final Logger LOGGER = Logger.getLogger(SessionOperations.class.getName());

    private final AtomicReference<String> unexecuted = new AtomicReference<>();

    /** Creates a lazy {@link SessionResult} tracked against this session. */
    <T> SessionResult<T> operation(String name, Supplier<T> body) {
        track(name);
        return new SessionResult<>(name, () -> {
            begin(name);
            return body.get();
        });
    }

    /** Records a created operation, warning when the previous one never ran. */
    void track(String name) {
        String pending = unexecuted.getAndSet(name);
        if (pending != null) {
            LOGGER.warning("session operation '" + pending + "' was created but never "
                    + "executed; did you forget to call execute(), get(), or getOrNull()?");
        }
    }

    /** Marks the named operation as executing. */
    void begin(String name) {
        unexecuted.compareAndSet(name, null);
    }
}
