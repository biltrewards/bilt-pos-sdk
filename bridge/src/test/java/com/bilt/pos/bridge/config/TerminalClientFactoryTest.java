package com.bilt.pos.bridge.config;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.Optional;
import org.junit.jupiter.api.Test;

class TerminalClientFactoryTest {

  @Test
  void buildsDevelopmentClient() {
    TerminalConfig dev =
        new TerminalConfig(
            Optional.empty(),
            Optional.empty(),
            "192.168.4.108",
            8443,
            false,
            Optional.empty(),
            Optional.empty(),
            0,
            true,
            Optional.empty(),
            Optional.empty());

    assertNotNull(TerminalClientFactory.build(dev));
  }

  @Test
  void buildsEncryptedTrustAllClient() {
    TerminalConfig encrypted =
        new TerminalConfig(
            Optional.empty(),
            Optional.empty(),
            "192.168.4.108",
            8443,
            true,
            Optional.of("passphrase"),
            Optional.of("key-1"),
            1,
            true,
            Optional.empty(),
            Optional.empty());

    assertNotNull(TerminalClientFactory.build(encrypted));
  }
}
