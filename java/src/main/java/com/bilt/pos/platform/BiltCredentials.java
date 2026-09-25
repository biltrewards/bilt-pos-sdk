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

import java.util.Arrays;
import java.util.Objects;

/**
 * Credentials that identify an integrator to the Bilt platform.
 *
 * <p>The only grant issued today is OAuth 2.0 client credentials, created with {@link
 * #clientCredentials(String, String)}. The SDK exchanges the pair for short-lived access tokens on
 * the integrator's behalf; neither the pair nor the tokens are ever exposed to widgets, surfaces or
 * callbacks.
 *
 * <p>The secret is held as a {@code char[]} rather than a {@code String}. A {@code String} would be
 * interned in the heap for the lifetime of the process and printed by any careless {@code
 * toString()}, log statement or debugger watch; a {@code char[]} prints as an opaque array
 * reference and can be zeroed if a caller ever needs to. {@link #toString()} redacts the secret and
 * no exception raised by this package carries it. Building the HTTP {@code Authorization} header
 * necessarily creates a transient {@code String}; that is confined to the moment the token request
 * is sent.
 *
 * <p>Other grant types (for example a pre-issued token, or a delegated grant for a hosted surface)
 * would appear as further factories on this class; the {@link Kind} enum is where the client
 * dispatches on them. It is package-private on purpose so that no public commitment is made to
 * grants that do not exist yet.
 */
public final class BiltCredentials {

  /** The grant a set of credentials represents. Package-private until a second grant ships. */
  enum Kind {
    CLIENT_CREDENTIALS
  }

  private final Kind kind;
  private final String clientId;
  private final char[] clientSecret;

  private BiltCredentials(Kind kind, String clientId, char[] clientSecret) {
    this.kind = kind;
    this.clientId = clientId;
    this.clientSecret = clientSecret;
  }

  /**
   * OAuth 2.0 client credentials: the client id and secret issued to the integrator by Bilt. Both
   * must be non-empty. The secret is copied into private storage; the caller's {@code String} is
   * not retained.
   */
  public static BiltCredentials clientCredentials(String clientId, String clientSecret) {
    Objects.requireNonNull(clientId, "clientId");
    Objects.requireNonNull(clientSecret, "clientSecret");
    if (clientId.isEmpty()) {
      throw new IllegalArgumentException("clientId must not be empty");
    }
    if (clientSecret.isEmpty()) {
      throw new IllegalArgumentException("clientSecret must not be empty");
    }
    return new BiltCredentials(Kind.CLIENT_CREDENTIALS, clientId, clientSecret.toCharArray());
  }

  /** The public identifier of these credentials, safe to log. */
  public String clientId() {
    return clientId;
  }

  Kind kind() {
    return kind;
  }

  /**
   * A copy of the secret for building the token request. Callers zero the copy once the request is
   * built so that the secret lives in as few places as possible.
   */
  char[] copySecret() {
    return clientSecret.clone();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof BiltCredentials)) {
      return false;
    }
    BiltCredentials that = (BiltCredentials) o;
    return kind == that.kind
        && clientId.equals(that.clientId)
        && Arrays.equals(clientSecret, that.clientSecret);
  }

  @Override
  public int hashCode() {
    return Objects.hash(kind, clientId, Arrays.hashCode(clientSecret));
  }

  /** Names the grant and the client id; the secret is always shown as {@code <redacted>}. */
  @Override
  public String toString() {
    return "BiltCredentials{kind="
        + kind
        + ", clientId='"
        + clientId
        + "', clientSecret=<redacted>}";
  }
}
