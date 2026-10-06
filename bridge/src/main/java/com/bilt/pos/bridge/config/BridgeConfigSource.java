package com.bilt.pos.bridge.config;

import java.io.Closeable;
import java.util.Optional;

/**
 * Supplies the bridge's configuration and tells it when that configuration changes.
 *
 * <p>The development build reads a local JSON file. The design's paired, cloud-pulled configuration
 * is a second implementation of this interface, so nothing above it needs to change when that
 * arrives.
 */
public interface BridgeConfigSource {

  /** Reads the current configuration, creating a starter one where that makes sense. */
  BridgeConfig load() throws BridgeConfigException;

  /**
   * Re-reads the configuration while the bridge is running. Unlike {@link #load()} it never creates
   * a starter configuration: a source that has gone missing is an error, so the configuration in
   * force is kept rather than replaced by development defaults.
   */
  default BridgeConfig reload() throws BridgeConfigException {
    return load();
  }

  /** A human-readable location the tray can open, when the source has one. */
  Optional<String> location();

  /**
   * Starts notifying {@code onChange} whenever the configuration may have changed. The callback
   * reloads through {@link #reload()}; it is never given the new configuration directly, so a
   * failed reload leaves the previous configuration in force. Closing the returned handle stops the
   * notifications. Sources that cannot detect changes return a no-op handle.
   */
  default Closeable watch(Runnable onChange) {
    return () -> {};
  }
}
