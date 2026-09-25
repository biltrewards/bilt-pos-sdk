/*
 *    ____  _ _ _
 *   | __ )(_) | |_
 *   |  _ \| | | __|
 *   | |_) | | | |_
 *   |____/|_|_|\__|
 *
 *   Bilt POS SDK
 */
package com.bilt.pos.media.service;

import java.util.Objects;

/**
 * An opaque reference to a shopper session registered with an {@link AdDecisionService}.
 *
 * <p>The service mints one per {@link AdDecisionService#registerSession} and expects it back on
 * every later call for that session. The id is whatever the service chose — a platform session id,
 * a UUID — and callers treat it as opaque. Value semantics: two handles with the same id are the
 * same session.
 */
public final class SessionHandle {

  private final String id;

  private SessionHandle(String id) {
    this.id = Objects.requireNonNull(id, "id");
    if (id.isEmpty()) {
      throw new IllegalArgumentException("session handle id must not be empty");
    }
  }

  public static SessionHandle of(String id) {
    return new SessionHandle(id);
  }

  public String getId() {
    return id;
  }

  @Override
  public boolean equals(Object o) {
    return this == o || (o instanceof SessionHandle && id.equals(((SessionHandle) o).id));
  }

  @Override
  public int hashCode() {
    return id.hashCode();
  }

  @Override
  public String toString() {
    return "SessionHandle{" + id + "}";
  }
}
