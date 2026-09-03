/*
 *    ____  _ _ _
 *   | __ )(_) | |_
 *   |  _ \| | | __|
 *   | |_) | | | |_
 *   |____/|_|_|\__|
 *
 *   Bilt POS SDK
 */
package com.bilt.pos.session.settlement;

import com.bilt.pos.session.Receipt;
import com.bilt.pos.session.basket.Basket;
import com.bilt.pos.session.payment.EarnedReward;
import com.bilt.pos.session.payment.RedeemedRebate;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Final outcome of a successful settlement orchestration.
 *
 * <p>For returns and exchanges, {@link #getMovements()} is the complete
 * ledger of externally visible money and loyalty movements that remain
 * committed after settlement. The refund aggregate getters report the
 * amounts returned by card, stored value, external register-managed tender,
 * and loyalty restoration.</p>
 *
 * <p>The loyalty award is best-effort (the terminal stores-and-forwards when
 * the loyalty host is down): a failed award does not fail the checkout but is
 * reported in {@link #getWarnings()}.</p>
 */
public final class SettlementResult {

    private final boolean success;
    private final Basket finalBasket;

    // Charge-side breakdown
    private final BigDecimal authorizedAmount;
    private final BigDecimal storedValueAmountUsed;
    private final BigDecimal storedValueLoadedAmount;
    private final BigDecimal cardAmountCharged;
    private final BigDecimal externalPaymentAmount;
    private final String approvalCode;
    private final String acquirerTransactionId;
    private final String paymentBrand;

    // Loyalty — redeemed during charge-side settlement
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

    private final BigDecimal cardRefundedAmount;
    private final BigDecimal storedValueRefundedAmount;
    private final BigDecimal externalRefundedAmount;
    private final BigDecimal loyaltyRefundedAmount;
    private final List<SettlementMovement> movements;
    private final List<String> warnings;

    private SettlementResult(Builder builder) {
        this.success = builder.success;
        this.finalBasket = builder.finalBasket;
        this.authorizedAmount = builder.authorizedAmount;
        this.storedValueAmountUsed = builder.storedValueAmountUsed;
        this.storedValueLoadedAmount = builder.storedValueLoadedAmount;
        this.cardAmountCharged = builder.cardAmountCharged;
        this.externalPaymentAmount = builder.externalPaymentAmount;
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
        this.cardRefundedAmount = builder.cardRefundedAmount;
        this.storedValueRefundedAmount = builder.storedValueRefundedAmount;
        this.externalRefundedAmount = builder.externalRefundedAmount;
        this.loyaltyRefundedAmount = builder.loyaltyRefundedAmount;
        this.movements = Collections.unmodifiableList(new ArrayList<>(builder.movements));
        this.warnings = Collections.unmodifiableList(new ArrayList<>(builder.warnings));
    }

    public static Builder builder() {
        return new Builder();
    }

    /** Returns a builder initialized with this result's current values. */
    public Builder toBuilder() {
        return new Builder(this);
    }

    public boolean isSuccess() {
        return success;
    }

    /** Basket with settlement breakdown totals populated. */
    public Basket getFinalBasket() {
        return finalBasket;
    }

    /** Total authorized by terminal-managed stored-value and card tenders. */
    public BigDecimal getAuthorizedAmount() {
        return authorizedAmount;
    }

    /** Amount charged to the stored value card; zero if no split. */
    public BigDecimal getStoredValueAmountUsed() {
        return storedValueAmountUsed;
    }

    /** Total value activated or loaded for stored value basket lines. */
    public BigDecimal getStoredValueLoadedAmount() {
        return storedValueLoadedAmount;
    }

    /** References for each stored value basket line fulfilled by this settlement. */
    public List<StoredValueLoadRecord> getStoredValueLoads() {
        List<StoredValueLoadRecord> loads = new ArrayList<>();
        for (SettlementMovement movement : movements) {
            if (movement.getStep() == SettlementStep.STORED_VALUE_LOAD
                    && movement.getPoiTransactionId() != null
                    && movement.getTarget() != null
                    && movement.getTarget().getType() == SettlementTarget.Type.BASKET_LINE) {
                loads.add(StoredValueLoadRecord.builder()
                        .basketReference(movement.getTarget().getBasketReference())
                        .amount(movement.getAmount())
                        .poiTransactionId(movement.getPoiTransactionId())
                        .poiTransactionTimestamp(movement.getPoiTransactionTimestamp())
                        .build());
            }
        }
        return Collections.unmodifiableList(loads);
    }

    /** Amount charged to the payment card; zero if fully covered otherwise. */
    public BigDecimal getCardAmountCharged() {
        return cardAmountCharged;
    }

    /** Amount collected through register-managed tenders such as cash. */
    public BigDecimal getExternalPaymentAmount() {
        return externalPaymentAmount;
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

    /** Total returned to payment cards during this settlement. */
    public BigDecimal getCardRefundedAmount() {
        return cardRefundedAmount;
    }

    /** Total restored to stored value cards during this settlement. */
    public BigDecimal getStoredValueRefundedAmount() {
        return storedValueRefundedAmount;
    }

    /** Total refunded or restored outside the terminal during this settlement. */
    public BigDecimal getExternalRefundedAmount() {
        return externalRefundedAmount;
    }

    /** Monetary value restored by loyalty refund movements. */
    public BigDecimal getLoyaltyRefundedAmount() {
        return loyaltyRefundedAmount;
    }

    /**
     * Every externally visible money/loyalty movement that remained
     * committed when this settlement completed successfully. Charge-side
     * movements that were later unwound by same-run recovery are excluded.
     */
    public List<SettlementMovement> getMovements() {
        return movements;
    }

    /** Non-fatal problems, e.g. a failed loyalty award. */
    public List<String> getWarnings() {
        return warnings;
    }

    /** References needed to void or refund against this completed settlement later. */
    public OriginalSaleRecord toOriginalSaleRecord(String memberId) {
        return OriginalSaleRecord.from(this, memberId);
    }

    /** Builder for {@link SettlementResult}. Intended for SDK use. */
    public static final class Builder {

        private boolean success;
        private Basket finalBasket;
        private BigDecimal authorizedAmount = BigDecimal.ZERO;
        private BigDecimal storedValueAmountUsed = BigDecimal.ZERO;
        private BigDecimal storedValueLoadedAmount = BigDecimal.ZERO;
        private BigDecimal cardAmountCharged = BigDecimal.ZERO;
        private BigDecimal externalPaymentAmount = BigDecimal.ZERO;
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
        private BigDecimal cardRefundedAmount = BigDecimal.ZERO;
        private BigDecimal storedValueRefundedAmount = BigDecimal.ZERO;
        private BigDecimal externalRefundedAmount = BigDecimal.ZERO;
        private BigDecimal loyaltyRefundedAmount = BigDecimal.ZERO;
        private List<SettlementMovement> movements = new ArrayList<>();
        private List<String> warnings = new ArrayList<>();

        private Builder() {
        }

        private Builder(SettlementResult result) {
            this.success = result.success;
            this.finalBasket = result.finalBasket;
            this.authorizedAmount = result.authorizedAmount;
            this.storedValueAmountUsed = result.storedValueAmountUsed;
            this.storedValueLoadedAmount = result.storedValueLoadedAmount;
            this.cardAmountCharged = result.cardAmountCharged;
            this.externalPaymentAmount = result.externalPaymentAmount;
            this.approvalCode = result.approvalCode;
            this.acquirerTransactionId = result.acquirerTransactionId;
            this.paymentBrand = result.paymentBrand;
            this.redeemedRebates = new ArrayList<>(result.redeemedRebates);
            this.totalRebateAmount = result.totalRebateAmount;
            this.pointsRedeemed = result.pointsRedeemed;
            this.pointsMonetaryValue = result.pointsMonetaryValue;
            this.earnedRewards = new ArrayList<>(result.earnedRewards);
            this.totalPointsEarned = result.totalPointsEarned;
            this.pointsBalance = result.pointsBalance;
            this.promotionMessages = new ArrayList<>(result.promotionMessages);
            this.customerReceipt = result.customerReceipt;
            this.merchantReceipt = result.merchantReceipt;
            this.poiTransactionId = result.poiTransactionId;
            this.poiTransactionTimestamp = result.poiTransactionTimestamp;
            this.storedValuePoiTransactionId = result.storedValuePoiTransactionId;
            this.storedValuePoiTransactionTimestamp = result.storedValuePoiTransactionTimestamp;
            this.awardPoiTransactionId = result.awardPoiTransactionId;
            this.awardPoiTransactionTimestamp = result.awardPoiTransactionTimestamp;
            this.rebatePoiTransactionId = result.rebatePoiTransactionId;
            this.rebatePoiTransactionTimestamp = result.rebatePoiTransactionTimestamp;
            this.redemptionPoiTransactionId = result.redemptionPoiTransactionId;
            this.redemptionPoiTransactionTimestamp = result.redemptionPoiTransactionTimestamp;
            this.cardRefundedAmount = result.cardRefundedAmount;
            this.storedValueRefundedAmount = result.storedValueRefundedAmount;
            this.externalRefundedAmount = result.externalRefundedAmount;
            this.loyaltyRefundedAmount = result.loyaltyRefundedAmount;
            this.movements = new ArrayList<>(result.movements);
            this.warnings = new ArrayList<>(result.warnings);
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

        public Builder storedValueLoadedAmount(BigDecimal storedValueLoadedAmount) {
            this.storedValueLoadedAmount = storedValueLoadedAmount;
            return this;
        }

        public Builder cardAmountCharged(BigDecimal cardAmountCharged) {
            this.cardAmountCharged = cardAmountCharged;
            return this;
        }

        public Builder externalPaymentAmount(BigDecimal externalPaymentAmount) {
            this.externalPaymentAmount = externalPaymentAmount;
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
            this.redeemedRebates = redeemedRebates == null
                    ? new ArrayList<>() : new ArrayList<>(redeemedRebates);
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
            this.earnedRewards = earnedRewards == null
                    ? new ArrayList<>() : new ArrayList<>(earnedRewards);
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
            this.promotionMessages = promotionMessages == null
                    ? new ArrayList<>() : new ArrayList<>(promotionMessages);
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

        public Builder cardRefundedAmount(BigDecimal cardRefundedAmount) {
            this.cardRefundedAmount = cardRefundedAmount;
            return this;
        }

        public Builder storedValueRefundedAmount(BigDecimal storedValueRefundedAmount) {
            this.storedValueRefundedAmount = storedValueRefundedAmount;
            return this;
        }

        public Builder externalRefundedAmount(BigDecimal externalRefundedAmount) {
            this.externalRefundedAmount = externalRefundedAmount;
            return this;
        }

        public Builder loyaltyRefundedAmount(BigDecimal loyaltyRefundedAmount) {
            this.loyaltyRefundedAmount = loyaltyRefundedAmount;
            return this;
        }

        public Builder movements(List<SettlementMovement> movements) {
            this.movements = movements == null ? new ArrayList<>() : new ArrayList<>(movements);
            return this;
        }

        public Builder movement(SettlementMovement movement) {
            this.movements.add(movement);
            return this;
        }

        /** Removes provisional tail movements after a failed step is reset. */
        public Builder removeLastMovements(int count) {
            if (count < 0 || count > movements.size()) {
                throw new IllegalArgumentException("invalid movement count " + count);
            }
            movements.subList(movements.size() - count, movements.size()).clear();
            return this;
        }

        public Builder warning(String warning) {
            this.warnings.add(warning);
            return this;
        }

        public SettlementResult build() {
            return new SettlementResult(this);
        }
    }
}
