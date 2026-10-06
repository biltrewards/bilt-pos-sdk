package com.bilt.pos.bridge.config;

import com.bilt.pos.nexo.client.BiltNexoTerminalClient;
import com.bilt.pos.nexo.security.SecurityKey;
import java.nio.file.Path;

/** Turns a {@link TerminalConfig} into the SDK client that speaks to that terminal. */
public final class TerminalClientFactory {

  private TerminalClientFactory() {}

  /**
   * Builds the client. Building does not connect; the first request does. The SDK's builder
   * enforces the trust rules ({@code trustAll} excludes a CA anchor, a CA anchor needs the
   * environment), and {@link BridgeConfig#parse} already rejected the combinations it would refuse,
   * so an {@code IllegalStateException} here means the two drifted apart.
   */
  public static BiltNexoTerminalClient build(TerminalConfig terminal) {
    BiltNexoTerminalClient.Builder builder =
        BiltNexoTerminalClient.builder().endpoint(terminal.endpoint());
    if (terminal.trustAll()) {
      builder.trustAllCertificates();
    } else {
      builder.trustCertificate(Path.of(terminal.caCertificatePath().orElseThrow()));
      builder.environment(terminal.environment().orElseThrow());
    }
    if (terminal.encryption()) {
      builder.securityKey(
          SecurityKey.builder()
              .passphrase(terminal.passphrase().orElseThrow())
              .keyIdentifier(terminal.keyId().orElseThrow())
              .keyVersion(terminal.keyVersion())
              .build());
    }
    return builder.build();
  }
}
