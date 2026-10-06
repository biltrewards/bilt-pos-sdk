package com.bilt.pos.bridge.config;

import java.io.Closeable;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Reads the configuration from a JSON file, writing a commented starter file on first run.
 *
 * <p>Changes are detected by polling the file's modification time every couple of seconds rather
 * than with a {@code WatchService}: on macOS the JDK's watch service is itself a slow poller, and
 * editors that write via rename-and-replace confuse it, whereas an mtime check is uniform across
 * platforms and cheap at this interval.
 */
public final class FileBridgeConfigSource implements BridgeConfigSource {

  private static final Logger LOG = Logger.getLogger(FileBridgeConfigSource.class.getName());
  private static final Duration POLL_INTERVAL = Duration.ofSeconds(2);

  private final Path file;

  public FileBridgeConfigSource(Path file) {
    this.file = file;
  }

  /** The file this source reads. */
  public Path file() {
    return file;
  }

  @Override
  public BridgeConfig load() throws BridgeConfigException {
    if (!Files.exists(file)) {
      writeExample();
    }
    return BridgeConfig.read(file);
  }

  @Override
  public Optional<String> location() {
    return Optional.of(file.toString());
  }

  @Override
  public Closeable watch(Runnable onChange) {
    ScheduledExecutorService poller =
        Executors.newSingleThreadScheduledExecutor(
            r -> {
              Thread t = new Thread(r, "bridge-config-watch");
              t.setDaemon(true);
              return t;
            });
    FileTime[] last = {modified()};
    poller.scheduleWithFixedDelay(
        () -> {
          FileTime now = modified();
          if (now != null && !now.equals(last[0])) {
            last[0] = now;
            LOG.info("Config file changed, reloading");
            try {
              onChange.run();
            } catch (RuntimeException e) {
              LOG.log(Level.WARNING, "Config change handler failed", e);
            }
          }
        },
        POLL_INTERVAL.toMillis(),
        POLL_INTERVAL.toMillis(),
        TimeUnit.MILLISECONDS);
    return poller::shutdownNow;
  }

  private FileTime modified() {
    try {
      return Files.getLastModifiedTime(file);
    } catch (IOException e) {
      return null;
    }
  }

  private void writeExample() throws BridgeConfigException {
    try {
      Files.createDirectories(file.getParent());
      Files.writeString(file, exampleJson(), StandardCharsets.UTF_8);
      LOG.info("Wrote starter config to " + file);
    } catch (IOException e) {
      throw new BridgeConfigException("cannot create " + file + ": " + e.getMessage(), e);
    }
  }

  /**
   * The starter document. It is valid as written (so the bridge starts) and the comment explains
   * what to fill in. The example terminal is a development one: unencrypted and trusting any
   * certificate.
   */
  static String exampleJson() {
    return """
        {
          "_comment": [
            "Bilt Terminal Bridge configuration (development mode).",
            "port: loopback port the browser POS talks to; the next 10 ports are tried if taken.",
            "allowedOrigins: browser origins allowed by CORS. \\"*\\" is development-only;",
            "  list your POS page origins (e.g. https://pos.example.com) before going live.",
            "terminals: one entry per Bilt terminal on the store LAN, keyed by poiId.",
            "  Development terminals: encryption=false, trustAll=true.",
            "  Production terminals: encryption=true with passphrase+keyId, trustAll=false",
            "  with caCertificatePath and environment (PRODUCTION or STAGING).",
            "The bridge reloads this file when it changes."
          ],
          "port": 48333,
          "allowedOrigins": ["*"],
          "terminals": [
            {
              "poiId": "VictaLane-275839164",
              "host": "192.168.4.108",
              "port": 8443,
              "encryption": false,
              "passphrase": null,
              "keyId": null,
              "trustAll": true,
              "caCertificatePath": null,
              "environment": null
            }
          ]
        }
        """;
  }
}
