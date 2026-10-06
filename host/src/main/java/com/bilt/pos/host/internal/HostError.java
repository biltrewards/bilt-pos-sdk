/*
 *    ____  _ _ _
 *   | __ )(_) | |_
 *   |  _ \| | | __|
 *   | |_) | | | |_
 *   |____/|_|_|\__|
 *
 *   Bilt POS SDK
 */
package com.bilt.pos.host.internal;

import com.bilt.pos.session.SessionError;
import com.bilt.pos.session.SessionErrorCode;
import com.bilt.pos.session.SessionException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * A failure that already knows how it crosses the wire: HTTP status, {@code SessionError} code and
 * message, optional details. Everything a handler throws is funnelled through {@link #from} so the
 * mapping from SDK exceptions to status codes lives in one place.
 *
 * <p>Codes are the SDK's {@link SessionErrorCode} names wherever the SDK produced the failure. The
 * protocol itself needs three more that the SDK has no reason to know: {@code INVALID_REQUEST} for
 * a malformed or semantically invalid request, {@code NOT_FOUND} for a missing resource and {@code
 * UNAUTHORIZED} for a refused credential.
 */
public final class HostError extends RuntimeException {

  private static final long serialVersionUID = 1L;

  public static final String INVALID_REQUEST = "INVALID_REQUEST";
  public static final String NOT_FOUND = "NOT_FOUND";
  public static final String UNAUTHORIZED = "UNAUTHORIZED";

  private final int status;
  private final String code;
  private final transient JsonNode details;

  public HostError(int status, String code, String message) {
    this(status, code, message, null, null);
  }

  public HostError(int status, String code, String message, JsonNode details, Throwable cause) {
    super(message, cause);
    this.status = status;
    this.code = code;
    this.details = details;
  }

  public static HostError badRequest(String message) {
    return new HostError(400, INVALID_REQUEST, message);
  }

  public static HostError unprocessable(String message) {
    return new HostError(422, INVALID_REQUEST, message);
  }

  public static HostError notFound(String what) {
    return new HostError(404, NOT_FOUND, what + " not found");
  }

  public static HostError conflict(String message) {
    return new HostError(409, SessionErrorCode.INVALID_STATE.name(), message);
  }

  public static HostError unsupported(String message) {
    return new HostError(422, SessionErrorCode.UNSUPPORTED.name(), message);
  }

  public static HostError notImplemented(String message) {
    return new HostError(501, SessionErrorCode.UNSUPPORTED.name(), message);
  }

  public static HostError unauthorized() {
    return new HostError(401, UNAUTHORIZED, "the request was not authorized");
  }

  /** The wire form of an SDK error with the status its code implies. */
  public static HostError of(SessionError error) {
    return new HostError(
        statusFor(error.getCode()),
        error.getCode().name(),
        error.getMessage(),
        Views.errorDetails(error),
        error.getCause());
  }

  /** Classifies anything a handler threw. */
  public static HostError from(Throwable failure) {
    if (failure instanceof HostError) {
      return (HostError) failure;
    }
    if (failure instanceof SessionException) {
      return of(((SessionException) failure).getError());
    }
    if (failure instanceof IllegalStateException) {
      return conflict(failure.getMessage());
    }
    if (failure instanceof IllegalArgumentException || failure instanceof NullPointerException) {
      return new HostError(422, INVALID_REQUEST, describe(failure), null, failure);
    }
    return new HostError(
        500, SessionErrorCode.UNKNOWN.name(), describe(failure), null, failure);
  }

  private static String describe(Throwable failure) {
    String message = failure.getMessage();
    return message == null || message.isEmpty() ? failure.getClass().getSimpleName() : message;
  }

  /**
   * The SDK's codes on an HTTP scale: state guards are 409, the terminal being unreachable is 503,
   * business refusals the terminal or the shopper made are 422, and only the truly unexplained is
   * a 500.
   */
  static int statusFor(SessionErrorCode code) {
    switch (code) {
      case INVALID_STATE:
        return 409;
      case NETWORK:
      case TIMEOUT:
      case TERMINAL_ERROR:
        return 503;
      case UNKNOWN:
        return 500;
      default:
        return 422;
    }
  }

  public int status() {
    return status;
  }

  public String code() {
    return code;
  }

  public JsonNode details() {
    return details;
  }

  public ObjectNode toJson() {
    ObjectNode node = Json.object();
    node.put("code", code);
    node.put("message", getMessage() == null ? code : getMessage());
    if (details != null && !details.isNull() && !(details.isObject() && details.isEmpty())) {
      node.set("details", details);
    }
    return node;
  }
}
