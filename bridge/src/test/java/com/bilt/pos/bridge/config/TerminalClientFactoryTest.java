package com.bilt.pos.bridge.config;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.bilt.pos.nexo.client.TerminalClient;
import com.bilt.pos.nexo.sim.SimulatedTerminalClient;
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
            Optional.empty(),
            false,
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
            Optional.empty(),
            false,
            Optional.empty());

    assertNotNull(TerminalClientFactory.build(encrypted));
  }

  @Test
  void buildsSimulatedClientWithoutAnEndpoint() throws Exception {
    TerminalConfig simulated =
        BridgeConfig.parse("{\"terminal\":{\"simulated\":true}}").terminal().orElseThrow();

    TerminalClient client = TerminalClientFactory.build(simulated);

    assertInstanceOf(SimulatedTerminalClient.class, client);
  }
}
