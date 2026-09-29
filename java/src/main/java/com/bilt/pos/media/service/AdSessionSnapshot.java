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

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * What the ad decision service needs to know about a shopper session, at one moment.
 *
 * <p>A snapshot is the widget's projection of the checkout: where it is happening, what is in the
 * basket, who (if anyone) is identified, and which phase the checkout is in. It is a value the
 * widget rebuilds and hands over in full on every {@link AdDecisionService#updateSession}; the
 * service diffs if it cares. It carries no session object, so the service contract does not move
 * when the session API does.
 *
 * <p>{@link #getPhase()} is a free-form string for now — {@code "browsing"}, {@code "tendering"},
 * {@code "complete"} as the widget names them. A typed {@code CheckoutPhase} enum arrives in a
 * later ticket and will replace the string; callers should treat the value as opaque until then.
 *
 * <p>{@link #getMemberId()} is the only personal identifier and is present only for an identified
 * member; the platform, not the SDK, resolves it to a profile.
 */
public final class AdSessionSnapshot {

  private final String saleId;
  private final String storeLocation;
  private final String laneId;
  private final String currency;
  private final String phase;
  private final Map<String, String> attributes;
  private final String memberId;
  private final List<LineSummary> lines;

  private AdSessionSnapshot(Builder builder) {
    this.saleId = Objects.requireNonNull(builder.saleId, "saleId");
    this.storeLocation = Objects.requireNonNull(builder.storeLocation, "storeLocation");
    this.laneId = builder.laneId;
    this.currency = Objects.requireNonNull(builder.currency, "currency");
    this.phase = Objects.requireNonNull(builder.phase, "phase");
    this.attributes = Collections.unmodifiableMap(new LinkedHashMap<>(builder.attributes));
    this.memberId = builder.memberId;
    this.lines = Collections.unmodifiableList(new ArrayList<>(builder.lines));
  }

  public static Builder builder() {
    return new Builder();
  }

  /** The register's identifier for this sale. */
  public String getSaleId() {
    return saleId;
  }

  /** The store, in whatever form the deployment identifies stores. */
  public String getStoreLocation() {
    return storeLocation;
  }

  /** Lane or terminal identifier within the store, or {@code null}. */
  public String getLaneId() {
    return laneId;
  }

  /** ISO 4217 currency code of the basket, e.g. {@code "USD"}. */
  public String getCurrency() {
    return currency;
  }

  /** The checkout phase as the widget names it; free-form until {@code CheckoutPhase} lands. */
  public String getPhase() {
    return phase;
  }

  /** Deployment-defined pass-through attributes. Never {@code null}. */
  public Map<String, String> getAttributes() {
    return attributes;
  }

  /** The identified member's loyalty id, or {@code null} for a guest. */
  public String getMemberId() {
    return memberId;
  }

  /** The basket's lines, in register order. Never {@code null}. */
  public List<LineSummary> getLines() {
    return lines;
  }

  public Builder toBuilder() {
    return builder()
        .saleId(saleId)
        .storeLocation(storeLocation)
        .laneId(laneId)
        .currency(currency)
        .phase(phase)
        .attributes(attributes)
        .memberId(memberId)
        .lines(lines);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof AdSessionSnapshot)) {
      return false;
    }
    AdSessionSnapshot other = (AdSessionSnapshot) o;
    return saleId.equals(other.saleId)
        && storeLocation.equals(other.storeLocation)
        && Objects.equals(laneId, other.laneId)
        && currency.equals(other.currency)
        && phase.equals(other.phase)
        && attributes.equals(other.attributes)
        && Objects.equals(memberId, other.memberId)
        && lines.equals(other.lines);
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        saleId, storeLocation, laneId, currency, phase, attributes, memberId, lines);
  }

  @Override
  public String toString() {
    return "AdSessionSnapshot{saleId='"
        + saleId
        + "', store='"
        + storeLocation
        + "', phase='"
        + phase
        + "', lines="
        + lines.size()
        + (memberId == null ? "" : ", member")
        + "}";
  }

  /** One basket line as the ad platform sees it: catalog identity, quantity, price, category. */
  public static final class LineSummary {

    private final String sku;
    private final String description;
    private final int quantity;
    private final BigDecimal unitPrice;
    private final String category;
    private final Map<String, String> metadata;

    private LineSummary(Builder builder) {
      this.sku = Objects.requireNonNull(builder.sku, "sku");
      this.description = builder.description;
      if (builder.quantity <= 0) {
        throw new IllegalArgumentException("quantity must be positive");
      }
      this.quantity = builder.quantity;
      this.unitPrice = Objects.requireNonNull(builder.unitPrice, "unitPrice");
      this.category = builder.category;
      this.metadata = Collections.unmodifiableMap(new LinkedHashMap<>(builder.metadata));
    }

    public static Builder builder() {
      return new Builder();
    }

    /** Shorthand for a line with no category or metadata. */
    public static LineSummary of(
        String sku, String description, int quantity, BigDecimal unitPrice) {
      return builder()
          .sku(sku)
          .description(description)
          .quantity(quantity)
          .unitPrice(unitPrice)
          .build();
    }

    public String getSku() {
      return sku;
    }

    /** Human-readable description, or {@code null}. */
    public String getDescription() {
      return description;
    }

    public int getQuantity() {
      return quantity;
    }

    public BigDecimal getUnitPrice() {
      return unitPrice;
    }

    /** Product category, or {@code null}. */
    public String getCategory() {
      return category;
    }

    /** Pass-through metadata. Never {@code null}. */
    public Map<String, String> getMetadata() {
      return metadata;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (!(o instanceof LineSummary)) {
        return false;
      }
      LineSummary other = (LineSummary) o;
      return sku.equals(other.sku)
          && Objects.equals(description, other.description)
          && quantity == other.quantity
          && unitPrice.compareTo(other.unitPrice) == 0
          && Objects.equals(category, other.category)
          && metadata.equals(other.metadata);
    }

    @Override
    public int hashCode() {
      return Objects.hash(
          sku, description, quantity, unitPrice.stripTrailingZeros(), category, metadata);
    }

    @Override
    public String toString() {
      return "LineSummary{sku='"
          + sku
          + "', quantity="
          + quantity
          + ", unitPrice="
          + unitPrice
          + "}";
    }

    /** Builder for {@link LineSummary}. */
    public static final class Builder {

      private String sku;
      private String description;
      private int quantity = 1;
      private BigDecimal unitPrice;
      private String category;
      private Map<String, String> metadata = new LinkedHashMap<>();

      private Builder() {}

      public Builder sku(String sku) {
        this.sku = sku;
        return this;
      }

      public Builder description(String description) {
        this.description = description;
        return this;
      }

      /** Default 1. */
      public Builder quantity(int quantity) {
        this.quantity = quantity;
        return this;
      }

      public Builder unitPrice(BigDecimal unitPrice) {
        this.unitPrice = unitPrice;
        return this;
      }

      public Builder category(String category) {
        this.category = category;
        return this;
      }

      public Builder metadata(Map<String, String> metadata) {
        this.metadata = metadata == null ? new LinkedHashMap<>() : new LinkedHashMap<>(metadata);
        return this;
      }

      public LineSummary build() {
        return new LineSummary(this);
      }
    }
  }

  /** Builder for {@link AdSessionSnapshot}. */
  public static final class Builder {

    private String saleId;
    private String storeLocation;
    private String laneId;
    private String currency;
    private String phase;
    private Map<String, String> attributes = new LinkedHashMap<>();
    private String memberId;
    private List<LineSummary> lines = new ArrayList<>();

    private Builder() {}

    public Builder saleId(String saleId) {
      this.saleId = saleId;
      return this;
    }

    public Builder storeLocation(String storeLocation) {
      this.storeLocation = storeLocation;
      return this;
    }

    public Builder laneId(String laneId) {
      this.laneId = laneId;
      return this;
    }

    public Builder currency(String currency) {
      this.currency = currency;
      return this;
    }

    public Builder phase(String phase) {
      this.phase = phase;
      return this;
    }

    public Builder attributes(Map<String, String> attributes) {
      this.attributes =
          attributes == null ? new LinkedHashMap<>() : new LinkedHashMap<>(attributes);
      return this;
    }

    public Builder attribute(String name, String value) {
      attributes.put(Objects.requireNonNull(name, "name"), Objects.requireNonNull(value, "value"));
      return this;
    }

    public Builder memberId(String memberId) {
      this.memberId = memberId;
      return this;
    }

    public Builder lines(List<LineSummary> lines) {
      this.lines = lines == null ? new ArrayList<>() : new ArrayList<>(lines);
      return this;
    }

    public Builder addLine(LineSummary line) {
      lines.add(Objects.requireNonNull(line, "line"));
      return this;
    }

    public AdSessionSnapshot build() {
      return new AdSessionSnapshot(this);
    }
  }
}
