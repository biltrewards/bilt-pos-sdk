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
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * A failure that already knows how it crosses the wire: HTTP status and the {@code SessionError}
 * body. Everything a handler throws is funnelled through {@link #from} so the mapping from SDK
 * exceptions to status codes lives in one place, following the protocol's status table: 400 {@code
 * VALIDATION}, 404 {@code NOT_FOUND}, 409 {@code INVALID_STATE} for the SDK's lifecycle guards, 422
 * for a step reply that does not match or a business refusal, 503 for an unreachable terminal.
 *
 * <p>Codes are the SDK's {@link SessionErrorCode} names wherever the SDK produced the failure;
 * {@code VALIDATION}, {@code NOT_FOUND} and {@code UNAUTHORIZED} exist only on the wire.
 */
public final class HostError extends RuntimeException {

  private static final long serialVersionUID = 1L;

  public static final String VALIDATION = "VALIDATION";
  public static final String NOT_FOUND = "NOT_FOUND";
  public static final String UNAUTHORIZED = "UNAUTHORIZED";

  private final int status;
  private final String code;
  private final transient SessionError sessionError;
  private final transient ObjectNode details;

  private HostError(
      int status, String code, String message, SessionError sessionError, ObjectNode details) {
    super(message, sessionError == null ? null : sessionError.getCause());
    this.status = status;
    this.code = code;
    this.sessionError = sessionError;
    this.details = details;
  }

  public HostError(int status, String code, String message) {
    this(status, code, message, null, null);
  }

  public static HostError badRequest(String message) {
    return new HostError(400, VALIDATION, message);
  }

  /**
   * A well-formed request the host cannot honour as asked, for example a reused idempotency key.
   */
  public static HostError unprocessable(String message) {
    return new HostError(422, VALIDATION, message);
  }

  /** A step reply for a step that is not pending: answered, expired or unknown. */
  public static HostError notPending(String message) {
    return new HostError(422, SessionErrorCode.INVALID_STATE.name(), message);
  }

  public static HostError notFound(String what) {
    return new HostError(404, NOT_FOUND, what + " not found");
  }

  public static HostError conflict(String message) {
    return new HostError(409, SessionErrorCode.INVALID_STATE.name(), message);
  }

  /** Something this session kind or host cannot do; a lifecycle-style refusal, so 409. */
  public static HostError unsupported(String message) {
    return new HostError(409, SessionErrorCode.UNSUPPORTED.name(), message);
  }

  /** The event replay position the client asked for is no longer buffered. */
  public static HostError gone(long oldestSeq) {
    ObjectNode details = Json.object();
    details.put("oldestSeq", oldestSeq);
    return new HostError(
        410,
        NOT_FOUND,
        "the requested since position is no longer buffered; resynchronise from the resources",
        null,
        details);
  }

  public static HostError unauthorized() {
    return new HostError(401, UNAUTHORIZED, "the request was not authorized");
  }

  public HostError withDetails(ObjectNode details) {
    return new HostError(status, code, getMessage(), sessionError, details);
  }

  /** The wire form of an SDK error with the status its code implies. */
  public static HostError of(SessionError error) {
    return new HostError(
        statusFor(error.getCode()), error.getCode().name(), error.getMessage(), error, null);
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
      return new HostError(400, VALIDATION, describe(failure));
    }
    return new HostError(500, SessionErrorCode.UNKNOWN.name(), describe(failure));
  }

  private static String describe(Throwable failure) {
    String message = failure.getMessage();
    return message == null || message.isEmpty() ? failure.getClass().getSimpleName() : message;
  }

  /**
   * The SDK's codes on an HTTP scale: state guards are 409, an unreachable terminal is 503,
   * business refusals the terminal or the shopper made are 422, and only the unexplained is 500.
   */
  static int statusFor(SessionErrorCode code) {
    switch (code) {
      case INVALID_STATE:
        return 409;
      case NETWORK:
      case TIMEOUT:
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

  /** The {@code SessionError} body: code, message, the SDK's Nexo detail, protocol details. */
  public ObjectNode toJson() {
    ObjectNode node =
        sessionError != null
            ? Views.error(sessionError)
            : Json.object()
                .put("code", code)
                .put("message", getMessage() == null ? code : getMessage());
    node.put("code", code);
    if (details != null && !details.isEmpty()) {
      node.set("details", details);
    }
    return node;
  }
}
