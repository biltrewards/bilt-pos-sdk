package com.bilt.pos.host;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import com.bilt.pos.nexo.sim.SimulatedTerminalClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The dev host answers CORS for every origin unless its config file narrows them. */
class DevMainTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  @Test
  void everyOriginIsAllowedByDefault() throws Exception {
    assertEquals(List.of("*"), DevMain.allowedOrigins(MAPPER.readTree("{}")));
  }

  @Test
  void theConfigFileCanNarrowTheOrigins() throws Exception {
    assertEquals(
        List.of("https://pos.example.com"),
        DevMain.allowedOrigins(
            MAPPER.readTree("{\"allowedOrigins\":[\"https://pos.example.com\"]}")));
  }

  @Test
  void simulatedTerminalUsesItsFixtureAndReportsReachable(@TempDir Path directory)
      throws Exception {
    Path members = directory.resolve("members.json");
    Files.writeString(
        members,
        "{\"members\":[{\"memberId\":\"custom-member\",\"accountId\":\"custom-account\","
            + "\"phone\":\"5550001\",\"firstName\":\"Test\",\"lastName\":\"Member\","
            + "\"tierName\":\"Test\",\"points\":42,\"rewards\":[]}]}");
    ObjectNode terminalConfig = MAPPER.createObjectNode();
    terminalConfig.put("simulated", true);
    terminalConfig.put("members", members.toString());
    terminalConfig.put("label", "Lane 3");
    terminalConfig.put("model", "Virtual");
    ObjectNode config = MAPPER.createObjectNode();
    config.set("terminal", terminalConfig);

    TerminalClientProvider provider = DevMain.terminal(config);

    SimulatedTerminalClient terminal =
        assertInstanceOf(SimulatedTerminalClient.class, provider.client());
    assertEquals(42, terminal.points("custom-member"));
    assertEquals("Lane 3", provider.info().label());
    assertEquals("Virtual", provider.info().model());
    assertEquals(true, provider.info().reachable());

    TerminalClientProvider defaultLabel =
        DevMain.terminal(MAPPER.readTree("{\"terminal\":{\"simulated\":true}}"));
    assertEquals("Simulated terminal", defaultLabel.info().label());
  }
}
