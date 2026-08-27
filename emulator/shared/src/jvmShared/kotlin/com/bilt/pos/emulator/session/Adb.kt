package com.bilt.pos.emulator.session

import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Locates and runs the adb executable for the emulator's device
 * integrations — the address autodetect and the adb tunnel.
 *
 * A desktop app does not inherit the shell's PATH (a Finder-launched app
 * sees only the system default), so a bare `adb` often fails even though
 * the operator's terminal finds one. The resolver checks the conventional
 * SDK locations first and falls back to PATH lookup.
 */
internal object Adb {

    /** The resolved adb executable — a full path when a conventional
     *  location has one, else plain `adb` for PATH lookup. */
    val executable: String by lazy {
        val home = System.getProperty("user.home")
        listOfNotNull(
            System.getenv("ANDROID_HOME")?.let { "$it/platform-tools/adb" },
            System.getenv("ANDROID_SDK_ROOT")?.let { "$it/platform-tools/adb" },
            // the default SDK locations on macOS and Linux
            "$home/Library/Android/sdk/platform-tools/adb",
            "$home/Android/Sdk/platform-tools/adb",
            "/opt/homebrew/bin/adb",
            "/usr/local/bin/adb",
        ).firstOrNull { File(it).canExecute() } ?: "adb"
    }

    /**
     * Runs adb with [args] and returns its combined output. The timeout is
     * generous because the first call may have to start the adb server.
     *
     * @throws IllegalStateException when adb is missing, times out, or
     *         exits nonzero — the message carries adb's own output
     */
    fun run(vararg args: String): String {
        val command = listOf(executable) + args
        val process = try {
            ProcessBuilder(command).redirectErrorStream(true).start()
        } catch (e: Exception) {
            throw IllegalStateException(
                "adb could not be started ($executable — install platform-tools " +
                    "or set ANDROID_HOME): ${e.message}"
            )
        }
        // wait before reading: adb output is tiny, so the pipe can't fill
        // and deadlock the wait
        if (!process.waitFor(15, TimeUnit.SECONDS)) {
            process.destroyForcibly()
            throw IllegalStateException("adb timed out: ${command.joinToString(" ")}")
        }
        val output = process.inputStream.bufferedReader().readText()
        if (process.exitValue() != 0) {
            throw IllegalStateException(
                "adb failed (${args.joinToString(" ")}): ${output.trim()}"
            )
        }
        return output
    }

    /** [run] for best-effort callers: null on any failure. */
    fun runOrNull(vararg args: String): String? = try {
        run(*args)
    } catch (_: Exception) {
        null
    }

    /**
     * The attached-device serials, retried once when the call had to start
     * the adb server — USB devices can take a moment to enumerate after
     * that, and an empty first answer would read as "no device".
     */
    fun devices(): List<String> {
        val first = run("devices")
        val serials = parseSerials(first)
        if (serials.isNotEmpty() || "daemon" !in first) {
            return serials
        }
        Thread.sleep(1_500)
        return parseSerials(run("devices"))
    }

    /** The attached-device serials from `adb devices` output. Tolerates
     *  tabs or spaces between serial and state, and skips the header, the
     *  daemon-start banner, and offline/unauthorized devices. */
    internal fun parseSerials(devicesOutput: String): List<String> =
        devicesOutput.lineSequence()
            .map { it.trim().split(Regex("\\s+")) }
            .filter { it.size >= 2 && it[1] == "device" }
            .map { it[0] }
            .toList()
}
