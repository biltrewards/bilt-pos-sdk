/*
 *    ____  _ _ _
 *   | __ )(_) | |_
 *   |  _ \| | | __|
 *   | |_) | | | |_
 *   |____/|_|_|\__|
 *
 *   Bilt POS SDK
 */
package com.bilt.pos.session;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * The {@link SessionContext} behind {@link AbstractShopperSession#context()}: the phase and an
 * insertion-ordered attribute map, every read and write under the session lock so a change is
 * atomic against the session's own transitions. A write that changes something publishes a fresh
 * {@link SessionContextSnapshot} to the session's {@code contextChanged} hook, still under the
 * lock; a write that leaves the context as it was publishes nothing.
 */
final class DefaultSessionContext implements SessionContext {

  private final ReentrantLock lock;
  private final String saleId;
  private final String currency;
  private final String storeLocation;
  private final String poiId;
  private final BooleanSupplier ended;
  private final Consumer<SessionContextSnapshot> onChanged;
  private CheckoutPhase phase;
  private final LinkedHashMap<String, String> attributes = new LinkedHashMap<>();

  DefaultSessionContext(
      ReentrantLock lock,
      String saleId,
      String currency,
      String storeLocation,
      String poiId,
      CheckoutPhase initialPhase,
      Map<String, String> initialAttributes,
      BooleanSupplier ended,
      Consumer<SessionContextSnapshot> onChanged) {
    this.lock = lock;
    this.saleId = saleId;
    this.currency = currency;
    this.storeLocation = storeLocation;
    this.poiId = poiId;
    this.phase = Objects.requireNonNull(initialPhase, "initialPhase");
    this.attributes.putAll(initialAttributes);
    this.ended = ended;
    this.onChanged = onChanged;
  }

  @Override
  public CheckoutPhase phase() {
    lock.lock();
    try {
      return phase;
    } finally {
      lock.unlock();
    }
  }

  @Override
  public SessionContext phase(CheckoutPhase phase) {
    Objects.requireNonNull(phase, "phase");
    lock.lock();
    try {
      requireMutable();
      if (this.phase != phase) {
        this.phase = phase;
        publish();
      }
    } finally {
      lock.unlock();
    }
    return this;
  }

  @Override
  public SessionContext attribute(String key, String value) {
    Objects.requireNonNull(key, "key");
    if (value == null) {
      return removeAttribute(key);
    }
    lock.lock();
    try {
      requireMutable();
      if (!value.equals(attributes.put(key, value))) {
        publish();
      }
    } finally {
      lock.unlock();
    }
    return this;
  }

  @Override
  public SessionContext removeAttribute(String key) {
    Objects.requireNonNull(key, "key");
    lock.lock();
    try {
      requireMutable();
      if (attributes.remove(key) != null) {
        publish();
      }
    } finally {
      lock.unlock();
    }
    return this;
  }

  @Override
  public Map<String, String> attributes() {
    lock.lock();
    try {
      return Collections.unmodifiableMap(new LinkedHashMap<>(attributes));
    } finally {
      lock.unlock();
    }
  }

  @Override
  public String saleId() {
    return saleId;
  }

  @Override
  public String currency() {
    return currency;
  }

  @Override
  public String storeLocation() {
    return storeLocation;
  }

  @Override
  public String poiId() {
    return poiId;
  }

  @Override
  public SessionContextSnapshot snapshot() {
    lock.lock();
    try {
      return SessionContextSnapshot.builder()
          .phase(phase)
          .attributes(attributes)
          .saleId(saleId)
          .currency(currency)
          .storeLocation(storeLocation)
          .poiId(poiId)
          .build();
    } finally {
      lock.unlock();
    }
  }

  private void requireMutable() {
    if (ended.getAsBoolean()) {
      throw new IllegalStateException("the session context cannot be modified after end()");
    }
  }

  private void publish() {
    onChanged.accept(snapshot());
  }
}
