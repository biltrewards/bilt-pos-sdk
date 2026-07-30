package com.bilt.pos.emulator.session

import java.util.concurrent.atomic.AtomicBoolean
import java.util.logging.Handler
import java.util.logging.Level
import java.util.logging.LogRecord
import java.util.logging.Logger

/**
 * Captures java.util.logging output — the SDK logs through JUL from the
 * client, session, and payment internals — and feeds each record to a sink
 * for the emulator's Detailed log tab. Throwables are expanded to full stack
 * traces, which the curated event log deliberately doesn't show.
 */
object JulLogCapture {

    private val installed = AtomicBoolean(false)

    @Volatile
    private var sink: ((String) -> Unit)? = null

    /** Idempotent; a later call just redirects the sink (e.g. a recreated controller). */
    fun install(newSink: (String) -> Unit) {
        sink = newSink
        if (!installed.compareAndSet(false, true)) {
            return
        }
        Logger.getLogger("").addHandler(object : Handler() {
            override fun publish(record: LogRecord) {
                if (record.level.intValue() < Level.INFO.intValue()) {
                    return
                }
                val message = buildString {
                    append(record.level.name)
                    append(' ')
                    append(record.loggerName ?: "?")
                    append(": ")
                    append(record.message ?: "")
                    record.thrown?.let {
                        append('\n')
                        append(it.stackTraceToString())
                    }
                }
                sink?.invoke(message)
            }

            override fun flush() = Unit

            override fun close() = Unit
        })
    }
}
