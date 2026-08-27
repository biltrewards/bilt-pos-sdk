package com.bilt.pos.emulator.session

/**
 * Best-effort terminal address discovery via adb, ported from the Python
 * emulator's `_detect_device_ip`: take the first attached device and read its
 * wlan0 IPv4. Every failure mode (no adb anywhere, no device, no wlan0 —
 * including running on Android itself, where adb doesn't exist) returns null
 * and the operator types the address by hand. [Adb] resolves the executable
 * beyond PATH, which a desktop app may not inherit from the shell.
 */
object TerminalAddressDetector {

    fun detect(): String? {
        val serial = try {
            Adb.devices().firstOrNull()
        } catch (_: Exception) {
            null
        } ?: return null

        val addr = Adb.runOrNull("-s", serial, "shell", "ip", "-f", "inet", "addr", "show", "wlan0")
            ?: return null
        return addr.lineSequence()
            .map { it.trim() }
            .firstOrNull { it.startsWith("inet ") }
            ?.split(" ")?.getOrNull(1)
            ?.substringBefore('/')
    }
}
