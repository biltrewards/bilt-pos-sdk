package com.bilt.pos.emulator.session

import java.util.logging.Logger

/**
 * Best-effort terminal discovery via adb, ported from the Python emulator's
 * `_detect_device_ip`: take the first attached device and read its network
 * address. Every hard failure (no adb anywhere, no device — including
 * running on Android itself, where adb doesn't exist) returns null and the
 * operator types the address by hand. A device WITHOUT a network address is
 * still a detection: a USB-only terminal is reachable through the adb
 * tunnel, which needs no address.
 */
object TerminalAddressDetector {

    private val LOGGER = Logger.getLogger("com.bilt.pos.emulator.TerminalAddressDetector")

    /** An attached device, and its network address when it has one. */
    data class Detection(val serial: String, val address: String?)

    fun detect(): Detection? {
        val serial = try {
            Adb.devices().firstOrNull()
        } catch (_: Exception) {
            null
        } ?: return null

        // all interfaces, not just wlan0: a docked terminal may sit on
        // ethernet, and a USB-only one has no global address at all
        val address = Adb.runOrNull("-s", serial, "shell", "ip", "-f", "inet", "addr", "show")
            ?.let(::parseGlobalIpv4)
        if (address == null) {
            LOGGER.info("device $serial has no global IPv4 — reachable via the adb tunnel only")
        }
        return Detection(serial, address)
    }

    /** The first global-scope IPv4 from `ip -f inet addr show` output —
     *  loopback and link-local addresses carry other scopes and are
     *  skipped. */
    internal fun parseGlobalIpv4(output: String): String? =
        output.lineSequence()
            .map { it.trim() }
            .filter { it.startsWith("inet ") && "scope global" in it }
            .firstNotNullOfOrNull { it.split(" ").getOrNull(1)?.substringBefore('/') }
}
