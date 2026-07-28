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
import com.bilt.pos.session.basket.Basket;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Final outcome of a successful payment orchestration.
 *
 * <p>The loyalty award is best-effort (the terminal stores-and-forwards when
 * the loyalty host is down): a failed award does not fail the checkout but is
 * reported in {@link #getWarnings()}.</p>
 */
public final class CheckoutResult {

    private final boolean success;
    private final Basket finalBasket;

    // Payment breakdown
    private final BigDecimal authorizedAmount;
    private final BigDecimal storedValueAmountUsed;
    private final BigDecimal cardAmountCharged;
    private final String approvalCode;
    private final String acquirerTransactionId;
    private final String paymentBrand;

    // Loyalty — redeemed during payment
    private final List<RedeemedRebate> redeemedRebates;
    private final BigDecimal totalRebateAmount;
    private final int pointsRedeemed;
    private final BigDecimal pointsMonetaryValue;

    // Loyalty — earned
    private final List<EarnedReward> earnedRewards;
    private final int totalPointsEarned;
    private final int pointsBalance;
    private final List<String> promotionMessages;

    // Receipt
    private final Receipt customerReceipt;
    private final Receipt merchantReceipt;

    // Transaction references (for void/refund)
    private final String poiTransactionId;
    private final Instant poiTransactionTimestamp;
    private final String storedValuePoiTransactionId;
    private final Instant storedValuePoiTransactionTimestamp;
    private final String awardPoiTransactionId;
    private final Instant awardPoiTransactionTimestamp;
    private final String rebatePoiTransactionId;
    private final Instant rebatePoiTransactionTimestamp;
    private final String redemptionPoiTransactionId;
    private final Instant redemptionPoiTransactionTimestamp;

    private final List<String> warnings;

    private CheckoutResult(Builder builder) {
        this.success = builder.success;
        this.finalBasket = builder.finalBasket;
        this.authorizedAmount = builder.authorizedAmount;
        this.storedValueAmountUsed = builder.storedValueAmountUsed;
        this.cardAmountCharged = builder.cardAmountCharged;
        this.approvalCode = builder.approvalCode;
        this.acquirerTransactionId = builder.acquirerTransactionId;
        this.paymentBrand = builder.paymentBrand;
        this.redeemedRebates = Collections.unmodifiableList(new ArrayList<>(builder.redeemedRebates));
        this.totalRebateAmount = builder.totalRebateAmount;
        this.pointsRedeemed = builder.pointsRedeemed;
        this.pointsMonetaryValue = builder.pointsMonetaryValue;
        this.earnedRewards = Collections.unmodifiableList(new ArrayList<>(builder.earnedRewards));
        this.totalPointsEarned = builder.totalPointsEarned;
        this.pointsBalance = builder.pointsBalance;
        this.promotionMessages = Collections.unmodifiableList(new ArrayList<>(builder.promotionMessages));
        this.customerReceipt = builder.customerReceipt;
        this.merchantReceipt = builder.merchantReceipt;
        this.poiTransactionId = builder.poiTransactionId;
        this.poiTransactionTimestamp = builder.poiTransactionTimestamp;
        this.storedValuePoiTransactionId = builder.storedValuePoiTransactionId;
        this.storedValuePoiTransactionTimestamp = builder.storedValuePoiTransactionTimestamp;
        this.awardPoiTransactionId = builder.awardPoiTransactionId;
        this.awardPoiTransactionTimestamp = builder.awardPoiTransactionTimestamp;
        this.rebatePoiTransactionId = builder.rebatePoiTransactionId;
        this.rebatePoiTransactionTimestamp = builder.rebatePoiTransactionTimestamp;
        this.redemptionPoiTransactionId = builder.redemptionPoiTransactionId;
        this.redemptionPoiTransactionTimestamp = builder.redemptionPoiTransactionTimestamp;
        this.warnings = Collections.unmodifiableList(new ArrayList<>(builder.warnings));
    }

    public static Builder builder() {
        return new Builder();
    }

    public boolean isSuccess() {
        return success;
    }

    /** Basket with the payment breakdown totals populated. */
    public Basket getFinalBasket() {
        return finalBasket;
    }

    /** Total authorized across all tenders. */
    public BigDecimal getAuthorizedAmount() {
        return authorizedAmount;
    }

    /** Amount charged to the stored value card; zero if no split. */
    public BigDecimal getStoredValueAmountUsed() {
        return storedValueAmountUsed;
    }

    /** Amount charged to the payment card; zero if fully covered otherwise. */
    public BigDecimal getCardAmountCharged() {
        return cardAmountCharged;
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

    public List<RedeemedRebate> getRedeemedRebates() {
        return redeemedRebates;
    }

    public BigDecimal getTotalRebateAmount() {
        return totalRebateAmount;
    }

    public int getPointsRedeemed() {
        return pointsRedeemed;
    }

    public BigDecimal getPointsMonetaryValue() {
        return pointsMonetaryValue;
    }

    public List<EarnedReward> getEarnedRewards() {
        return earnedRewards;
    }

    public int getTotalPointsEarned() {
        return totalPointsEarned;
    }

    /** Member's point balance after the award; {@code 0} when not reported. */
    public int getPointsBalance() {
        return pointsBalance;
    }

    /** Promotional messages for the receipt or terminal display. */
    public List<String> getPromotionMessages() {
        return promotionMessages;
    }

    public Receipt getCustomerReceipt() {
        return customerReceipt;
    }

    public Receipt getMerchantReceipt() {
        return merchantReceipt;
    }

    /** Terminal reference of the card payment, for void/refund. */
    public String getPoiTransactionId() {
        return poiTransactionId;
    }

    public Instant getPoiTransactionTimestamp() {
        return poiTransactionTimestamp;
    }

    /**
     * Terminal reference of the stored value leg in a split tender, or
     * {@code null} when no separate gift card charge was made. A same-session
     * void reverses both legs.
     */
    public String getStoredValuePoiTransactionId() {
        return storedValuePoiTransactionId;
    }

    public Instant getStoredValuePoiTransactionTimestamp() {
        return storedValuePoiTransactionTimestamp;
    }

    /**
     * Terminal reference of the loyalty award, or {@code null} when no award
     * was submitted. The award-reversal contract references this ID.
     */
    public String getAwardPoiTransactionId() {
        return awardPoiTransactionId;
    }

    public Instant getAwardPoiTransactionTimestamp() {
        return awardPoiTransactionTimestamp;
    }

    /**
     * Terminal reference of the committed rebate redemption, or {@code null}
     * when no rebates were applied. A void of a checkout with no payment
     * legs reverses this movement.
     */
    public String getRebatePoiTransactionId() {
        return rebatePoiTransactionId;
    }

    public Instant getRebatePoiTransactionTimestamp() {
        return rebatePoiTransactionTimestamp;
    }

    /**
     * Terminal reference of the committed point/reward redemption, or
     * {@code null} when no points were redeemed. A void of a checkout with
     * no payment legs reverses this movement.
     */
    public String getRedemptionPoiTransactionId() {
        return redemptionPoiTransactionId;
    }

    public Instant getRedemptionPoiTransactionTimestamp() {
        return redemptionPoiTransactionTimestamp;
    }

    /** Non-fatal problems, e.g. a failed loyalty award. */
    public List<String> getWarnings() {
        return warnings;
    }

    /** Builder for {@link CheckoutResult}. Intended for SDK use. */
    public static final class Builder {

        private boolean success;
        private Basket finalBasket;
        private BigDecimal authorizedAmount = BigDecimal.ZERO;
        private BigDecimal storedValueAmountUsed = BigDecimal.ZERO;
        private BigDecimal cardAmountCharged = BigDecimal.ZERO;
        private String approvalCode;
        private String acquirerTransactionId;
        private String paymentBrand;
        private List<RedeemedRebate> redeemedRebates = new ArrayList<>();
        private BigDecimal totalRebateAmount = BigDecimal.ZERO;
        private int pointsRedeemed;
        private BigDecimal pointsMonetaryValue = BigDecimal.ZERO;
        private List<EarnedReward> earnedRewards = new ArrayList<>();
        private int totalPointsEarned;
        private int pointsBalance;
        private List<String> promotionMessages = new ArrayList<>();
        private Receipt customerReceipt;
        private Receipt merchantReceipt;
        private String poiTransactionId;
        private Instant poiTransactionTimestamp;
        private String storedValuePoiTransactionId;
        private Instant storedValuePoiTransactionTimestamp;
        private String awardPoiTransactionId;
        private Instant awardPoiTransactionTimestamp;
        private String rebatePoiTransactionId;
        private Instant rebatePoiTransactionTimestamp;
        private String redemptionPoiTransactionId;
        private Instant redemptionPoiTransactionTimestamp;
        private List<String> warnings = new ArrayList<>();

        private Builder() {
        }

        public Builder success(boolean success) {
            this.success = success;
            return this;
        }

        public Builder finalBasket(Basket finalBasket) {
            this.finalBasket = finalBasket;
            return this;
        }

        public Builder authorizedAmount(BigDecimal authorizedAmount) {
            this.authorizedAmount = authorizedAmount;
            return this;
        }

        public Builder storedValueAmountUsed(BigDecimal storedValueAmountUsed) {
            this.storedValueAmountUsed = storedValueAmountUsed;
            return this;
        }

        public Builder cardAmountCharged(BigDecimal cardAmountCharged) {
            this.cardAmountCharged = cardAmountCharged;
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

        public Builder redeemedRebates(List<RedeemedRebate> redeemedRebates) {
            this.redeemedRebates = redeemedRebates;
            return this;
        }

        public Builder totalRebateAmount(BigDecimal totalRebateAmount) {
            this.totalRebateAmount = totalRebateAmount;
            return this;
        }

        public Builder pointsRedeemed(int pointsRedeemed) {
            this.pointsRedeemed = pointsRedeemed;
            return this;
        }

        public Builder pointsMonetaryValue(BigDecimal pointsMonetaryValue) {
            this.pointsMonetaryValue = pointsMonetaryValue;
            return this;
        }

        public Builder earnedRewards(List<EarnedReward> earnedRewards) {
            this.earnedRewards = earnedRewards;
            return this;
        }

        public Builder totalPointsEarned(int totalPointsEarned) {
            this.totalPointsEarned = totalPointsEarned;
            return this;
        }

        public Builder pointsBalance(int pointsBalance) {
            this.pointsBalance = pointsBalance;
            return this;
        }

        public Builder promotionMessages(List<String> promotionMessages) {
            this.promotionMessages = promotionMessages;
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

        public Builder poiTransactionId(String poiTransactionId) {
            this.poiTransactionId = poiTransactionId;
            return this;
        }

        public Builder poiTransactionTimestamp(Instant poiTransactionTimestamp) {
            this.poiTransactionTimestamp = poiTransactionTimestamp;
            return this;
        }

        public Builder storedValuePoiTransactionId(String storedValuePoiTransactionId) {
            this.storedValuePoiTransactionId = storedValuePoiTransactionId;
            return this;
        }

        public Builder storedValuePoiTransactionTimestamp(Instant storedValuePoiTransactionTimestamp) {
            this.storedValuePoiTransactionTimestamp = storedValuePoiTransactionTimestamp;
            return this;
        }

        public Builder awardPoiTransactionId(String awardPoiTransactionId) {
            this.awardPoiTransactionId = awardPoiTransactionId;
            return this;
        }

        public Builder awardPoiTransactionTimestamp(Instant awardPoiTransactionTimestamp) {
            this.awardPoiTransactionTimestamp = awardPoiTransactionTimestamp;
            return this;
        }

        public Builder rebatePoiTransactionId(String rebatePoiTransactionId) {
            this.rebatePoiTransactionId = rebatePoiTransactionId;
            return this;
        }

        public Builder rebatePoiTransactionTimestamp(Instant rebatePoiTransactionTimestamp) {
            this.rebatePoiTransactionTimestamp = rebatePoiTransactionTimestamp;
            return this;
        }

        public Builder redemptionPoiTransactionId(String redemptionPoiTransactionId) {
            this.redemptionPoiTransactionId = redemptionPoiTransactionId;
            return this;
        }

        public Builder redemptionPoiTransactionTimestamp(Instant redemptionPoiTransactionTimestamp) {
            this.redemptionPoiTransactionTimestamp = redemptionPoiTransactionTimestamp;
            return this;
        }

        public Builder warning(String warning) {
            this.warnings.add(warning);
            return this;
        }

        public CheckoutResult build() {
            return new CheckoutResult(this);
        }
    }
}
