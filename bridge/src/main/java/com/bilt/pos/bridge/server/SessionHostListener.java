package com.bilt.pos.bridge.server;

import com.bilt.pos.host.HostAuth;
import com.bilt.pos.host.SessionHost;
import com.bilt.pos.host.TerminalClientProvider;
import java.io.Closeable;
import java.io.IOException;
import java.net.BindException;
import java.net.InetAddress;
import java.util.logging.Logger;

/**
 * Runs the Session Host on a loopback port, with the bridge's port fallback around its start.
 *
 * <p>A {@link SessionHost} owns one Javalin instance that cannot be restarted after a failed bind,
 * so each attempt builds a fresh host from the same builder settings.
 */
public final class SessionHostListener implements Closeable {

  private static final Logger LOG = Logger.getLogger(SessionHostListener.class.getName());

  private final InetAddress bindAddress;
  private final int preferredPort;
  private final int fallbackPorts;
  private final TerminalClientProvider terminal;
  private final HostAuth auth;

  private SessionHost host;

  public SessionHostListener(
      InetAddress bindAddress,
      int preferredPort,
      int fallbackPorts,
      TerminalClientProvider terminal,
      HostAuth auth) {
    this.bindAddress = bindAddress;
    this.preferredPort = preferredPort;
    this.fallbackPorts = fallbackPorts;
    this.terminal = terminal;
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
            .terminal(terminal)
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

  /** The address the host listens on; fixed at construction, whatever the config says later. */
  public InetAddress bindAddress() {
    return bindAddress;
  }

  /** The bound port, or {@code -1} before {@link #start()}. */
  public synchronized int port() {
    return host == null ? -1 : host.port();
  }

  /** Sessions the host currently has open. */
  public synchronized int sessionCount() {
    return host == null ? 0 : host.activeSessions();
  }

  @Override
  public synchronized void close() {
    if (host != null) {
      host.close();
      host = null;
    }
  }
}
