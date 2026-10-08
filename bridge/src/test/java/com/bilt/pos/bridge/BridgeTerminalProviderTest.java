package com.bilt.pos.bridge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.bilt.pos.bridge.config.FileBridgeConfigSource;
import com.bilt.pos.host.TerminalInfo;
import com.bilt.pos.nexo.client.TerminalClient;
import java.net.ServerSocket;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BridgeTerminalProviderTest {

  @Test
  void exposesTheConfiguredTerminalAndFollowsReloads(@TempDir Path dir) throws Exception {
    Path file = dir.resolve("config.json");
    int free;
    try (ServerSocket s = new ServerSocket(0)) {
      free = s.getLocalPort();
    }
    Files.writeString(
        file,
        "{\"port\": "
            + free
            + ", \"terminal\": {\"label\": \"Lane 1\", \"model\": \"VictaLane\","
            + " \"host\": \"10.0.0.1\", \"trustAll\": true}}");

    try (Bridge bridge = new Bridge(new FileBridgeConfigSource(file))) {
      bridge.start();
      BridgeTerminalProvider provider = new BridgeTerminalProvider(bridge);

      assertEquals(TerminalInfo.of("Lane 1", "VictaLane"), provider.info());
      TerminalClient first = provider.client();
      assertNotNull(first);

      Files.writeString(
          file,
          "{\"port\": " + free + ", \"terminal\": {\"host\": \"10.0.0.2\", \"trustAll\": true}}");
      bridge.reload();

      assertEquals(TerminalInfo.of(null, null), provider.info());
      assertNotNull(provider.client());
      assertNotSame(first, provider.client());

      Files.writeString(file, "{\"port\": " + free + "}");
      bridge.reload();

      assertNull(provider.client());
    }
  }
}
