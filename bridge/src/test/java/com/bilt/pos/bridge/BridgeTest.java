package com.bilt.pos.bridge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bilt.pos.bridge.config.BridgeConfig;
import com.bilt.pos.bridge.config.FileBridgeConfigSource;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * End-to-end smoke: config file created, listener up, health answers, reload keeps a good config.
 */
class BridgeTest {

  @Test
  void startsFromStarterConfigAndSurvivesBadReload(@TempDir Path dir) throws Exception {
    Path file = dir.resolve("config.json");
    // a free port keeps the test independent of whatever runs on 48333
    int free;
    try (java.net.ServerSocket s = new java.net.ServerSocket(0)) {
      free = s.getLocalPort();
    }
    Files.writeString(
        file,
        "{\"port\": "
            + free
            + ", \"terminals\": [{\"poiId\": \"T1\", \"host\": \"10.0.0.1\", \"trustAll\": true}]}");

    try (Bridge bridge = new Bridge(new FileBridgeConfigSource(file))) {
      bridge.start();
      BridgeStatus status = bridge.status();
      assertEquals(free, status.port());
      assertEquals(1, status.terminalCount());
      assertEquals(List.of("T1"), bridge.terminalIds());
      assertTrue(bridge.terminalClient("T1").isPresent());

      HttpResponse<String> res =
          HttpClient.newHttpClient()
              .send(
                  HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + free + "/health"))
                      .build(),
                  HttpResponse.BodyHandlers.ofString());
      assertEquals(200, res.statusCode());
      assertTrue(res.body().contains("\"terminals\":1"), res.body());

      Files.writeString(file, "{\"port\": \"oops\"}");
      bridge.reload();
      assertTrue(bridge.lastConfigError().isPresent());
      assertEquals(1, bridge.status().terminalCount(), "previous config stays in force");

      Files.writeString(
          file, "{\"port\": " + free + ", \"allowedOrigins\": [\"https://a.example\"]}");
      bridge.reload();
      assertTrue(bridge.lastConfigError().isEmpty());
      assertEquals(0, bridge.status().terminalCount());
      assertEquals(List.of("https://a.example"), bridge.config().allowedOrigins());
    }
  }

  @Test
  void writesStarterFileWhenMissing(@TempDir Path dir) throws Exception {
    Path file = dir.resolve("nested").resolve("config.json");
    FileBridgeConfigSource source = new FileBridgeConfigSource(file);

    BridgeConfig config = source.load();

    assertTrue(Files.exists(file));
    assertEquals(BridgeConfig.DEFAULT_PORT, config.port());
    assertEquals(1, config.terminals().size());
    assertTrue(Files.readString(file).contains("\"_comment\""));
  }
}
