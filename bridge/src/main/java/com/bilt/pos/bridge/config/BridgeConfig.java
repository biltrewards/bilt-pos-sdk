package com.bilt.pos.bridge.config;

import com.bilt.pos.nexo.client.BiltTerminalEnvironment;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * The bridge's validated configuration: where to listen, which browser origins may call it, and the
 * terminals it can reach.
 *
 * <p>Parsing is strict. Unknown keys are rejected so a typo cannot silently disable a setting; keys
 * starting with an underscore (such as {@code _comment}) are the exception, since JSON has no
 * comment syntax. The bind address must be a loopback address no matter what the file says: the
 * bridge is a localhost service and never a LAN one.
 */
public record BridgeConfig(
    InetAddress bindAddress,
    int port,
    List<String> allowedOrigins,
    List<TerminalConfig> terminals) {

  public static final int DEFAULT_PORT = 48333;
  public static final int DEFAULT_TERMINAL_PORT = 8443;
  public static final List<String> DEFAULT_ALLOWED_ORIGINS = List.of("*");

  private static final Set<String> ROOT_KEYS =
      Set.of("bindAddress", "port", "allowedOrigins", "terminals");
  private static final Set<String> TERMINAL_KEYS =
      Set.of(
          "poiId",
          "host",
          "port",
          "encryption",
          "passphrase",
          "keyId",
          "keyVersion",
          "trustAll",
          "caCertificatePath",
          "environment");

  public BridgeConfig {
    allowedOrigins = List.copyOf(allowedOrigins);
    terminals = List.copyOf(terminals);
  }

  /** The configuration a fresh install runs with until the user edits the file. */
  public static BridgeConfig defaults() {
    return new BridgeConfig(
        InetAddress.getLoopbackAddress(), DEFAULT_PORT, DEFAULT_ALLOWED_ORIGINS, List.of());
  }

  /** Whether any browser origin may call the bridge, which is acceptable in development only. */
  public boolean allowsAnyOrigin() {
    return allowedOrigins.contains("*");
  }

  /** The terminal with the given id, if configured. */
  public Optional<TerminalConfig> terminal(String poiId) {
    return terminals.stream().filter(t -> t.poiId().equals(poiId)).findFirst();
  }

  /** Parses and validates a configuration document. */
  public static BridgeConfig parse(String json) throws BridgeConfigException {
    JsonNode root;
    try {
      root = new ObjectMapper().readTree(json);
    } catch (JsonProcessingException e) {
      throw new BridgeConfigException("config is not valid JSON: " + e.getOriginalMessage(), e);
    }
    if (root == null || !root.isObject()) {
      throw new BridgeConfigException("config must be a JSON object");
    }
    rejectUnknownKeys(root, ROOT_KEYS, "config");

    InetAddress bind = parseBindAddress(root.path("bindAddress"));
    int port = parsePort(root.path("port"), DEFAULT_PORT, "port");
    List<String> origins = parseOrigins(root.path("allowedOrigins"));
    List<TerminalConfig> terminals = parseTerminals(root.path("terminals"));
    return new BridgeConfig(bind, port, origins, terminals);
  }

  /** Reads and parses the configuration file at {@code path}. */
  public static BridgeConfig read(Path path) throws BridgeConfigException {
    try {
      return parse(Files.readString(path));
    } catch (IOException e) {
      throw new BridgeConfigException("cannot read " + path + ": " + e.getMessage(), e);
    }
  }

  /** A copy with every passphrase replaced by a marker, safe to print in diagnostics. */
  public BridgeConfig redacted() {
    return new BridgeConfig(
        bindAddress,
        port,
        allowedOrigins,
        terminals.stream().map(TerminalConfig::redacted).toList());
  }

  /** Renders this configuration as a JSON tree; use on {@link #redacted()} before printing. */
  public ObjectNode toJson() {
    ObjectMapper mapper = new ObjectMapper();
    ObjectNode root = mapper.createObjectNode();
    root.put("bindAddress", bindAddress.getHostAddress());
    root.put("port", port);
    ArrayNode origins = root.putArray("allowedOrigins");
    allowedOrigins.forEach(origins::add);
    ArrayNode list = root.putArray("terminals");
    for (TerminalConfig t : terminals) {
      ObjectNode node = list.addObject();
      node.put("poiId", t.poiId());
      node.put("host", t.host());
      node.put("port", t.port());
      node.put("encryption", t.encryption());
      node.put("passphrase", t.passphrase().orElse(null));
      node.put("keyId", t.keyId().orElse(null));
      node.put("keyVersion", t.keyVersion());
      node.put("trustAll", t.trustAll());
      node.put("caCertificatePath", t.caCertificatePath().orElse(null));
      node.put("environment", t.environment().map(Enum::name).orElse(null));
    }
    return root;
  }

  private static InetAddress parseBindAddress(JsonNode node) throws BridgeConfigException {
    if (node.isMissingNode() || node.isNull()) {
      return InetAddress.getLoopbackAddress();
    }
    if (!node.isTextual()) {
      throw new BridgeConfigException("bindAddress must be a string");
    }
    String text = node.asText().trim();
    InetAddress address;
    try {
      address = InetAddress.getByName(text);
    } catch (UnknownHostException e) {
      throw new BridgeConfigException("bindAddress '" + text + "' is not a valid address", e);
    }
    if (!address.isLoopbackAddress()) {
      throw new BridgeConfigException(
          "bindAddress '"
              + text
              + "' is not a loopback address; the bridge only ever listens on 127.0.0.1 or ::1");
    }
    return address;
  }

  private static int parsePort(JsonNode node, int fallback, String name)
      throws BridgeConfigException {
    if (node.isMissingNode() || node.isNull()) {
      return fallback;
    }
    if (!node.isIntegralNumber()) {
      throw new BridgeConfigException(name + " must be an integer between 1 and 65535");
    }
    int port = node.asInt();
    if (port < 1 || port > 65535) {
      throw new BridgeConfigException(name + " must be between 1 and 65535, got " + port);
    }
    return port;
  }

  private static List<String> parseOrigins(JsonNode node) throws BridgeConfigException {
    if (node.isMissingNode() || node.isNull()) {
      return DEFAULT_ALLOWED_ORIGINS;
    }
    if (!node.isArray()) {
      throw new BridgeConfigException("allowedOrigins must be an array of origins");
    }
    List<String> origins = new ArrayList<>();
    for (JsonNode item : node) {
      if (!item.isTextual() || item.asText().isBlank()) {
        throw new BridgeConfigException("allowedOrigins entries must be non-empty strings");
      }
      String origin = item.asText().trim();
      if (!origin.equals("*") && !origin.matches("(?i)https?://[^/\\s]+")) {
        throw new BridgeConfigException(
            "allowedOrigins entry '"
                + origin
                + "' is not an origin (expected scheme://host[:port] without a path)");
      }
      origins.add(origin);
    }
    if (origins.isEmpty()) {
      throw new BridgeConfigException("allowedOrigins must list at least one origin or \"*\"");
    }
    return origins;
  }

  private static List<TerminalConfig> parseTerminals(JsonNode node) throws BridgeConfigException {
    if (node.isMissingNode() || node.isNull()) {
      return List.of();
    }
    if (!node.isArray()) {
      throw new BridgeConfigException("terminals must be an array");
    }
    List<TerminalConfig> terminals = new ArrayList<>();
    Set<String> ids = new HashSet<>();
    int index = 0;
    for (JsonNode item : node) {
      TerminalConfig terminal = parseTerminal(item, "terminals[" + index + "]");
      if (!ids.add(terminal.poiId())) {
        throw new BridgeConfigException("duplicate terminal poiId '" + terminal.poiId() + "'");
      }
      terminals.add(terminal);
      index++;
    }
    return terminals;
  }

  private static TerminalConfig parseTerminal(JsonNode node, String where)
      throws BridgeConfigException {
    if (!node.isObject()) {
      throw new BridgeConfigException(where + " must be an object");
    }
    rejectUnknownKeys(node, TERMINAL_KEYS, where);
    String poiId = requireText(node, "poiId", where);
    String host = requireText(node, "host", where);
    int port = parsePort(node.path("port"), DEFAULT_TERMINAL_PORT, where + ".port");
    boolean encryption = optionalBoolean(node, "encryption", false, where);
    Optional<String> passphrase = optionalText(node, "passphrase", where);
    Optional<String> keyId = optionalText(node, "keyId", where);
    int keyVersion = optionalInt(node, "keyVersion", 0, where);
    boolean trustAll = optionalBoolean(node, "trustAll", false, where);
    Optional<String> caPath = optionalText(node, "caCertificatePath", where);
    Optional<BiltTerminalEnvironment> environment = parseEnvironment(node, where);

    if (encryption) {
      if (passphrase.isEmpty()) {
        throw new BridgeConfigException(where + ": encryption is on but passphrase is missing");
      }
      if (keyId.isEmpty()) {
        throw new BridgeConfigException(where + ": encryption is on but keyId is missing");
      }
    }
    if (keyVersion < 0) {
      throw new BridgeConfigException(where + ".keyVersion must not be negative");
    }
    if (trustAll) {
      if (caPath.isPresent() || environment.isPresent()) {
        throw new BridgeConfigException(
            where
                + ": trustAll cannot be combined with caCertificatePath or environment; the SDK"
                + " refuses the contradiction, so pick one");
      }
    } else {
      if (caPath.isEmpty()) {
        throw new BridgeConfigException(
            where + ": caCertificatePath is required unless trustAll is true");
      }
      if (environment.isEmpty()) {
        throw new BridgeConfigException(
            where + ": environment (PRODUCTION or STAGING) is required unless trustAll is true");
      }
      if (!Files.isRegularFile(Path.of(caPath.get()))) {
        throw new BridgeConfigException(
            where + ".caCertificatePath '" + caPath.get() + "' does not exist");
      }
    }
    return new TerminalConfig(
        poiId,
        host,
        port,
        encryption,
        passphrase,
        keyId,
        keyVersion,
        trustAll,
        caPath,
        environment);
  }

  private static Optional<BiltTerminalEnvironment> parseEnvironment(JsonNode node, String where)
      throws BridgeConfigException {
    Optional<String> text = optionalText(node, "environment", where);
    if (text.isEmpty()) {
      return Optional.empty();
    }
    try {
      return Optional.of(
          BiltTerminalEnvironment.valueOf(text.get().trim().toUpperCase(Locale.ROOT)));
    } catch (IllegalArgumentException e) {
      throw new BridgeConfigException(
          where + ".environment must be PRODUCTION or STAGING, got '" + text.get() + "'");
    }
  }

  private static void rejectUnknownKeys(JsonNode node, Set<String> known, String where)
      throws BridgeConfigException {
    Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
    while (fields.hasNext()) {
      String key = fields.next().getKey();
      if (!key.startsWith("_") && !known.contains(key)) {
        throw new BridgeConfigException(
            where
                + " has unknown key '"
                + key
                + "' (known keys: "
                + String.join(", ", known)
                + ")");
      }
    }
  }

  private static String requireText(JsonNode node, String key, String where)
      throws BridgeConfigException {
    return optionalText(node, key, where)
        .orElseThrow(() -> new BridgeConfigException(where + "." + key + " is required"));
  }

  private static Optional<String> optionalText(JsonNode node, String key, String where)
      throws BridgeConfigException {
    JsonNode value = node.path(key);
    if (value.isMissingNode() || value.isNull()) {
      return Optional.empty();
    }
    if (!value.isTextual()) {
      throw new BridgeConfigException(where + "." + key + " must be a string");
    }
    String text = value.asText();
    return text.isBlank() ? Optional.empty() : Optional.of(text);
  }

  private static boolean optionalBoolean(JsonNode node, String key, boolean fallback, String where)
      throws BridgeConfigException {
    JsonNode value = node.path(key);
    if (value.isMissingNode() || value.isNull()) {
      return fallback;
    }
    if (!value.isBoolean()) {
      throw new BridgeConfigException(where + "." + key + " must be true or false");
    }
    return value.asBoolean();
  }

  private static int optionalInt(JsonNode node, String key, int fallback, String where)
      throws BridgeConfigException {
    JsonNode value = node.path(key);
    if (value.isMissingNode() || value.isNull()) {
      return fallback;
    }
    if (!value.isIntegralNumber()) {
      throw new BridgeConfigException(where + "." + key + " must be an integer");
    }
    return value.asInt();
  }
}
