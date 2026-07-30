package com.bilt.pos.emulator.session

import java.util.concurrent.TimeUnit

/**
 * Best-effort terminal address discovery via adb, ported from the Python
 * emulator's `_detect_device_ip`: take the first attached device and read its
 * wlan0 IPv4. Every failure mode (no adb on PATH, no device, no wlan0 —
 * including running on Android itself, where adb doesn't exist) returns null
 * and the operator types the address by hand.
 */
object TerminalAddressDetector {

    fun detect(): String? {
        val devices = run("adb", "devices") ?: return null
        val serial = devices.lineSequence()
            .filter { it.contains("\tdevice") }
            .map { it.substringBefore('\t') }
            .firstOrNull() ?: return null

        val addr = run("adb", "-s", serial, "shell", "ip", "-f", "inet", "addr", "show", "wlan0")
            ?: return null
        return addr.lineSequence()
            .map { it.trim() }
            .firstOrNull { it.startsWith("inet ") }
            ?.split(" ")?.getOrNull(1)
            ?.substringBefore('/')
    }

    private fun run(vararg command: String): String? = try {
        val process = ProcessBuilder(*command).redirectErrorStream(true).start()
        if (!process.waitFor(2, TimeUnit.SECONDS)) {
            process.destroyForcibly()
            null
        } else if (process.exitValue() != 0) {
            null
        } else {
            process.inputStream.bufferedReader().readText()
        }
    } catch (_: Exception) {
        null
    }
}
