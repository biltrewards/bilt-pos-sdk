package com.bilt.pos.emulator.session

import java.io.File
import java.util.Properties

/**
 * Emulator configuration, mirroring the conventions of the Python nexo
 * emulator and the CLI: values come from environment variables first, then
 * from a `local.properties` found by walking up from the working directory
 * (the same file the Android tooling reads, so one place holds the secrets).
 *
 * Keys:
 * - `NEXO_PASSPHRASE` — enables message encryption when present
 * - `NEXO_KEY_ID`, `NEXO_KEY_VERSION` — encryption key identity (defaults: "emulator", 0)
 * - `NEXO_CA_CERT` — inline PEM (a .properties value is one line; encode line
 *   breaks as literal `\n`); enables TLS verification when present
 * - `NEXO_CA_BUNDLE` — path to a PEM/bundle file, alternative to the above
 * - `NEXO_HOSTNAME_PATTERN` — expected certificate SAN pattern; defaults from `NEXO_ENV`
 * - `NEXO_ENV` — `staging` (default) or `production`
 */
data class EmulatorConfig(
    val passphrase: String?,
    val keyId: String,
    val keyVersion: Int,
    val caPem: String?,
    /** Set when a CA *was* configured but couldn't be loaded (bad NEXO_CA_BUNDLE
     *  path, unreadable file) — reported as a TLS failure rather than silently
     *  degrading to "no CA configured". */
    val caError: String? = null,
    val hostnamePattern: String,
    val saleId: String = "bilt-emulator",
    val poiId: String = "EMULATOR",
    val currency: String = "USD",
) {
    val encryptionEnabled: Boolean get() = !passphrase.isNullOrBlank()

    companion object {
        private const val STAGING_PATTERN = "*.pos.staging.bilt.dev"
        private const val PRODUCTION_PATTERN = "*.live.pos.bilt.com"

        fun load(): EmulatorConfig {
            val props = loadLocalProperties()
            fun value(key: String): String? =
                System.getenv(key)?.takeIf { it.isNotBlank() }
                    ?: props.getProperty(key)?.takeIf { it.isNotBlank() }

            var caError: String? = null
            val caPem = value("NEXO_CA_CERT")?.replace("\\n", "\n")
                ?: value("NEXO_CA_BUNDLE")?.let { path ->
                    try {
                        val file = File(path)
                        if (file.isFile) {
                            file.readText()
                        } else {
                            caError = "NEXO_CA_BUNDLE points to a missing file: $path"
                            null
                        }
                    } catch (e: Exception) {
                        caError = "NEXO_CA_BUNDLE could not be read ($path): ${e.message}"
                        null
                    }
                }

            val pattern = value("NEXO_HOSTNAME_PATTERN")
                ?: if (value("NEXO_ENV").equals("production", ignoreCase = true)) {
                    PRODUCTION_PATTERN
                } else {
                    STAGING_PATTERN
                }

            return EmulatorConfig(
                passphrase = value("NEXO_PASSPHRASE"),
                keyId = value("NEXO_KEY_ID") ?: "emulator",
                keyVersion = value("NEXO_KEY_VERSION")?.toIntOrNull() ?: 0,
                caPem = caPem,
                caError = caError,
                hostnamePattern = pattern,
            )
        }

        private fun loadLocalProperties(): Properties {
            val props = Properties()
            var dir: File? = File(System.getProperty("user.dir")).absoluteFile
            repeat(5) {
                val candidate = dir?.let { File(it, "local.properties") } ?: return props
                if (candidate.isFile) {
                    candidate.inputStream().use(props::load)
                    return props
                }
                dir = dir?.parentFile
            }
            return props
        }
    }
}
