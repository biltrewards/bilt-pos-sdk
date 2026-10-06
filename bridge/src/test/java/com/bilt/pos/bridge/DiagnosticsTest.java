package com.bilt.pos.bridge;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bilt.pos.bridge.config.BridgeConfig;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class DiagnosticsTest {

  @Test
  void passphraseNeverAppearsEvenWhenLogged() throws Exception {
    String secret = "correct-horse-battery-staple";
    BridgeConfig config =
        BridgeConfig.parse(
            "{\"allowedOrigins\": [\"https://pos.example.com\"], \"terminals\": [{\"poiId\": \"T1\","
                + " \"host\": \"10.0.0.9\", \"trustAll\": true, \"encryption\": true,"
                + " \"passphrase\": \""
                + secret
                + "\", \"keyId\": \"key-7\"}]}");
    BridgeStatus status = new BridgeStatus("0.30.0", "0.30.0", "127.0.0.1", 48333, 1, 0, false);
    AppDirs dirs = AppDirs.detect("Mac OS X", Path.of("/Users/cashier"), Map.of(), null);
    List<String> log =
        List.of(
            "2026-10-05 10:00:00.000 INFO Bridge - Terminal T1",
            "2026-10-05 10:00:01.000 SEVERE Nexo - handshake failed for passphrase " + secret);

    String text =
        Diagnostics.render(
            status,
            Optional.of(config),
            Optional.of("/Users/cashier/config.json"),
            Optional.of("terminals[1].host is required"),
            dirs,
            log);

    assertFalse(text.contains(secret), text);
    assertTrue(text.contains("\"passphrase\" : \"***\""), text);
    assertTrue(text.contains("handshake failed for passphrase ***"), text);
    assertTrue(text.contains("bridge version: 0.30.0"), text);
    assertTrue(text.contains("Listening on 127.0.0.1:48333"), text);
    assertTrue(text.contains("key-7"), text);
    assertTrue(text.contains("https://pos.example.com"), text);
    assertTrue(text.contains("last config error: terminals[1].host is required"), text);
    assertTrue(text.contains("/Users/cashier/Library/Logs/Bilt Terminal Bridge"), text);
  }

  @Test
  void rendersWithoutConfigWhenLoadFailed() {
    BridgeStatus status = new BridgeStatus("0.30.0", "0.30.0", "127.0.0.1", -1, 0, 0, false);
    AppDirs dirs = AppDirs.detect("Linux", Path.of("/home/c"), Map.of(), null);

    String text =
        Diagnostics.render(
            status, Optional.empty(), Optional.empty(), Optional.of("boom"), dirs, List.of());

    assertTrue(text.contains("(not loaded)"), text);
    assertTrue(text.contains("Not listening"), text);
  }
}
