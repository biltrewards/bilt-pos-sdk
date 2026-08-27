package com.bilt.pos.emulator.session

import java.util.concurrent.TimeUnit
import java.util.logging.Logger

/**
 * Locates and runs the adb executable for the emulator's device
 * integrations — the address autodetect and the adb tunnel.
 *
 * Resolution prefers PATH: that is the adb the operator's terminal uses,
 * and running a DIFFERENT install (a second SDK copy) would kill their adb
 * server over the version mismatch and leave devices enumerating late. The
 * conventional SDK locations are fallbacks for launches that don't inherit
 * the shell's PATH (a Finder-launched app sees only the system default).
 * Every candidate is probed with `adb version` — which does not start the
 * server — so the resolution log states what actually runs.
 *
 * Logs through JUL under `com.bilt.pos`, so the emulator's Detailed tab
 * carries the whole trail: the resolved executable, every command, and the
 * raw output.
 */
internal object Adb {

    private val LOGGER = Logger.getLogger("com.bilt.pos.emulator.Adb")

    /** The resolved adb executable: the first candidate whose
     *  `adb version` answers — PATH first, then the SDK locations. */
    val executable: String by lazy {
        val home = System.getProperty("user.home")
        val candidates = listOf("adb") + listOfNotNull(
            System.getenv("ANDROID_HOME")?.let { "$it/platform-tools/adb" },
            System.getenv("ANDROID_SDK_ROOT")?.let { "$it/platform-tools/adb" },
            // the default SDK locations on macOS and Linux
            "$home/Library/Android/sdk/platform-tools/adb",
            "$home/Android/Sdk/platform-tools/adb",
            "/opt/homebrew/bin/adb",
            "/usr/local/bin/adb",
        )
        for (candidate in candidates.distinct()) {
            val version = probe(candidate)
            if (version != null) {
                LOGGER.info("using adb: $candidate ($version)")
                return@lazy candidate
            }
            LOGGER.fine("adb candidate not usable: $candidate")
        }
        LOGGER.warning(
            "no usable adb found — tried PATH, ANDROID_HOME, ANDROID_SDK_ROOT, " +
                "and the default SDK locations; install platform-tools or set ANDROID_HOME"
        )
        "adb"
    }

    /** First line of `[candidate] version`, or null when it can't run. */
    private fun probe(candidate: String): String? = try {
        val process = ProcessBuilder(candidate, "version").redirectErrorStream(true).start()
        if (!process.waitFor(5, TimeUnit.SECONDS)) {
            process.destroyForcibly()
            null
        } else {
            process.inputStream.bufferedReader().readText()
                .takeIf { process.exitValue() == 0 }
                ?.lineSequence()?.firstOrNull()?.trim()
        }
    } catch (_: Exception) {
        null
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
        LOGGER.fine("running: ${command.joinToString(" ")}")
        val process = try {
            ProcessBuilder(command).redirectErrorStream(true).start()
        } catch (e: Exception) {
            LOGGER.warning("adb could not be started ($executable): ${e.message}")
            throw IllegalStateException(
                "adb could not be started ($executable — install platform-tools " +
                    "or set ANDROID_HOME): ${e.message}"
            )
        }
        // wait before reading: adb output is tiny, so the pipe can't fill
        // and deadlock the wait
        if (!process.waitFor(15, TimeUnit.SECONDS)) {
            process.destroyForcibly()
            LOGGER.warning("adb timed out: ${command.joinToString(" ")}")
            throw IllegalStateException("adb timed out: ${command.joinToString(" ")}")
        }
        val output = process.inputStream.bufferedReader().readText()
        LOGGER.fine("adb output: ${output.trim().ifEmpty { "(none)" }}")
        if (process.exitValue() != 0) {
            LOGGER.warning("adb failed (${args.joinToString(" ")}): ${output.trim()}")
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
        if (serials.isNotEmpty()) {
            LOGGER.info("adb devices: $serials")
            return serials
        }
        if ("daemon" !in first) {
            LOGGER.info("adb devices found none; raw output: ${first.trim().ifEmpty { "(none)" }}")
            return serials
        }
        LOGGER.info("adb server just started — retrying the device listing")
        Thread.sleep(1_500)
        val retried = run("devices")
        return parseSerials(retried).also {
            LOGGER.info(
                if (it.isEmpty()) "adb devices found none after retry; raw output: ${retried.trim()}"
                else "adb devices: $it"
            )
        }
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
