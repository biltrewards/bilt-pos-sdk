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

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Remembers the response to each {@code Idempotency-Key} so a browser that retries after a dropped
 * connection gets the original outcome rather than a second settlement. Bounded and insertion
 * ordered: the oldest keys fall out first. A key reused for a different request — different
 * method, path or body — is refused rather than replayed, since replaying would silently answer
 * the wrong question.
 */
public final class IdempotencyCache {

  /** A stored response: what was sent and a fingerprint of what was asked. */
  public static final class Entry {
    final String fingerprint;
    final int status;
    final String body;

    Entry(String fingerprint, int status, String body) {
      this.fingerprint = fingerprint;
      this.status = status;
      this.body = body;
    }

    public int status() {
      return status;
    }

    public String body() {
      return body;
    }
  }

  private final int capacity;
  private final LinkedHashMap<String, Entry> entries =
      new LinkedHashMap<>(64, 0.75f, false) {
        private static final long serialVersionUID = 1L;

        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Entry> eldest) {
          return size() > capacity;
        }
      };

  public IdempotencyCache(int capacity) {
    this.capacity = capacity;
  }

  /**
   * The stored response for the key, or {@code null} if the key is new. Throws a 422 when the key
   * was used for a different request.
   */
  public synchronized Entry lookup(String key, String fingerprint) {
    Entry entry = entries.get(key);
    if (entry == null) {
      return null;
    }
    if (!entry.fingerprint.equals(fingerprint)) {
      throw HostError.unprocessable(
          "Idempotency-Key '" + key + "' was already used for a different request");
    }
    return entry;
  }

  public synchronized void store(String key, String fingerprint, int status, String body) {
    entries.put(key, new Entry(fingerprint, status, body));
  }

  /** A stable digest of method, path and body. */
  public static String fingerprint(String method, String path, String body) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      digest.update(method.getBytes(StandardCharsets.UTF_8));
      digest.update((byte) 0);
      digest.update(path.getBytes(StandardCharsets.UTF_8));
      digest.update((byte) 0);
      digest.update((body == null ? "" : body).getBytes(StandardCharsets.UTF_8));
      return Base64.getEncoder().encodeToString(digest.digest());
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 is mandatory in every JRE", e);
    }
  }
}
