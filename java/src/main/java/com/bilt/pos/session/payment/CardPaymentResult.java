/*
 *    ____  _ _ _
 *   | __ )(_) | |_
 *   |  _ \| | | __|
 *   | |_) | | | |_
 *   |____/|_|_|\__|
 *
 *   Bilt POS SDK
 */
package com.bilt.pos.session.payment;

import com.bilt.pos.session.Receipt;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

/**
 * One card payment a settlement made: what the terminal authorized and the references to void or
 * refund it by. A settlement may hold several, in the order they were made (see {@code
 * SettlementResult.getCardPayments()}); each is one nexo {@code PaymentRequest}.
 *
 * <p>Every reference is as the terminal reported it, so each may be {@code null} when the response
 * did not carry it.
 *
 * <p>Bilt rewards can pay part of a card payment (implicit loyalty with multi-tender off on the
 * terminal). Then {@link #getAmount()} is still everything the payment authorized, which is what a
 * void reverses, and {@link #getBiltRewardsAmount()} is the rewards part of it: the card paid
 * {@link #getCardAmount()}. A payment the rewards paid in full is not a card payment; see {@link
 * BiltRewardsPaymentResult}.
 */
public final class CardPaymentResult {

  private final BigDecimal amount;
  private final String poiTransactionId;
  private final Instant poiTransactionTimestamp;
  private final String approvalCode;
  private final String acquirerTransactionId;
  private final String paymentBrand;
  private final String maskedPan;
  private final Receipt customerReceipt;
  private final Receipt merchantReceipt;
  private final boolean partialApproval;
  private final BigDecimal biltRewardsAmount;

  private CardPaymentResult(Builder builder) {
    this.amount = Objects.requireNonNull(builder.amount, "amount");
    this.poiTransactionId = builder.poiTransactionId;
    this.poiTransactionTimestamp = builder.poiTransactionTimestamp;
    this.approvalCode = builder.approvalCode;
    this.acquirerTransactionId = builder.acquirerTransactionId;
    this.paymentBrand = builder.paymentBrand;
    this.maskedPan = builder.maskedPan;
    this.customerReceipt = builder.customerReceipt;
    this.merchantReceipt = builder.merchantReceipt;
    this.partialApproval = builder.partialApproval;
    this.biltRewardsAmount =
        builder.biltRewardsAmount == null ? BigDecimal.ZERO : builder.biltRewardsAmount;
  }

  public static Builder builder() {
    return new Builder();
  }

  /**
   * Amount the terminal authorized for this payment, cashback included, and Bilt rewards included
   * when they paid part of it ({@link #getBiltRewardsAmount()}).
   */
  public BigDecimal getAmount() {
    return amount;
  }

  /** Terminal reference of this payment, for void and refund. */
  public String getPoiTransactionId() {
    return poiTransactionId;
  }

  public Instant getPoiTransactionTimestamp() {
    return poiTransactionTimestamp;
  }

  public String getApprovalCode() {
    return approvalCode;
  }

  public String getAcquirerTransactionId() {
    return acquirerTransactionId;
  }

  public String getPaymentBrand() {
    return paymentBrand;
  }

  /** The card number as the terminal masked it, or {@code null} when it was not reported. */
  public String getMaskedPan() {
    return maskedPan;
  }

  public Receipt getCustomerReceipt() {
    return customerReceipt;
  }

  public Receipt getMerchantReceipt() {
    return merchantReceipt;
  }

  /** Whether the terminal authorized less than this payment requested. */
  public boolean isPartialApproval() {
    return partialApproval;
  }

  /**
   * The part of {@link #getAmount()} the terminal paid with Bilt rewards, as it reported it in
   * {@code biltRewardsAmount}; zero when the card paid it all.
   */
  public BigDecimal getBiltRewardsAmount() {
    return biltRewardsAmount;
  }

  /**
   * The part of {@link #getAmount()} the card paid: the amount less {@link
   * #getBiltRewardsAmount()}.
   */
  public BigDecimal getCardAmount() {
    return amount.subtract(biltRewardsAmount);
  }

  /** Builder for {@link CardPaymentResult}. Intended for SDK use. */
  public static final class Builder {

    private BigDecimal amount;
    private String poiTransactionId;
    private Instant poiTransactionTimestamp;
    private String approvalCode;
    private String acquirerTransactionId;
    private String paymentBrand;
    private String maskedPan;
    private Receipt customerReceipt;
    private Receipt merchantReceipt;
    private boolean partialApproval;
    private BigDecimal biltRewardsAmount;

    private Builder() {}

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

    public Builder acquirerTransactionId(String acquirerTransactionId) {
      this.acquirerTransactionId = acquirerTransactionId;
      return this;
    }

    public Builder paymentBrand(String paymentBrand) {
      this.paymentBrand = paymentBrand;
      return this;
    }

    public Builder maskedPan(String maskedPan) {
      this.maskedPan = maskedPan;
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

    public Builder partialApproval(boolean partialApproval) {
      this.partialApproval = partialApproval;
      return this;
    }

    public Builder biltRewardsAmount(BigDecimal biltRewardsAmount) {
      this.biltRewardsAmount = biltRewardsAmount;
      return this;
    }

    public CardPaymentResult build() {
      return new CardPaymentResult(this);
    }
  }
}
