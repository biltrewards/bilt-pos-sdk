/*
 *    ____  _ _ _
 *   | __ )(_) | |_
 *   |  _ \| | | __|
 *   | |_) | | | |_
 *   |____/|_|_|\__|
 *
 *   Bilt POS SDK
 */
package com.bilt.pos.nexo.client

/**
 * A Bilt POS terminal deployment environment.
 *
 * Production and staging terminals are issued certificates from **fully
 * separate CA hierarchies** and use distinct synthetic hostname domains, so a
 * certificate from one environment can never be trusted in the other.
 * Selecting an environment pins the [expected hostname pattern]
 * [BiltNexoTerminalClient] to that environment's domain; the caller still
 * supplies the matching CA trust anchor via `trustedCertificates`.
 */
enum class BiltTerminalEnvironment(
    /** The expected certificate hostname pattern, e.g. `*.live.pos.bilt.com`. */
    val hostnamePattern: String
) {

    /** Production terminals: `{Model}-{Serial}.live.pos.bilt.com`. */
    PRODUCTION("*.live.pos.bilt.com"),

    /** Staging terminals: `{Model}-{Serial}.pos.staging.bilt.dev`. */
    STAGING("*.pos.staging.bilt.dev")
}
