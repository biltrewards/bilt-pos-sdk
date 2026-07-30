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

/**
 * Encodes the Bilt session lifecycle signals carried in the
 * {@code ServiceIdentification} field of a Nexo {@code AdminRequest} — the
 * spec allows "a name or CSV path", so the signal is a versioned CSV:
 *
 * <pre>
 * BiltSession,Start,v1,&lt;sessionId&gt;
 * BiltSession,End,v1,&lt;sessionId&gt;
 * </pre>
 *
 * <p>Start announces the session; End tells the terminal to discard the
 * session-scoped data it accumulated. This format is a contract with the
 * terminal firmware — change it only in lockstep, bumping the version.</p>
 */
public final class SessionSignalCodec {

    private static final String PREFIX = "BiltSession";
    private static final String VERSION = "v1";

    private SessionSignalCodec() {
    }

    /** The Start signal for the given session. */
    public static String start(String sessionId) {
        return signal("Start", sessionId);
    }

    /** The End signal for the given session. */
    public static String end(String sessionId) {
        return signal("End", sessionId);
    }

    private static String signal(String action, String sessionId) {
        return PREFIX + ',' + action + ',' + VERSION + ',' + sessionId;
    }
}
