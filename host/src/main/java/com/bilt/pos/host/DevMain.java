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
import java.util.List;

/**
 * Starts a development host from a small JSON file so the protocol can be driven with curl or a
 * browser before the Terminal Bridge exists.
 *
 * <pre>{@code
 * {
 *   "port": 48333,
 *   "terminal": { "host": "192.168.1.40", "port": 8443, "encryption": false, "trustAll": true,
 *                 "label": "Lane 3", "model": "VictaLane" }
 * }
 * }</pre>
 *
 * <p>CORS answers every origin; {@code "allowedOrigins": ["https://pos.example.com"]} narrows it.
 *
 * <p>The host drives that one terminal, a {@code BiltNexoTerminalClient} on {@code
 * https://host:port/nexo} (or {@code http://} when {@code "tls": false}). {@code "passphrase"}
 * turns on payload encryption with a key derived from it; {@code "encryption": false} leaves it off
 * for lab devices. {@code "trustAll": true} skips certificate validation, {@code "caFile"} trusts
 * one PEM instead. Without a terminal the host still serves {@code local} sessions. Retail-media
 * widgets are backed by an {@code InMemoryAdDecisionService} with no creatives, so they attach but
 * render nothing.
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
            .allowedOrigins(allowedOrigins(config))
            .terminal(terminal(config))
            .adDecisionService(new InMemoryAdDecisionService())
            .build();
    host.start();
    System.out.println("Session Host (dev) listening on http://" + bindAddress + ":" + host.port());
    System.out.println("  GET /health, GET /v1/terminal, POST /v1/sessions ... Ctrl-C to stop");
    Runtime.getRuntime().addShutdownHook(new Thread(host::stop, "bilt-host-shutdown"));
    Thread.currentThread().join();
  }

  /** Every origin unless the config file narrows it with {@code "allowedOrigins": [...]}. */
  static List<String> allowedOrigins(JsonNode config) {
    JsonNode configured = config.path("allowedOrigins");
    if (!configured.isArray()) {
      return List.of("*");
    }
    List<String> origins = new ArrayList<>();
    configured.forEach(origin -> origins.add(origin.asText()));
    return origins;
  }

  /** The terminal from the config file, or none when the file names no {@code terminal}. */
  static TerminalClientProvider terminal(JsonNode config) {
    if (config.has("terminals")) {
      throw new IllegalArgumentException(
          "\"terminals\" is no longer supported: a host drives exactly one terminal. Replace the"
              + " list with a single \"terminal\" object and drop its poiId; the poiId a session"
              + " names is passed through to the terminal");
    }
    JsonNode spec = config.path("terminal");
    if (!spec.isObject()) {
      return TerminalClientProvider.none();
    }
    return TerminalClientProvider.of(
        build(spec),
        TerminalInfo.of(spec.path("label").asText(null), spec.path("model").asText(null)));
  }

  private static TerminalClient build(JsonNode spec) {
    String scheme = spec.path("tls").asBoolean(true) ? "https" : "http";
    String endpoint =
        scheme + "://" + spec.path("host").asText() + ":" + spec.path("port").asInt(8443) + "/nexo";
    BiltNexoTerminalClient.Builder builder = BiltNexoTerminalClient.builder().endpoint(endpoint);
    if (spec.path("encryption").asBoolean(true)) {
      String passphrase = spec.path("passphrase").asText(null);
      if (passphrase == null) {
        throw new IllegalArgumentException("the terminal needs a passphrase or encryption:false");
      }
      if (!spec.hasNonNull("keyIdentifier")) {
        throw new IllegalArgumentException(
            "the terminal needs the keyIdentifier its passphrase was provisioned under");
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

  static String read(Path path) throws IOException {
    return Files.readString(path);
  }
}
