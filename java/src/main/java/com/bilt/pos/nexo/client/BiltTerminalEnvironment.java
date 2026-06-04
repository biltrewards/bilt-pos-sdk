/*
 *    ____  _ _ _
 *   | __ )(_) | |_
 *   |  _ \| | | __|
 *   | |_) | | | |_
 *   |____/|_|_|\__|
 *
 *   Bilt POS SDK
 */
package com.bilt.pos.nexo.client;

/**
 * A Bilt POS terminal deployment environment.
 *
 * <p>Production and staging terminals are issued certificates from
 * <strong>fully separate CA hierarchies</strong> and use distinct synthetic
 * hostname domains, so a certificate from one environment can never be trusted
 * in the other. Selecting an environment here pins
 * {@link BiltNexoTerminalClient.Builder#expectedHostnamePattern(String) the
 * expected hostname pattern} to that environment's domain; the caller still
 * supplies the matching CA trust anchor via
 * {@link BiltNexoTerminalClient.Builder#trustCertificate(java.nio.file.Path)}.</p>
 */
public enum BiltTerminalEnvironment {

    /**
     * Production terminals.
     *
     * <p><strong>Unverified:</strong> this pattern comes from the original
     * design doc and has not been confirmed against a live production
     * certificate. Staging certificates were found to use
     * {@code pos.staging.bilt.dev} rather than the doc's
     * {@code staging.pos.bilt.com}, so the production domain likely differs
     * from the value below too. Confirm against a real production cert (and
     * update this) before relying on it; until then, pass the exact pattern
     * via {@link BiltNexoTerminalClient.Builder#expectedHostnamePattern(String)}.</p>
     */
    PRODUCTION("*.live.pos.bilt.com"),

    /** Staging terminals: {@code {Model}-{Serial}.pos.staging.bilt.dev}. */
    STAGING("*.pos.staging.bilt.dev");

    private final String hostnamePattern;

    BiltTerminalEnvironment(String hostnamePattern) {
        this.hostnamePattern = hostnamePattern;
    }

    /**
     * The expected certificate hostname pattern for this environment, e.g.
     * {@code "*.live.pos.bilt.com"}.
     */
    public String hostnamePattern() {
        return hostnamePattern;
    }
}
