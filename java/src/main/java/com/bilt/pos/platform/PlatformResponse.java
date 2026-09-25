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
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * The platform's answer to a {@link PlatformRequest}: status, headers and the body as read into
 * memory.
 *
 * <p>Any status is delivered as a response; the client only throws for failures that prevented a
 * response from being obtained (transport errors, or an access token that could not be acquired).
 * Callers check {@link #isSuccessful()} or {@link #status()} and decode the body themselves, since
 * the shape of each endpoint's payload belongs to the feature that calls it.
 */
public final class PlatformResponse {

  private final int status;
  private final Map<String, List<String>> headers;
  private final byte[] body;

  private PlatformResponse(Builder builder) {
    this.status = builder.status;
    Map<String, List<String>> copy = new LinkedHashMap<>();
    builder.headers.forEach((name, values) -> copy.put(name, Collections.unmodifiableList(values)));
    this.headers = Collections.unmodifiableMap(copy);
    this.body = builder.body;
  }

  public static Builder builder() {
    return new Builder();
  }

  /** The HTTP status code. */
  public int status() {
    return status;
  }

  /** {@code true} for a 2xx status. */
  public boolean isSuccessful() {
    return status >= 200 && status < 300;
  }

  /** All headers, keyed by the name as received, each with its values in order. */
  public Map<String, List<String>> headers() {
    return headers;
  }

  /** The first value of the named header, compared case-insensitively, or {@code null}. */
  public String header(String name) {
    Objects.requireNonNull(name, "name");
    for (Map.Entry<String, List<String>> entry : headers.entrySet()) {
      if (entry.getKey().equalsIgnoreCase(name) && !entry.getValue().isEmpty()) {
        return entry.getValue().get(0);
      }
    }
    return null;
  }

  /** A copy of the body bytes; empty when the response had no body. */
  public byte[] body() {
    return body.clone();
  }

  /** The body decoded as UTF-8; empty when the response had no body. */
  public String bodyAsString() {
    return new String(body, StandardCharsets.UTF_8);
  }

  @Override
  public String toString() {
    return "PlatformResponse{status=" + status + ", body=" + body.length + " bytes}";
  }

  /** Assembles a {@link PlatformResponse}; also useful for test doubles of the client. */
  public static final class Builder {

    private int status;
    private final Map<String, List<String>> headers = new LinkedHashMap<>();
    private byte[] body = new byte[0];

    private Builder() {}

    public Builder status(int status) {
      if (status < 100 || status > 599) {
        throw new IllegalArgumentException("status must be an HTTP status code: " + status);
      }
      this.status = status;
      return this;
    }

    /** Appends a header value; repeated names accumulate in order. */
    public Builder addHeader(String name, String value) {
      Objects.requireNonNull(name, "name");
      Objects.requireNonNull(value, "value");
      headers.computeIfAbsent(name, k -> new ArrayList<>()).add(value);
      return this;
    }

    /** The body bytes; copied. */
    public Builder body(byte[] body) {
      this.body = Objects.requireNonNull(body, "body").clone();
      return this;
    }

    /** A UTF-8 text body. */
    public Builder body(String body) {
      return body(Objects.requireNonNull(body, "body").getBytes(StandardCharsets.UTF_8));
    }

    public PlatformResponse build() {
      if (status == 0) {
        throw new IllegalStateException("status is required");
      }
      return new PlatformResponse(this);
    }
  }
}
