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

/**
 * A point-in-time copy of a {@link SessionContext}: the phase, the attributes, and the lane
 * identifiers, frozen together so that a widget or observer reasons about one consistent state
 * while the live context keeps changing. The session takes one with {@link
 * SessionContext#snapshot()}; the builder is for test doubles and for code that assembles a context
 * outside a session.
 */
public final class SessionContextSnapshot {

  private final CheckoutPhase phase;
  private final Map<String, String> attributes;
  private final String saleId;
  private final String currency;
  private final String storeLocation;
  private final String poiId;

  private SessionContextSnapshot(Builder builder) {
    this.phase = builder.phase;
    this.attributes = Collections.unmodifiableMap(new LinkedHashMap<>(builder.attributes));
    this.saleId = builder.saleId;
    this.currency = builder.currency;
    this.storeLocation = builder.storeLocation;
    this.poiId = builder.poiId;
  }

  public static Builder builder() {
    return new Builder();
  }

  /** The checkout phase at the time of the snapshot. */
  public CheckoutPhase phase() {
    return phase;
  }

  /** The attributes at the time of the snapshot, unmodifiable, in insertion order. */
  public Map<String, String> attributes() {
    return attributes;
  }

  /** The register's identifier for the lane. */
  public String saleId() {
    return saleId;
  }

  /** ISO 4217 currency code of the session. */
  public String currency() {
    return currency;
  }

  /** Store location identifier, or {@code null} if not configured. */
  public String storeLocation() {
    return storeLocation;
  }

  /** The terminal identifier on a terminal session; {@code null} on a local one. */
  public String poiId() {
    return poiId;
  }

  @Override
  public boolean equals(Object other) {
    if (this == other) {
      return true;
    }
    if (!(other instanceof SessionContextSnapshot)) {
      return false;
    }
    SessionContextSnapshot that = (SessionContextSnapshot) other;
    return phase == that.phase
        && attributes.equals(that.attributes)
        && Objects.equals(saleId, that.saleId)
        && Objects.equals(currency, that.currency)
        && Objects.equals(storeLocation, that.storeLocation)
        && Objects.equals(poiId, that.poiId);
  }

  @Override
  public int hashCode() {
    return Objects.hash(phase, attributes, saleId, currency, storeLocation, poiId);
  }

  @Override
  public String toString() {
    return "SessionContextSnapshot[phase="
        + phase
        + ", attributes="
        + attributes
        + ", saleId="
        + saleId
        + ", currency="
        + currency
        + ", storeLocation="
        + storeLocation
        + ", poiId="
        + poiId
        + ']';
  }

  /** Builder for a {@link SessionContextSnapshot}; the phase defaults to {@code SCANNING}. */
  public static final class Builder {

    private CheckoutPhase phase = CheckoutPhase.SCANNING;
    private final LinkedHashMap<String, String> attributes = new LinkedHashMap<>();
    private String saleId;
    private String currency;
    private String storeLocation;
    private String poiId;

    private Builder() {}

    public Builder phase(CheckoutPhase phase) {
      this.phase = Objects.requireNonNull(phase, "phase");
      return this;
    }

    /** Adds one attribute; repeatable. A {@code null} value removes the key. */
    public Builder attribute(String key, String value) {
      Objects.requireNonNull(key, "key");
      if (value == null) {
        attributes.remove(key);
      } else {
        attributes.put(key, value);
      }
      return this;
    }

    /** Adds every entry of the map, in its iteration order. */
    public Builder attributes(Map<String, String> attributes) {
      Objects.requireNonNull(attributes, "attributes").forEach(this::attribute);
      return this;
    }

    public Builder saleId(String saleId) {
      this.saleId = saleId;
      return this;
    }

    public Builder currency(String currency) {
      this.currency = currency;
      return this;
    }

    public Builder storeLocation(String storeLocation) {
      this.storeLocation = storeLocation;
      return this;
    }

    public Builder poiId(String poiId) {
      this.poiId = poiId;
      return this;
    }

    public SessionContextSnapshot build() {
      return new SessionContextSnapshot(this);
    }
  }
}
