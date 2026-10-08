package com.bilt.pos.bridge;

import com.bilt.pos.bridge.config.BridgeConfig;
import com.bilt.pos.bridge.config.BridgeConfigException;
import com.bilt.pos.bridge.config.BridgeConfigSource;
import com.bilt.pos.bridge.config.TerminalClientFactory;
import com.bilt.pos.bridge.config.TerminalConfig;
import com.bilt.pos.bridge.server.OriginAuth;
import com.bilt.pos.bridge.server.PortSelector;
import com.bilt.pos.bridge.server.SessionHostListener;
import com.bilt.pos.nexo.client.TerminalClient;
import java.io.Closeable;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The running bridge: configuration, the client for its one terminal, and the Session Host serving
 * it on loopback. Reloading swaps the configuration and the terminal client in place; the host
 * keeps its port, since clients that already found the bridge would otherwise lose it.
 *
 * <p>The host sees the terminal through {@link BridgeTerminalProvider}, which reads {@link
 * #terminalClient()} on every call, so a reload reaches the next session without a restart.
 */
public final class Bridge implements Closeable {

  private static final Logger LOG = Logger.getLogger(Bridge.class.getName());

  /** Observes configuration reloads, successful or not, so the tray can refresh its labels. */
  public interface Listener {
    void onReload(Optional<BridgeConfig> config, Optional<String> error);
  }

  private final BridgeConfigSource source;
  private final List<Listener> listeners = new CopyOnWriteArrayList<>();

  private volatile BridgeConfig config;
  private volatile Optional<TerminalClient> terminal = Optional.empty();
  private volatile Optional<String> lastConfigError = Optional.empty();
  private SessionHostListener host;
  private Closeable watch = () -> {};

  public Bridge(BridgeConfigSource source) {
    this.source = source;
  }

  /** Loads the configuration, starts the Session Host and begins watching for config changes. */
  public synchronized void start() throws IOException {
    try {
      apply(source.load());
    } catch (BridgeConfigException e) {
      // A broken file on first start must not leave the user with nothing: run on the
      // defaults so /health answers and the tray can open the file to fix it.
      lastConfigError = Optional.of(e.getMessage());
      LOG.log(Level.SEVERE, "Config invalid, running with defaults until fixed: " + e.getMessage());
      config = BridgeConfig.defaults();
      terminal = Optional.empty();
    }
    if (config.allowsAnyOrigin()) {
      LOG.warning(
          "allowedOrigins is [\"*\"]: any web page on this machine may drive the terminal."
              + " This is for development only; list your POS origins before going live.");
    }
    host =
        new SessionHostListener(
            config.bindAddress(),
            config.port(),
            PortSelector.DEFAULT_FALLBACK_PORTS,
            new BridgeTerminalProvider(this),
            new OriginAuth(() -> config.allowedOrigins()));
    host.start();
    watch = source.watch(this::reload);
  }

  /** Re-reads the configuration. On failure the previous configuration stays in force. */
  public synchronized void reload() {
    BridgeConfig previous = config;
    try {
      BridgeConfig next = source.reload();
      apply(next);
      if (previous != null
          && (next.port() != previous.port()
              || !next.bindAddress().equals(previous.bindAddress()))) {
        LOG.warning(
            "Listener address changed in config; it takes effect after a restart"
                + " (still listening on "
                + status().listeningLine()
                + ")");
      }
      LOG.info(
          "Config reloaded: "
              + next.terminal().map(TerminalConfig::toString).orElse("no terminal")
              + ", origins "
              + next.allowedOrigins());
      notifyListeners(Optional.of(next), Optional.empty());
    } catch (BridgeConfigException e) {
      lastConfigError = Optional.of(e.getMessage());
      LOG.log(Level.SEVERE, "Config reload failed, keeping previous config: " + e.getMessage());
      notifyListeners(Optional.ofNullable(previous), lastConfigError);
    }
  }

  private void apply(BridgeConfig next) throws BridgeConfigException {
    Optional<TerminalClient> built = Optional.empty();
    if (next.terminal().isPresent()) {
      TerminalConfig spec = next.terminal().get();
      try {
        built = Optional.of(TerminalClientFactory.build(spec));
      } catch (RuntimeException e) {
        throw new BridgeConfigException("terminal cannot be set up: " + e.getMessage(), e);
      }
      LOG.info("Terminal " + spec);
    }
    config = next;
    terminal = built;
    lastConfigError = Optional.empty();
  }

  private void notifyListeners(Optional<BridgeConfig> config, Optional<String> error) {
    for (Listener listener : listeners) {
      try {
        listener.onReload(config, error);
      } catch (RuntimeException e) {
        LOG.log(Level.WARNING, "Reload listener failed", e);
      }
    }
  }

  /** Registers a reload observer. */
  public void addListener(Listener listener) {
    listeners.add(listener);
  }

  /** The configuration in force. */
  public BridgeConfig config() {
    return config;
  }

  /** Why the most recent load or reload failed, if it did. */
  public Optional<String> lastConfigError() {
    return lastConfigError;
  }

  /** The configuration's source, for the tray's "open config" action. */
  public BridgeConfigSource source() {
    return source;
  }

  /** The SDK client for the configured terminal, if there is one. */
  public Optional<TerminalClient> terminalClient() {
    return terminal;
  }

  /** A snapshot for the tray and diagnostics. */
  public BridgeStatus status() {
    BridgeConfig c = config;
    SessionHostListener h = host;
    return new BridgeStatus(
        BridgeVersion.get(),
        SdkVersion.get(),
        h != null
            ? h.bindAddress().getHostAddress()
            : c == null ? "127.0.0.1" : c.bindAddress().getHostAddress(),
        h == null ? -1 : h.port(),
        terminal.isPresent(),
        h == null ? 0 : h.sessionCount(),
        true);
  }

  @Override
  public synchronized void close() {
    try {
      watch.close();
    } catch (IOException ignored) {
      // the watcher is a daemon poller; nothing to recover
    }
    if (host != null) {
      host.close();
      host = null;
    }
  }
}
