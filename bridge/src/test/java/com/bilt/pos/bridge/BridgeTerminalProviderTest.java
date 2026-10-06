package com.bilt.pos.bridge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.bilt.pos.bridge.config.FileBridgeConfigSource;
import com.bilt.pos.host.TerminalInfo;
import java.net.ServerSocket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BridgeTerminalProviderTest {

  @Test
  void exposesConfiguredTerminalsAndFollowsReloads(@TempDir Path dir) throws Exception {
    Path file = dir.resolve("config.json");
    int free;
    try (ServerSocket s = new ServerSocket(0)) {
      free = s.getLocalPort();
    }
    Files.writeString(
        file,
        "{\"port\": "
            + free
            + ", \"terminals\": [{\"poiId\": \"T1\", \"host\": \"10.0.0.1\", \"trustAll\": true}]}");

    try (Bridge bridge = new Bridge(new FileBridgeConfigSource(file))) {
      bridge.start();
      BridgeTerminalProvider provider = new BridgeTerminalProvider(bridge);

      assertEquals(List.of(TerminalInfo.of("T1")), provider.terminals());
      assertNotNull(provider.forPoi("T1"));
      assertNull(provider.forPoi("T2"));

      Files.writeString(
          file,
          "{\"port\": "
              + free
              + ", \"terminals\": [{\"poiId\": \"T2\", \"host\": \"10.0.0.2\", \"trustAll\": true}]}");
      bridge.reload();

      assertEquals(List.of(TerminalInfo.of("T2")), provider.terminals());
      assertNull(provider.forPoi("T1"));
      assertNotNull(provider.forPoi("T2"));
    }
  }
}
