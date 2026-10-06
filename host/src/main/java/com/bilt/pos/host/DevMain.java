/*
 *    ____  _ _ _
 *   | __ )(_) | |_
 *   |  _ \| | | __|
 *   | |_) | | | |_
 *   |____/|_|_|\__|
 *
 *   Bilt POS SDK
 */
package com.bilt.pos.host;

import com.bilt.pos.media.service.InMemoryAdDecisionService;
import com.bilt.pos.nexo.client.BiltNexoTerminalClient;
import com.bilt.pos.nexo.client.TerminalClient;
import com.bilt.pos.nexo.security.SecurityKey;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Starts a development host from a small JSON file so the protocol can be driven with curl or a
 * browser before the Terminal Bridge exists.
 *
 * <pre>{@code
 * {
 *   "port": 48333,
 *   "terminals": [
 *     { "poiId": "VictaLane-275839164", "host": "192.168.1.40", "port": 8443,
 *       "encryption": false, "trustAll": true, "label": "Lane 3", "model": "VictaLane" }
 *   ]
 * }
 * }</pre>
 *
 * <p>Each terminal becomes a {@code BiltNexoTerminalClient} on {@code https://host:port/nexo} (or
 * {@code http://} when {@code "tls": false}). {@code "passphrase"} turns on payload encryption with
 * a key derived from it; {@code "encryption": false} leaves it off for lab devices. {@code
 * "trustAll": true} skips certificate validation, {@code "caFile"} trusts one PEM instead. Without
 * any terminals the host still serves {@code local} sessions. Retail-media widgets are backed by an
 * {@code InMemoryAdDecisionService} with no creatives, so they attach but render nothing.
 *
 * <p>Run with {@code ./gradlew :host:run --args="dev-host.json"}; the file argument is optional and
 * defaults to a terminal-less host on port 48333.
 */
public final class DevMain {

  private DevMain() {}

  public static void main(String[] args) throws Exception {
    JsonNode config =
        args.length > 0
            ? new ObjectMapper().readTree(Files.readString(Path.of(args[0])))
            : new ObjectMapper().createObjectNode();
    int port = config.path("port").asInt(48333);
    String bindAddress = config.path("bindAddress").asText("127.0.0.1");

    SessionHost host =
        SessionHost.builder()
            .port(port)
            .bindAddress(bindAddress)
            .terminalClients(new FileTerminals(config.path("terminals")))
            .adDecisionService(new InMemoryAdDecisionService())
            .build();
    host.start();
    System.out.println("Session Host (dev) listening on http://" + bindAddress + ":" + host.port());
    System.out.println("  GET /health, GET /v1/terminals, POST /v1/sessions ... Ctrl-C to stop");
    Runtime.getRuntime().addShutdownHook(new Thread(host::stop, "bilt-host-shutdown"));
    Thread.currentThread().join();
  }

  /** Terminals from the config file, each with a lazily built Nexo client. */
  static final class FileTerminals implements TerminalClientProvider {
    private final Map<String, JsonNode> specs = new LinkedHashMap<>();
    private final Map<String, TerminalClient> clients = new LinkedHashMap<>();

    FileTerminals(JsonNode terminals) {
      for (JsonNode terminal : terminals) {
        String poiId = terminal.path("poiId").asText(null);
        if (poiId == null || poiId.isEmpty()) {
          throw new IllegalArgumentException("every terminal needs a poiId");
        }
        specs.put(poiId, terminal);
      }
    }

    @Override
    public synchronized TerminalClient forPoi(String poiId) {
      JsonNode spec = specs.get(poiId);
      if (spec == null) {
        return null;
      }
      return clients.computeIfAbsent(poiId, id -> build(spec));
    }

    @Override
    public List<TerminalInfo> terminals() {
      List<TerminalInfo> infos = new ArrayList<>();
      specs.forEach(
          (poiId, spec) -> infos.add(TerminalInfo.of(poiId, spec.path("model").asText(null))));
      return infos;
    }

    private static TerminalClient build(JsonNode spec) {
      String scheme = spec.path("tls").asBoolean(true) ? "https" : "http";
      String endpoint =
          scheme
              + "://"
              + spec.path("host").asText()
              + ":"
              + spec.path("port").asInt(8443)
              + "/nexo";
      BiltNexoTerminalClient.Builder builder = BiltNexoTerminalClient.builder().endpoint(endpoint);
      if (spec.path("encryption").asBoolean(true)) {
        String passphrase = spec.path("passphrase").asText(null);
        if (passphrase == null) {
          throw new IllegalArgumentException(
              "terminal "
                  + spec.path("poiId").asText()
                  + " needs a passphrase or encryption:false");
        }
        if (!spec.hasNonNull("keyIdentifier")) {
          throw new IllegalArgumentException(
              "terminal "
                  + spec.path("poiId").asText()
                  + " needs the keyIdentifier its passphrase"
                  + " was provisioned under");
        }
        SecurityKey.Builder key =
            SecurityKey.builder()
                .passphrase(passphrase)
                .keyIdentifier(spec.get("keyIdentifier").asText());
        if (spec.hasNonNull("keyVersion")) {
          key.keyVersion(spec.get("keyVersion").asInt());
        }
        builder.securityKey(key.build());
      }
      if (spec.path("trustAll").asBoolean(false)) {
        builder.trustAllCertificates();
      } else if (spec.hasNonNull("caFile")) {
        try {
          builder.trustCertificate(Path.of(spec.get("caFile").asText()));
        } catch (RuntimeException e) {
          throw new IllegalArgumentException("could not read caFile: " + e.getMessage(), e);
        }
      }
      return builder.build();
    }
  }

  static String read(Path path) throws IOException {
    return Files.readString(path);
  }
}
