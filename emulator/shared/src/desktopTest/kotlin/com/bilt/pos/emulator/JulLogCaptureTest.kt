package com.bilt.pos.emulator

import com.bilt.pos.emulator.session.JulLogCapture
import java.util.concurrent.CopyOnWriteArrayList
import java.util.logging.Logger
import kotlin.test.Test
import kotlin.test.assertTrue

class JulLogCaptureTest {

    @Test
    fun capturesSdkLogsIncludingFineLevel() {
        val received = CopyOnWriteArrayList<String>()
        JulLogCapture.install(received::add)

        val logger = Logger.getLogger("com.bilt.pos.test.capture")
        // the SDK's client logs its diagnostics at FINE — the level the
        // default JUL configuration silently drops
        logger.fine("fine diagnostic line")
        logger.warning("warning line")
        logger.severe("severe line")

        assertTrue(received.any { "fine diagnostic line" in it }, "FINE records must be captured")
        assertTrue(received.any { "warning line" in it })
        assertTrue(received.any { "severe line" in it })
    }

    @Test
    fun expandsThrowablesToStackTraces() {
        val received = CopyOnWriteArrayList<String>()
        JulLogCapture.install(received::add)

        Logger.getLogger("com.bilt.pos.test.capture").log(
            java.util.logging.Level.WARNING,
            "request failed",
            IllegalStateException("boom"),
        )

        assertTrue(
            received.any { "request failed" in it && "IllegalStateException" in it && "boom" in it },
            "throwable should be expanded to a stack trace, got: $received",
        )
    }
}
