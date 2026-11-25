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

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

/**
 * One tender leg a void reversed or a refund returned — a card payment, a Bilt rewards payment or a
 * stored value payment — with the terminal's own reference and receipts for it. A sale with several
 * card payments gets one per card payment, so the register can print and store every receipt.
 *
 * <p>When Bilt rewards paid part of a card payment, its card leg's {@link #getAmount()} is
 * everything the reversal or refund returned and {@link #getBiltRewardsAmount()} is the part that
 * went back to the member's rewards.
 */
public final class TenderMovement {

  private final TenderType tenderType;
  private final String originalPoiTransactionId;
  private final BigDecimal amount;
  private final String poiTransactionId;
  private final Instant poiTransactionTimestamp;
  private final String approvalCode;
  private final String serviceId;
  private final Receipt customerReceipt;
  private final Receipt merchantReceipt;
  private final BigDecimal biltRewardsAmount;

  private TenderMovement(Builder builder) {
    this.tenderType = Objects.requireNonNull(builder.tenderType, "tenderType");
    this.originalPoiTransactionId = builder.originalPoiTransactionId;
    this.amount = builder.amount;
    this.poiTransactionId = builder.poiTransactionId;
    this.poiTransactionTimestamp = builder.poiTransactionTimestamp;
    this.approvalCode = builder.approvalCode;
    this.serviceId = builder.serviceId;
    this.customerReceipt = builder.customerReceipt;
    this.merchantReceipt = builder.merchantReceipt;
    this.biltRewardsAmount = builder.biltRewardsAmount;
  }

  public static Builder builder() {
    return new Builder();
  }

  /** The tender this leg returned money to: {@code CARD} or {@code STORED_VALUE}. */
  public TenderType getTenderType() {
    return tenderType;
  }

  /** The payment's POI transaction this reversed or refunded; {@code null} when unlinked. */
  public String getOriginalPoiTransactionId() {
    return originalPoiTransactionId;
  }

  /**
   * Amount returned to the tender; {@code null} when the terminal did not report it and the SDK had
   * no amount for the original payment.
   */
  public BigDecimal getAmount() {
    return amount;
  }

  /** Terminal reference of the reversal or refund itself. */
  public String getPoiTransactionId() {
    return poiTransactionId;
  }

  public Instant getPoiTransactionTimestamp() {
    return poiTransactionTimestamp;
  }

  /** Approval code of a refund; {@code null} for a reversal or when not reported. */
  public String getApprovalCode() {
    return approvalCode;
  }

  /** The Nexo {@code ServiceID} the reversal or refund request was sent with. */
  public String getServiceId() {
    return serviceId;
  }

  public Receipt getCustomerReceipt() {
    return customerReceipt;
  }

  public Receipt getMerchantReceipt() {
    return merchantReceipt;
  }

  /**
   * The part of {@link #getAmount()} that went back to Bilt rewards: on a card leg, the rewards
   * part of a card payment rewards paid only part of; on a {@link TenderType#BILT_REWARDS} leg, the
   * whole amount. {@code null} when none did, or when it is not known.
   */
  public BigDecimal getBiltRewardsAmount() {
    return biltRewardsAmount;
  }

  /** Builder for {@link TenderMovement}. Intended for SDK use. */
  public static final class Builder {

    private TenderType tenderType;
    private String originalPoiTransactionId;
    private BigDecimal amount;
    private String poiTransactionId;
    private Instant poiTransactionTimestamp;
    private String approvalCode;
    private String serviceId;
    private Receipt customerReceipt;
    private Receipt merchantReceipt;
    private BigDecimal biltRewardsAmount;

    private Builder() {}

    public Builder tenderType(TenderType tenderType) {
      this.tenderType = tenderType;
      return this;
    }

    public Builder originalPoiTransactionId(String originalPoiTransactionId) {
      this.originalPoiTransactionId = originalPoiTransactionId;
      return this;
    }

    public Builder amount(BigDecimal amount) {
      this.amount = amount;
      return this;
    }

    public Builder poiTransactionId(String poiTransactionId) {
      this.poiTransactionId = poiTransactionId;
      return this;
    }

    public Builder poiTransactionTimestamp(Instant poiTransactionTimestamp) {
      this.poiTransactionTimestamp = poiTransactionTimestamp;
      return this;
    }

    public Builder approvalCode(String approvalCode) {
      this.approvalCode = approvalCode;
      return this;
    }

    public Builder serviceId(String serviceId) {
      this.serviceId = serviceId;
      return this;
    }

    public Builder customerReceipt(Receipt customerReceipt) {
      this.customerReceipt = customerReceipt;
      return this;
    }

    public Builder merchantReceipt(Receipt merchantReceipt) {
      this.merchantReceipt = merchantReceipt;
      return this;
    }

    public Builder biltRewardsAmount(BigDecimal biltRewardsAmount) {
      this.biltRewardsAmount = biltRewardsAmount;
      return this;
    }

    public TenderMovement build() {
      return new TenderMovement(this);
    }
  }
}
