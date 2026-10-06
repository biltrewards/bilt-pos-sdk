package com.bilt.pos.bridge.server;

import java.io.IOException;
import java.net.BindException;
import java.net.InetAddress;
import java.util.logging.Logger;

/**
 * Binds the first free port in a short range: the preferred one, then the next few. Clients probe
 * the preferred port first, so a fallback is logged loudly; it keeps the bridge reachable through
 * {@code /health} on whichever port it got rather than failing to start.
 */
public final class PortSelector {

  private static final Logger LOG = Logger.getLogger(PortSelector.class.getName());

  /** How many ports after the preferred one are tried before giving up. */
  public static final int DEFAULT_FALLBACK_PORTS = 10;

  /** One bind attempt; throws {@link BindException} when the port is taken. */
  @FunctionalInterface
  public interface Binder {
    int bind(int port) throws IOException;
  }

  private PortSelector() {}

  /**
   * Tries {@code preferred} then up to {@code fallbacks} following ports, returning the port the
   * binder reports. A preferred port of {@code 0} asks the OS for an ephemeral one and tries once.
   */
  public static int bind(InetAddress address, int preferred, int fallbacks, Binder binder)
      throws IOException {
    if (!address.isLoopbackAddress()) {
      throw new IllegalArgumentException(
          "refusing to bind non-loopback address " + address.getHostAddress());
    }
    int last = preferred == 0 ? 0 : Math.min(65535, preferred + fallbacks);
    BindException lastFailure = null;
    for (int port = preferred; port <= last; port++) {
      try {
        int bound = binder.bind(port);
        if (preferred != 0 && bound != preferred) {
          LOG.warning(
              "Preferred port "
                  + preferred
                  + " was taken; listening on "
                  + bound
                  + " instead. Clients probing the default port will not find this bridge.");
        }
        return bound;
      } catch (BindException e) {
        lastFailure = e;
        LOG.info("Port " + port + " is in use, trying the next one");
      }
    }
    throw new IOException(
        "no free port between " + preferred + " and " + last + " on " + address.getHostAddress(),
        lastFailure);
  }
}
