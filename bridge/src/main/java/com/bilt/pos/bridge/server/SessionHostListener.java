package com.bilt.pos.bridge.server;

import com.bilt.pos.host.HostAuth;
import com.bilt.pos.host.SessionHost;
import com.bilt.pos.host.TerminalClientProvider;
import java.io.Closeable;
import java.io.IOException;
import java.net.BindException;
import java.net.InetAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Runs the Session Host on a loopback port, with the bridge's port fallback around its start.
 *
 * <p>A {@link SessionHost} owns one Javalin instance that cannot be restarted after a failed bind,
 * so each attempt builds a fresh host from the same builder settings. Session counts for the tray
 * come from the host's own {@code /health}, which is the only place it reports them; the probe is
 * local and cheap, and cached briefly so a menu refresh does not hammer it.
 */
public final class SessionHostListener implements Closeable {

  private static final Logger LOG = Logger.getLogger(SessionHostListener.class.getName());
  private static final Duration PROBE_TIMEOUT = Duration.ofMillis(500);
  private static final Duration PROBE_CACHE = Duration.ofSeconds(1);

  private final InetAddress bindAddress;
  private final int preferredPort;
  private final int fallbackPorts;
  private final TerminalClientProvider terminals;
  private final HostAuth auth;
  private final HttpClient probe = HttpClient.newBuilder().connectTimeout(PROBE_TIMEOUT).build();

  private SessionHost host;
  private volatile int cachedSessions;
  private volatile long cachedAt;

  public SessionHostListener(
      InetAddress bindAddress,
      int preferredPort,
      int fallbackPorts,
      TerminalClientProvider terminals,
      HostAuth auth) {
    this.bindAddress = bindAddress;
    this.preferredPort = preferredPort;
    this.fallbackPorts = fallbackPorts;
    this.terminals = terminals;
    this.auth = auth;
  }

  /** Starts the host, returning the port it bound. */
  public synchronized int start() throws IOException {
    if (host != null) {
      return host.port();
    }
    int port = PortSelector.bind(bindAddress, preferredPort, fallbackPorts, this::tryBind);
    LOG.info("Session Host listening on http://" + bindAddress.getHostAddress() + ":" + port);
    return port;
  }

  private int tryBind(int port) throws IOException {
    SessionHost attempt =
        SessionHost.builder()
            .bindAddress(bindAddress.getHostAddress())
            .port(port)
            .terminalClients(terminals)
            .auth(auth)
            .hostKind("bridge")
            .build();
    try {
      attempt.start();
    } catch (RuntimeException e) {
      attempt.close();
      if (isBindFailure(e)) {
        throw new BindException("port " + port + " in use: " + e.getMessage());
      }
      throw new IOException("Session Host failed to start on port " + port, e);
    }
    host = attempt;
    return attempt.port();
  }

  /** Javalin wraps the socket error in its own exception type; look for either shape. */
  private static boolean isBindFailure(Throwable t) {
    for (Throwable cause = t; cause != null; cause = cause.getCause()) {
      if (cause instanceof BindException
          || cause.getClass().getSimpleName().equals("JavalinBindException")) {
        return true;
      }
    }
    return false;
  }

  /** The bound port, or {@code -1} before {@link #start()}. */
  public synchronized int port() {
    return host == null ? -1 : host.port();
  }

  /** Sessions the host currently has open, as reported by its {@code /health}. */
  public int sessionCount() {
    int port = port();
    if (port < 0) {
      return 0;
    }
    long now = System.nanoTime();
    if (now - cachedAt < PROBE_CACHE.toNanos()) {
      return cachedSessions;
    }
    try {
      HttpResponse<String> res =
          probe.send(
              HttpRequest.newBuilder(
                      URI.create("http://" + bindAddress.getHostAddress() + ":" + port + "/health"))
                  .timeout(PROBE_TIMEOUT)
                  .build(),
              HttpResponse.BodyHandlers.ofString());
      cachedSessions = sessionsFrom(res.body());
    } catch (IOException | RuntimeException e) {
      LOG.log(Level.FINE, "health probe failed", e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
    cachedAt = now;
    return cachedSessions;
  }

  static int sessionsFrom(String healthJson) {
    int at = healthJson.indexOf("\"sessions\":");
    if (at < 0) {
      return 0;
    }
    int start = at + "\"sessions\":".length();
    int end = start;
    while (end < healthJson.length() && Character.isDigit(healthJson.charAt(end))) {
      end++;
    }
    return end == start ? 0 : Integer.parseInt(healthJson.substring(start, end));
  }

  @Override
  public synchronized void close() {
    if (host != null) {
      host.close();
      host = null;
    }
  }
}
