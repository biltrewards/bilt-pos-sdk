/*
 *    ____  _ _ _
 *   | __ )(_) | |_
 *   |  _ \| | | __|
 *   | |_) | | | |_
 *   |____/|_|_|\__|
 *
 *   Bilt POS SDK
 */
package com.bilt.pos.platform;

import java.net.URI;
import java.util.Locale;
import java.util.Objects;

/**
 * A Bilt platform deployment: where the SDK obtains access tokens and where it sends API calls.
 *
 * <p>This mirrors {@link com.bilt.pos.nexo.client.BiltTerminalEnvironment} for the cloud side.
 * Production and staging are separate deployments with separate credentials, so a client id issued
 * for one is unknown to the other. {@link #PRODUCTION} and {@link #STAGING} are the two shipped
 * environments; {@link #custom(URI, URI)} points a client at any other pair of endpoints, which is
 * how tests and the emulator target a local mock server.
 *
 * <p>Tokens come from the {@code ENTERPRISE-POS} Keycloak realm and API calls go to the POS API
 * gateway under {@code /biltpos}, the same gateway that serves terminal configuration.
 */
public final class BiltEnvironment {

  /** The production platform. */
  public static final BiltEnvironment PRODUCTION =
      new BiltEnvironment(
          "PRODUCTION",
          URI.create("https://www.bilt.com/realms/ENTERPRISE-POS/protocol/openid-connect/token"),
          URI.create("https://api.bilt.com/biltpos"));

  /** The staging platform. */
  public static final BiltEnvironment STAGING =
      new BiltEnvironment(
          "STAGING",
          URI.create(
              "https://staging.biltrewards.com/realms/ENTERPRISE-POS/protocol/openid-connect/token"),
          URI.create("https://api.staging.bilt.dev/biltpos"));

  private final String name;
  private final URI tokenEndpoint;
  private final URI apiBaseUrl;

  private BiltEnvironment(String name, URI tokenEndpoint, URI apiBaseUrl) {
    this.name = name;
    this.tokenEndpoint = tokenEndpoint;
    this.apiBaseUrl = apiBaseUrl;
  }

  /**
   * An environment with explicit endpoints. Both URIs must be absolute {@code http} or {@code
   * https} URLs. The API base may carry a path prefix; request paths are resolved beneath it.
   */
  public static BiltEnvironment custom(URI tokenEndpoint, URI apiBaseUrl) {
    return new BiltEnvironment(
        "CUSTOM",
        requireHttpUrl(tokenEndpoint, "tokenEndpoint"),
        requireHttpUrl(apiBaseUrl, "apiBaseUrl"));
  }

  private static URI requireHttpUrl(URI uri, String what) {
    Objects.requireNonNull(uri, what);
    if (!uri.isAbsolute() || uri.getHost() == null) {
      throw new IllegalArgumentException(what + " must be an absolute URL: " + uri);
    }
    String scheme = uri.getScheme().toLowerCase(Locale.ROOT);
    if (!scheme.equals("https") && !scheme.equals("http")) {
      throw new IllegalArgumentException(what + " must use http or https: " + uri);
    }
    return uri;
  }

  /** {@code PRODUCTION}, {@code STAGING} or {@code CUSTOM}. */
  public String name() {
    return name;
  }

  /** The OAuth 2.0 token endpoint the client posts the client-credentials grant to. */
  public URI tokenEndpoint() {
    return tokenEndpoint;
  }

  /** The POS API gateway base URL that {@link PlatformRequest#path()} is resolved against. */
  public URI apiBaseUrl() {
    return apiBaseUrl;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof BiltEnvironment)) {
      return false;
    }
    BiltEnvironment that = (BiltEnvironment) o;
    return name.equals(that.name)
        && tokenEndpoint.equals(that.tokenEndpoint)
        && apiBaseUrl.equals(that.apiBaseUrl);
  }

  @Override
  public int hashCode() {
    return Objects.hash(name, tokenEndpoint, apiBaseUrl);
  }

  @Override
  public String toString() {
    return "BiltEnvironment{"
        + name
        + ", tokenEndpoint="
        + tokenEndpoint
        + ", apiBaseUrl="
        + apiBaseUrl
        + '}';
  }
}
