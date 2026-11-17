package com.bilt.pos.session.settlement;

import java.math.BigDecimal;
import java.time.Instant;

/** Persistable reference for updating, completing or releasing a NEXO Reservation hold. */
public final class ReservationReference {
  private final String saleReferenceId;
  private final String poiTransactionId;
  private final Instant poiTransactionTimestamp;
  private final BigDecimal authorizedAmount;
  private final String validityDate;
  private final String merchantId;
  private final String poiId;
  private final String saleId;

  private ReservationReference(Builder b) {
    this.saleReferenceId = b.saleReferenceId;
    this.poiTransactionId = b.poiTransactionId;
    this.poiTransactionTimestamp = b.poiTransactionTimestamp;
    this.authorizedAmount = b.authorizedAmount;
    this.validityDate = b.validityDate;
    this.merchantId = b.merchantId;
    this.poiId = b.poiId;
    this.saleId = b.saleId;
  }

  public static Builder builder() {
    return new Builder();
  }

  public String getSaleReferenceId() {
    return saleReferenceId;
  }

  public String getPoiTransactionId() {
    return poiTransactionId;
  }

  public Instant getPoiTransactionTimestamp() {
    return poiTransactionTimestamp;
  }

  public BigDecimal getAuthorizedAmount() {
    return authorizedAmount;
  }

  public String getValidityDate() {
    return validityDate;
  }

  public String getMerchantId() {
    return merchantId;
  }

  public String getPoiId() {
    return poiId;
  }

  public String getSaleId() {
    return saleId;
  }

  public static final class Builder {
    private String saleReferenceId;
    private String poiTransactionId;
    private Instant poiTransactionTimestamp;
    private BigDecimal authorizedAmount;
    private String validityDate;
    private String merchantId;
    private String poiId;
    private String saleId;

    private Builder() {}

    public Builder saleReferenceId(String value) {
      this.saleReferenceId = value;
      return this;
    }

    public Builder poiTransactionId(String value) {
      this.poiTransactionId = value;
      return this;
    }

    public Builder poiTransactionTimestamp(Instant value) {
      this.poiTransactionTimestamp = value;
      return this;
    }

    public Builder authorizedAmount(BigDecimal value) {
      this.authorizedAmount = value;
      return this;
    }

    public Builder validityDate(String value) {
      this.validityDate = value;
      return this;
    }

    public Builder merchantId(String value) {
      this.merchantId = value;
      return this;
    }

    public Builder poiId(String value) {
      this.poiId = value;
      return this;
    }

    public Builder saleId(String value) {
      this.saleId = value;
      return this;
    }

    public ReservationReference build() {
      return new ReservationReference(this);
    }
  }
}
