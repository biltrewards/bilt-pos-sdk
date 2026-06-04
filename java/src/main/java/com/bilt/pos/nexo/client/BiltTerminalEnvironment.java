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

    /** Production terminals: {@code {Model}-{Serial}.live.pos.bilt.com}. */
    PRODUCTION("*.live.pos.bilt.com"),

    /** Staging terminals: {@code {Model}-{Serial}.staging.pos.bilt.com}. */
    STAGING("*.staging.pos.bilt.com");

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
