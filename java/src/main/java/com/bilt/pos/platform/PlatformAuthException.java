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

/**
 * An access token could not be obtained from the environment's token endpoint.
 *
 * <p>{@link #status()} is the HTTP status the token endpoint answered with, or {@code 0} when no
 * response was received (transport failure, malformed token document). {@link #errorCode()} is the
 * OAuth 2.0 {@code error} field from the response when the endpoint supplied one, e.g. {@code
 * invalid_client} for a rejected client id or secret. Neither the request's credentials nor the
 * response body appear in the message.
 */
public final class PlatformAuthException extends PlatformException {

  private static final long serialVersionUID = 1L;

  private final int status;
  private final String errorCode;

  public PlatformAuthException(String message) {
    this(message, 0, null, null);
  }

  public PlatformAuthException(String message, Throwable cause) {
    this(message, 0, null, cause);
  }

  public PlatformAuthException(String message, int status, String errorCode) {
    this(message, status, errorCode, null);
  }

  public PlatformAuthException(String message, int status, String errorCode, Throwable cause) {
    super(message, cause);
    this.status = status;
    this.errorCode = errorCode;
  }

  /** The token endpoint's HTTP status, or {@code 0} when none was received. */
  public int status() {
    return status;
  }

  /** The OAuth 2.0 {@code error} code from the token endpoint, or {@code null}. */
  public String errorCode() {
    return errorCode;
  }
}
