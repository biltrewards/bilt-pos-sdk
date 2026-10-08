package com.bilt.pos.bridge;

import com.bilt.pos.bridge.config.BridgeConfig;
import com.bilt.pos.bridge.config.TerminalConfig;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Builds the redacted summary that "Copy diagnostics" puts on the clipboard: versions, platform,
 * listener, the configuration without secrets, and the most recent log lines.
 *
 * <p>Redaction happens twice. The configuration is rendered from its {@link
 * BridgeConfig#redacted()} copy, and every passphrase value is then scrubbed from the whole text in
 * case one was ever echoed into a log line by a stack trace or a careless message.
 */
public final class Diagnostics {

  private static final String MASK = "***";

  private Diagnostics() {}

  /** Renders the summary. {@code config} may be absent when the last load failed. */
  public static String render(
      BridgeStatus status,
      Optional<BridgeConfig> config,
      Optional<String> configLocation,
      Optional<String> lastConfigError,
      AppDirs dirs,
      List<String> recentLogLines) {
    StringBuilder sb = new StringBuilder();
    sb.append("Bilt Terminal Bridge diagnostics\n");
    sb.append("generated: ").append(Instant.now()).append('\n');
    sb.append("bridge version: ").append(status.bridgeVersion()).append('\n');
    sb.append("sdk version: ").append(status.sdkVersion()).append('\n');
    sb.append("session host embedded: ").append(status.sessionHostEmbedded()).append('\n');
    sb.append("java: ")
        .append(System.getProperty("java.vendor"))
        .append(' ')
        .append(System.getProperty("java.runtime.version"))
        .append('\n');
    sb.append("os: ")
        .append(System.getProperty("os.name"))
        .append(' ')
        .append(System.getProperty("os.version"))
        .append(' ')
        .append(System.getProperty("os.arch"))
        .append('\n');
    sb.append("listener: ").append(status.listeningLine()).append('\n');
    sb.append("terminal configured: ").append(status.terminalConfigured()).append('\n');
    sb.append("sessions active: ").append(status.sessionCount()).append('\n');
    sb.append("config file: ").append(configLocation.orElse("-")).append('\n');
    sb.append("log dir: ").append(dirs.logDir()).append('\n');
    lastConfigError.ifPresent(err -> sb.append("last config error: ").append(err).append('\n'));
    sb.append('\n').append("config (redacted):\n");
    sb.append(config.map(Diagnostics::configJson).orElse("(not loaded)")).append('\n');
    sb.append('\n').append("recent log (").append(recentLogLines.size()).append(" lines):\n");
    recentLogLines.forEach(line -> sb.append(line).append('\n'));
    return scrub(sb.toString(), config);
  }

  private static String configJson(BridgeConfig config) {
    try {
      return new ObjectMapper()
          .writerWithDefaultPrettyPrinter()
          .writeValueAsString(config.redacted().toJson());
    } catch (JsonProcessingException e) {
      return "(could not render config: " + e.getMessage() + ")";
    }
  }

  /** Replaces the configured passphrase, wherever it appears in {@code text}. */
  static String scrub(String text, Optional<BridgeConfig> config) {
    Optional<String> passphrase =
        config
            .flatMap(BridgeConfig::terminal)
            .flatMap(TerminalConfig::passphrase)
            .filter(p -> !p.isEmpty());
    return passphrase.map(p -> text.replace(p, MASK)).orElse(text);
  }
}
