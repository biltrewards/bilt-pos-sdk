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

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * One call to the Bilt platform, expressed independently of the HTTP library that sends it.
 *
 * <p>A request names an HTTP method, a path relative to the environment's {@link
 * BiltEnvironment#apiBaseUrl() API base}, optional headers, an optional body and an optional
 * per-call timeout. Authentication is not part of the request: the {@link BiltPlatformClient}
 * attaches the access token itself, and a caller-supplied {@code Authorization} header is replaced.
 *
 * <p>The path is resolved beneath the API base, so a base of {@code https://api.example/gateway}
 * and a path of {@code v1/members:resolve} address {@code
 * https://api.example/gateway/v1/members:resolve}. A leading slash on the path is ignored rather
 * than treated as absolute, which keeps a base path prefix intact. Query strings may be included in
 * the path. A path that would leave the API base, such as an absolute URL or one climbing out of
 * the base path with {@code ..}, is refused when the request is sent, before any credentials are
 * attached.
 */
public final class PlatformRequest {

  /** Media type used by {@link Builder#jsonBody(String)}. */
  public static final String JSON_CONTENT_TYPE = "application/json; charset=utf-8";

  private final String method;
  private final String path;
  private final Map<String, String> headers;
  private final byte[] body;
  private final String contentType;
  private final Duration timeout;

  private PlatformRequest(Builder builder) {
    this.method = builder.method;
    this.path = builder.path;
    this.headers = Collections.unmodifiableMap(new LinkedHashMap<>(builder.headers));
    this.body = builder.body;
    this.contentType = builder.contentType;
    this.timeout = builder.timeout;
  }

  public static Builder builder() {
    return new Builder();
  }

  /** A {@code GET} of the given path. */
  public static Builder get(String path) {
    return builder().method("GET").path(path);
  }

  /** A {@code POST} to the given path; add the payload with {@link Builder#jsonBody(String)}. */
  public static Builder post(String path) {
    return builder().method("POST").path(path);
  }

  /** The HTTP method in upper case, e.g. {@code "POST"}. */
  public String method() {
    return method;
  }

  /** The path relative to the API base, without a leading slash. */
  public String path() {
    return path;
  }

  /** Request headers in insertion order. Never contains {@code Authorization}. */
  public Map<String, String> headers() {
    return headers;
  }

  /** Whether the request carries a body. */
  public boolean hasBody() {
    return body != null;
  }

  /** A copy of the body bytes, or an empty array when there is none. */
  public byte[] body() {
    return body == null ? new byte[0] : body.clone();
  }

  /** The body's media type, or {@code null} when there is no body. */
  public String contentType() {
    return contentType;
  }

  /** Timeout for the whole call, token waits included, or {@code null} for the client default. */
  public Duration timeout() {
    return timeout;
  }

  @Override
  public String toString() {
    return "PlatformRequest{"
        + method
        + " "
        + path
        + (body == null ? "" : ", body=" + body.length + " bytes")
        + (timeout == null ? "" : ", timeout=" + timeout)
        + '}';
  }

  /** Assembles a {@link PlatformRequest}. */
  public static final class Builder {

    private String method;
    private String path;
    private final Map<String, String> headers = new LinkedHashMap<>();
    private byte[] body;
    private String contentType;
    private Duration timeout;

    private Builder() {}

    /** The HTTP method; case-insensitive, stored in upper case. */
    public Builder method(String method) {
      Objects.requireNonNull(method, "method");
      String upper = method.trim().toUpperCase(Locale.ROOT);
      if (upper.isEmpty() || !upper.chars().allMatch(c -> c >= 'A' && c <= 'Z')) {
        throw new IllegalArgumentException("method must be an HTTP method token: '" + method + "'");
      }
      this.method = upper;
      return this;
    }

    /** The path relative to the API base. A leading slash is dropped. */
    public Builder path(String path) {
      Objects.requireNonNull(path, "path");
      String trimmed = path.trim();
      while (trimmed.startsWith("/")) {
        trimmed = trimmed.substring(1);
      }
      this.path = trimmed;
      return this;
    }

    /**
     * Adds a header, replacing any earlier value for the same name (compared case-insensitively).
     * {@code Authorization} is rejected because the client owns it.
     */
    public Builder header(String name, String value) {
      Objects.requireNonNull(name, "name");
      Objects.requireNonNull(value, "value");
      if (name.equalsIgnoreCase("Authorization")) {
        throw new IllegalArgumentException("Authorization is set by the platform client");
      }
      headers.keySet().removeIf(existing -> existing.equalsIgnoreCase(name));
      headers.put(name, value);
      return this;
    }

    /** A UTF-8 JSON body with {@link #JSON_CONTENT_TYPE}. */
    public Builder jsonBody(String json) {
      Objects.requireNonNull(json, "json");
      return body(json.getBytes(StandardCharsets.UTF_8), JSON_CONTENT_TYPE);
    }

    /** A raw body with an explicit media type. The bytes are copied. */
    public Builder body(byte[] body, String contentType) {
      Objects.requireNonNull(body, "body");
      Objects.requireNonNull(contentType, "contentType");
      this.body = body.clone();
      this.contentType = contentType;
      return this;
    }

    /**
     * Timeout for the whole call: any wait for an access token, the exchange with the API and the
     * one retry after a {@code 401} all share this budget, so the caller is not blocked past it.
     */
    public Builder timeout(Duration timeout) {
      Objects.requireNonNull(timeout, "timeout");
      if (timeout.isNegative() || timeout.isZero()) {
        throw new IllegalArgumentException("timeout must be positive: " + timeout);
      }
      this.timeout = timeout;
      return this;
    }

    public PlatformRequest build() {
      if (method == null) {
        throw new IllegalStateException("method is required");
      }
      if (path == null) {
        throw new IllegalStateException("path is required");
      }
      if (body != null && (method.equals("GET") || method.equals("HEAD"))) {
        throw new IllegalStateException(method + " requests cannot carry a body");
      }
      return new PlatformRequest(this);
    }
  }
}
