package com.bilt.pos.bridge.config;

import com.bilt.pos.nexo.client.BiltTerminalEnvironment;
import java.util.Optional;

/**
 * One terminal the bridge can reach. {@code trustAll} and {@code encryption=false} are development
 * terminal settings; production terminals need the CA certificate, the environment and the payload
 * passphrase.
 *
 * <p>The record is a plain value, so the passphrase must never be printed through {@link
 * #toString()}; it is omitted there and from {@link #redacted()}.
 */
public record TerminalConfig(
    String poiId,
    String host,
    int port,
    boolean encryption,
    Optional<String> passphrase,
    Optional<String> keyId,
    int keyVersion,
    boolean trustAll,
    Optional<String> caCertificatePath,
    Optional<BiltTerminalEnvironment> environment) {

  /** The terminal's Nexo endpoint URL. */
  public String endpoint() {
    return "https://" + host + ":" + port + "/nexo";
  }

  /** A copy with the passphrase replaced by a marker, safe for diagnostics. */
  public TerminalConfig redacted() {
    return new TerminalConfig(
        poiId,
        host,
        port,
        encryption,
        passphrase.map(p -> "***"),
        keyId,
        keyVersion,
        trustAll,
        caCertificatePath,
        environment);
  }

  @Override
  public String toString() {
    return "TerminalConfig{"
        + "poiId="
        + poiId
        + ", endpoint="
        + endpoint()
        + ", encryption="
        + encryption
        + ", trustAll="
        + trustAll
        + ", environment="
        + environment.map(Enum::name).orElse("-")
        + "}";
  }
}
