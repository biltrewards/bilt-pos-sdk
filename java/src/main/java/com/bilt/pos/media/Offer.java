/*
 *    ____  _ _ _
 *   | __ )(_) | |_
 *   |  _ \| | | __|
 *   | |_) | | | |_
 *   |____/|_|_|\__|
 *
 *   Bilt POS SDK
 */
package com.bilt.pos.media;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

/**
 * A price action the ad platform has validated for this checkout.
 *
 * <p>An offer is what the register receives when a shopper accepts an {@code APPLY_OFFER} call to
 * action and the platform confirms the token: never the creative's copy, always this structured
 * answer. It is either a fixed {@link #getAmount() amount} off or a {@link #getPercentage()
 * percentage} off — exactly one — and applies to the whole basket or to one SKU depending on {@link
 * #getScope()}. How the register realises it (a basket credit, a line discount) is the register's
 * choice; the SDK carries the offer and its provenance ({@link #getCreativeId()}).
 */
public final class Offer {

  /** What the offer applies to. */
  public enum Scope {
    /** The basket total. */
    BASKET,
    /** One SKU; {@link Offer#getSku()} says which. */
    LINE_ITEM
  }

  private final String id;
  private final Scope scope;
  private final String sku;
  private final BigDecimal amount;
  private final BigDecimal percentage;
  private final Instant expiry;
  private final String creativeId;

  private Offer(Builder builder) {
    this.id = Objects.requireNonNull(builder.id, "id");
    this.scope = Objects.requireNonNull(builder.scope, "scope");
    this.creativeId = Objects.requireNonNull(builder.creativeId, "creativeId");
    if (builder.scope == Scope.LINE_ITEM && builder.sku == null) {
      throw new IllegalArgumentException("a LINE_ITEM offer requires a sku");
    }
    if ((builder.amount == null) == (builder.percentage == null)) {
      throw new IllegalArgumentException("exactly one of amount or percentage is required");
    }
    if (builder.amount != null && builder.amount.signum() <= 0) {
      throw new IllegalArgumentException("amount must be positive");
    }
    if (builder.percentage != null
        && (builder.percentage.signum() <= 0
            || builder.percentage.compareTo(BigDecimal.valueOf(100)) > 0)) {
      throw new IllegalArgumentException("percentage must be in (0, 100]");
    }
    this.sku = builder.sku;
    this.amount = builder.amount;
    this.percentage = builder.percentage;
    this.expiry = builder.expiry;
  }

  public static Builder builder() {
    return new Builder();
  }

  /** Platform identifier of the offer. */
  public String getId() {
    return id;
  }

  public Scope getScope() {
    return scope;
  }

  /** The SKU a {@link Scope#LINE_ITEM} offer applies to; {@code null} for basket offers. */
  public String getSku() {
    return sku;
  }

  /** Fixed amount off in the session currency, or {@code null} when the offer is a percentage. */
  public BigDecimal getAmount() {
    return amount;
  }

  /** Percentage off, {@code 0 < p <= 100}, or {@code null} when the offer is a fixed amount. */
  public BigDecimal getPercentage() {
    return percentage;
  }

  /** When the offer stops being applicable, or {@code null} when the platform set no expiry. */
  public Instant getExpiry() {
    return expiry;
  }

  /** The creative whose call to action produced this offer. */
  public String getCreativeId() {
    return creativeId;
  }

  public Builder toBuilder() {
    return builder()
        .id(id)
        .scope(scope)
        .sku(sku)
        .amount(amount)
        .percentage(percentage)
        .expiry(expiry)
        .creativeId(creativeId);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof Offer)) {
      return false;
    }
    Offer other = (Offer) o;
    return id.equals(other.id)
        && scope == other.scope
        && Objects.equals(sku, other.sku)
        && Objects.equals(amount, other.amount)
        && Objects.equals(percentage, other.percentage)
        && Objects.equals(expiry, other.expiry)
        && creativeId.equals(other.creativeId);
  }

  @Override
  public int hashCode() {
    return Objects.hash(id, scope, sku, amount, percentage, expiry, creativeId);
  }

  @Override
  public String toString() {
    return "Offer{id='"
        + id
        + "', scope="
        + scope
        + (sku == null ? "" : ", sku='" + sku + "'")
        + (amount == null ? ", percentage=" + percentage : ", amount=" + amount)
        + ", creativeId='"
        + creativeId
        + "'}";
  }

  /** Builder for {@link Offer}. */
  public static final class Builder {

    private String id;
    private Scope scope;
    private String sku;
    private BigDecimal amount;
    private BigDecimal percentage;
    private Instant expiry;
    private String creativeId;

    private Builder() {}

    public Builder id(String id) {
      this.id = id;
      return this;
    }

    public Builder scope(Scope scope) {
      this.scope = scope;
      return this;
    }

    /** Required when the scope is {@link Scope#LINE_ITEM}. */
    public Builder sku(String sku) {
      this.sku = sku;
      return this;
    }

    /** Fixed amount off; mutually exclusive with {@link #percentage}. */
    public Builder amount(BigDecimal amount) {
      this.amount = amount;
      return this;
    }

    /** Percentage off; mutually exclusive with {@link #amount}. */
    public Builder percentage(BigDecimal percentage) {
      this.percentage = percentage;
      return this;
    }

    public Builder expiry(Instant expiry) {
      this.expiry = expiry;
      return this;
    }

    public Builder creativeId(String creativeId) {
      this.creativeId = creativeId;
      return this;
    }

    public Offer build() {
      return new Offer(this);
    }
  }
}
