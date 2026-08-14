package com.bilt.pos.emulator.session

import java.util.concurrent.TimeUnit

/**
 * A localhost tunnel to the terminal's nexo port over `adb forward`: the
 * emulator connects to 127.0.0.1 and the adb server carries the traffic to
 * the device. This is the way around macOS denying the JVM process
 * local-network access — adb already holds that permission (or reaches the
 * device over USB), while the JVM only ever touches loopback.
 *
 * adb rather than ssh because the terminal is an Android device: it runs no
 * sshd, but it is already attached via adb — the address autodetection
 * ([TerminalAddressDetector]) depends on that.
 *
 * Forwards live in the adb server, not in this process, so an opened tunnel
 * must be [close]d — an unremoved forward outlives the emulator.
 */
object AdbTunnel {

    /** The terminal's nexo listener; what the forward targets on the device. */
    private const val DEVICE_PORT = 8443

    /** An open forward: connect to 127.0.0.1:[localPort]; [serial] is the
     *  adb device it was created against, needed to remove it. */
    data class Tunnel(val localPort: Int, val serial: String)

    /**
     * Forwards a fresh local port (`adb forward tcp:0`) to port 8443 of the
     * device matching [address] — the one whose wifi-adb serial carries the
     * address, or the single attached device otherwise.
     *
     * @throws IllegalStateException when adb is unavailable, no attached
     *   device matches, or the forward fails; the message carries adb's
     *   output so the event log explains the failure
     */
    fun open(address: String): Tunnel {
        val serials = parseSerials(run("adb", "devices"))
        val serial = pickSerial(serials, address) ?: throw IllegalStateException(
            if (serials.isEmpty()) {
                "no adb device attached — plug the terminal in (or `adb connect $address`)"
            } else {
                "several adb devices attached and none matches $address: $serials"
            }
        )
        val output = run("adb", "-s", serial, "forward", "tcp:0", "tcp:$DEVICE_PORT")
        val port = parseForwardedPort(output) ?: throw IllegalStateException(
            "adb forward did not return a port: \"${output.trim()}\""
        )
        return Tunnel(port, serial)
    }

    /** Removes the forward from the adb server. Best-effort: a failure
     *  (device already detached, adb gone) leaves nothing to clean up. */
    fun close(tunnel: Tunnel) {
        try {
            run("adb", "-s", tunnel.serial, "forward", "--remove", "tcp:${tunnel.localPort}")
        } catch (_: Exception) {
        }
    }

    /** The attached-device serials from `adb devices` output. */
    internal fun parseSerials(devicesOutput: String): List<String> =
        devicesOutput.lineSequence()
            .filter { it.contains("\tdevice") }
            .map { it.substringBefore('\t') }
            .toList()

    /** The serial to forward through: the device whose wifi-adb serial
     *  ("<ip>:5555") carries [address], else the single attached device —
     *  a USB-only terminal has an opaque serial its wlan address never
     *  matches. Null when several devices are attached and none matches. */
    internal fun pickSerial(serials: List<String>, address: String): String? =
        serials.firstOrNull { it == address || it.substringBeforeLast(':') == address }
            ?: serials.singleOrNull()

    /** The allocated local port from `adb forward tcp:0` output (the port
     *  number on its own line). */
    internal fun parseForwardedPort(output: String): Int? =
        output.trim().lineSequence().lastOrNull()?.trim()?.toIntOrNull()

    private fun run(vararg command: String): String {
        val process = try {
            ProcessBuilder(*command).redirectErrorStream(true).start()
        } catch (e: Exception) {
            throw IllegalStateException("adb could not be started (is it on PATH?): ${e.message}")
        }
        // wait before reading, like TerminalAddressDetector: adb output is
        // tiny, so the pipe can't fill and deadlock the wait
        if (!process.waitFor(5, TimeUnit.SECONDS)) {
            process.destroyForcibly()
            throw IllegalStateException("adb timed out: ${command.joinToString(" ")}")
        }
        val output = process.inputStream.bufferedReader().readText()
        if (process.exitValue() != 0) {
            throw IllegalStateException(
                "adb failed (${command.joinToString(" ")}): ${output.trim()}"
            )
        }
        return output
    }
}
