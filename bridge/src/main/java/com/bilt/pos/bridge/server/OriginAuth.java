package com.bilt.pos.bridge.server;

import com.bilt.pos.host.HostAuth;
import com.bilt.pos.host.HostRequest;
import java.util.List;
import java.util.function.Supplier;

/**
 * The bridge's development-mode request policy: a request from a browser page is admitted only if
 * its {@code Origin} is on the configured allow-list; requests without an {@code Origin} (curl,
 * native clients, same-origin tools) pass. The list is read per request, so a config reload applies
 * at once.
 *
 * <p>This enforces the allow-list on the server side. The CORS <em>response</em> headers a browser
 * also needs come from the Session Host, which owns the HTTP layer; until it exposes a CORS option
 * the page must be served from an origin the browser does not treat as cross-origin, see {@code
 * docs/terminal-bridge.md}.
 */
public final class OriginAuth implements HostAuth {

  private final Supplier<List<String>> allowedOrigins;

  public OriginAuth(Supplier<List<String>> allowedOrigins) {
    this.allowedOrigins = allowedOrigins;
  }

  @Override
  public boolean permits(HostRequest request) {
    String origin = request.header("Origin");
    return origin == null || allows(origin);
  }

  /** Whether a page at {@code origin} may call the bridge. */
  public boolean allows(String origin) {
    List<String> allowed = allowedOrigins.get();
    return allowed.contains("*") || allowed.stream().anyMatch(o -> o.equalsIgnoreCase(origin));
  }
}
