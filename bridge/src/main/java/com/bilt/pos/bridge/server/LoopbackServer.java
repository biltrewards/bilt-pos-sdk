package com.bilt.pos.bridge.server;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import java.io.Closeable;
import java.io.IOException;
import java.net.BindException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The bridge's HTTP listener. It binds a loopback address only, falling back through the ports
 * after the preferred one when that is taken, and applies the CORS policy to every exchange before
 * handing it to the application handler.
 *
 * <p>Built on the JDK's {@code com.sun.net.httpserver} so the skeleton has no server dependency;
 * the Session Host brings its own server and this class then only owns port selection.
 */
public final class LoopbackServer implements Closeable {

  private static final Logger LOG = Logger.getLogger(LoopbackServer.class.getName());

  /** How many ports after the preferred one are tried before giving up. */
  public static final int DEFAULT_FALLBACK_PORTS = 10;

  private final InetAddress bindAddress;
  private final int preferredPort;
  private final int fallbackPorts;
  private final Cors cors;
  private final HttpHandler app;
  private final ExecutorService executor =
      Executors.newThreadPerTaskExecutor(Thread.ofVirtual().name("bridge-http-", 0).factory());

  private HttpServer server;

  /**
   * Prepares a server; nothing is bound until {@link #start()}.
   *
   * @param preferredPort the port to try first; {@code 0} asks the OS for an ephemeral one
   * @param fallbackPorts how many consecutive ports after {@code preferredPort} may be tried
   * @param allowedOrigins the live CORS allow-list; consulted per request so reloads apply at once
   * @param app the handler that serves everything once CORS has been applied
   */
  public LoopbackServer(
      InetAddress bindAddress,
      int preferredPort,
      int fallbackPorts,
      Supplier<List<String>> allowedOrigins,
      HttpHandler app) {
    if (!bindAddress.isLoopbackAddress()) {
      throw new IllegalArgumentException(
          "refusing to bind non-loopback address " + bindAddress.getHostAddress());
    }
    this.bindAddress = bindAddress;
    this.preferredPort = preferredPort;
    this.fallbackPorts = fallbackPorts;
    this.cors = new Cors(allowedOrigins);
    this.app = app;
  }

  /** Binds and starts serving, returning the port actually chosen. */
  public synchronized int start() throws IOException {
    if (server != null) {
      return port();
    }
    BindException lastFailure = null;
    int last = preferredPort == 0 ? 0 : Math.min(65535, preferredPort + fallbackPorts);
    for (int port = preferredPort; port <= last; port++) {
      try {
        server = HttpServer.create(new InetSocketAddress(bindAddress, port), 0);
        break;
      } catch (BindException e) {
        lastFailure = e;
        LOG.info("Port " + port + " is in use, trying the next one");
      }
    }
    if (server == null) {
      throw new IOException(
          "no free port between "
              + preferredPort
              + " and "
              + last
              + " on "
              + bindAddress.getHostAddress(),
          lastFailure);
    }
    server.createContext("/", this::handle);
    server.setExecutor(executor);
    server.start();
    int chosen = port();
    if (chosen != preferredPort && preferredPort != 0) {
      LOG.warning(
          "Preferred port "
              + preferredPort
              + " was taken; listening on "
              + chosen
              + " instead. Clients probing the default port will not find this bridge.");
    }
    LOG.info("Listening on http://" + bindAddress.getHostAddress() + ":" + chosen);
    return chosen;
  }

  /** The bound port, or {@code -1} before {@link #start()}. */
  public synchronized int port() {
    return server == null ? -1 : server.getAddress().getPort();
  }

  /** The bound loopback address. */
  public InetAddress bindAddress() {
    return bindAddress;
  }

  private void handle(HttpExchange exchange) throws IOException {
    try {
      if (cors.apply(exchange)) {
        return;
      }
      app.handle(exchange);
    } catch (IOException | RuntimeException e) {
      LOG.log(Level.WARNING, "Request failed: " + exchange.getRequestURI(), e);
      Responses.json(exchange, 500, "{\"error\":\"internal\"}");
    } finally {
      exchange.close();
    }
  }

  @Override
  public synchronized void close() {
    if (server != null) {
      server.stop(0);
      server = null;
    }
    executor.shutdownNow();
  }
}
