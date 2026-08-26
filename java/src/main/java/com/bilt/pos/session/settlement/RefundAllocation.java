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

import com.bilt.pos.session.storedvalue.StoredValueCard;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

/**
 * Register-selected refund/restoration movement for a settlement with returns.
 *
 * <p>The SDK does not infer tender allocation policy. For a mixed card/gift
 * card/points sale, the register supplies one allocation per movement it wants
 * executed, including split refunds for a single returned item.</p>
 */
public final class RefundAllocation {

    private final RefundAllocationType type;
    private final BigDecimal amount;
    private final String originalPoiTransactionId;
    private final Instant originalPoiTransactionTimestamp;
    private final StoredValueCard storedValueCard;
    private final String memberId;

    private RefundAllocation(Builder builder) {
        this.type = Objects.requireNonNull(builder.type, "type");
        this.amount = builder.amount == null ? BigDecimal.ZERO : builder.amount;
        this.originalPoiTransactionId = builder.originalPoiTransactionId;
        this.originalPoiTransactionTimestamp = builder.originalPoiTransactionTimestamp;
        this.storedValueCard = builder.storedValueCard;
        this.memberId = builder.memberId;
        validate();
    }

    /** Linked card refund. */
    public static RefundAllocation card(BigDecimal amount, String originalPoiTransactionId,
                                        Instant originalPoiTransactionTimestamp) {
        return builder()
                .type(RefundAllocationType.CARD)
                .amount(amount)
                .originalPoiTransactionId(originalPoiTransactionId)
                .originalPoiTransactionTimestamp(originalPoiTransactionTimestamp)
                .build();
    }

    /** Linked card refund against a persisted original sale record. */
    public static RefundAllocation card(BigDecimal amount, OriginalSaleRecord originalSale) {
        Objects.requireNonNull(originalSale, "originalSale");
        return card(amount, originalSale.getCardPoiTransactionId(),
                originalSale.getCardPoiTransactionTimestamp());
    }

    /** Unlinked card refund. */
    public static RefundAllocation cardUnlinked(BigDecimal amount) {
        return builder()
                .type(RefundAllocationType.CARD)
                .amount(amount)
                .build();
    }

    /** Linked stored value refund using a Nexo PaymentRequest(Refund). */
    public static RefundAllocation storedValue(BigDecimal amount, String originalPoiTransactionId,
                                               Instant originalPoiTransactionTimestamp) {
        return builder()
                .type(RefundAllocationType.STORED_VALUE)
                .amount(amount)
                .originalPoiTransactionId(originalPoiTransactionId)
                .originalPoiTransactionTimestamp(originalPoiTransactionTimestamp)
                .build();
    }

    /** Linked stored value refund against a persisted original sale record. */
    public static RefundAllocation storedValue(BigDecimal amount, OriginalSaleRecord originalSale) {
        Objects.requireNonNull(originalSale, "originalSale");
        return storedValue(amount, originalSale.getStoredValuePoiTransactionId(),
                originalSale.getStoredValuePoiTransactionTimestamp());
    }

    /** Store-credit refund/restoration as a load onto the supplied stored value card. */
    public static RefundAllocation storeCredit(StoredValueCard card, BigDecimal amount) {
        return builder()
                .type(RefundAllocationType.STORE_CREDIT)
                .storedValueCard(card)
                .amount(amount)
                .build();
    }

    /** Refund a prior points/rewards redemption by original POI transaction reference. */
    public static RefundAllocation pointRedemption(BigDecimal amount,
                                                   String originalPoiTransactionId,
                                                   Instant originalPoiTransactionTimestamp,
                                                   String memberId) {
        return builder()
                .type(RefundAllocationType.POINT_REDEMPTION)
                .amount(amount)
                .originalPoiTransactionId(originalPoiTransactionId)
                .originalPoiTransactionTimestamp(originalPoiTransactionTimestamp)
                .memberId(memberId)
                .build();
    }

    /** Refund a prior points/rewards redemption from a persisted original sale record. */
    public static RefundAllocation pointRedemption(BigDecimal amount,
                                                   OriginalSaleRecord originalSale) {
        Objects.requireNonNull(originalSale, "originalSale");
        return pointRedemption(amount, originalSale.getRedemptionPoiTransactionId(),
                originalSale.getRedemptionPoiTransactionTimestamp(), originalSale.getMemberId());
    }

    /** Refund a prior rebate/coupon redemption by original POI transaction reference. */
    public static RefundAllocation rebate(BigDecimal amount, String originalPoiTransactionId,
                                          Instant originalPoiTransactionTimestamp,
                                          String memberId) {
        return builder()
                .type(RefundAllocationType.REBATE)
                .amount(amount)
                .originalPoiTransactionId(originalPoiTransactionId)
                .originalPoiTransactionTimestamp(originalPoiTransactionTimestamp)
                .memberId(memberId)
                .build();
    }

    /** Refund a prior rebate/coupon redemption from a persisted original sale record. */
    public static RefundAllocation rebate(BigDecimal amount, OriginalSaleRecord originalSale) {
        Objects.requireNonNull(originalSale, "originalSale");
        return rebate(amount, originalSale.getRebatePoiTransactionId(),
                originalSale.getRebatePoiTransactionTimestamp(), originalSale.getMemberId());
    }

    /** Reverse a prior loyalty award by original POI transaction reference. */
    public static RefundAllocation award(String originalPoiTransactionId,
                                         Instant originalPoiTransactionTimestamp,
                                         String memberId) {
        return builder()
                .type(RefundAllocationType.AWARD)
                .amount(BigDecimal.ZERO)
                .originalPoiTransactionId(originalPoiTransactionId)
                .originalPoiTransactionTimestamp(originalPoiTransactionTimestamp)
                .memberId(memberId)
                .build();
    }

    /** Reverse a prior loyalty award from a persisted original sale record. */
    public static RefundAllocation award(OriginalSaleRecord originalSale) {
        Objects.requireNonNull(originalSale, "originalSale");
        return award(originalSale.getAwardPoiTransactionId(),
                originalSale.getAwardPoiTransactionTimestamp(), originalSale.getMemberId());
    }

    public static Builder builder() {
        return new Builder();
    }

    public RefundAllocationType getType() {
        return type;
    }

    /**
     * Monetary value this movement contributes to the returned merchandise
     * allocation. Award reversals are bookkeeping and use zero.
     */
    public BigDecimal getAmount() {
        return amount;
    }

    public String getOriginalPoiTransactionId() {
        return originalPoiTransactionId;
    }

    public Instant getOriginalPoiTransactionTimestamp() {
        return originalPoiTransactionTimestamp;
    }

    public StoredValueCard getStoredValueCard() {
        return storedValueCard;
    }

    public String getMemberId() {
        return memberId;
    }

    /** Whether this allocation counts toward the monetary value of returned items. */
    public boolean countsTowardRefundTotal() {
        return type != RefundAllocationType.AWARD;
    }

    private void validate() {
        if (amount.signum() < 0 || (countsTowardRefundTotal() && amount.signum() == 0)) {
            throw new IllegalArgumentException(type + " refund allocation amount must be positive");
        }
        if (type == RefundAllocationType.STORED_VALUE) {
            Objects.requireNonNull(originalPoiTransactionId, "originalPoiTransactionId");
        }
        if (type == RefundAllocationType.STORE_CREDIT) {
            Objects.requireNonNull(storedValueCard, "storedValueCard");
        }
        if (type == RefundAllocationType.POINT_REDEMPTION
                || type == RefundAllocationType.REBATE
                || type == RefundAllocationType.AWARD) {
            Objects.requireNonNull(originalPoiTransactionId, "originalPoiTransactionId");
            Objects.requireNonNull(memberId, "memberId");
        }
    }

    /** Builder for {@link RefundAllocation}. */
    public static final class Builder {

        private RefundAllocationType type;
        private BigDecimal amount;
        private String originalPoiTransactionId;
        private Instant originalPoiTransactionTimestamp;
        private StoredValueCard storedValueCard;
        private String memberId;

        private Builder() {
        }

        public Builder type(RefundAllocationType type) {
            this.type = type;
            return this;
        }

        public Builder amount(BigDecimal amount) {
            this.amount = amount;
            return this;
        }

        public Builder originalPoiTransactionId(String originalPoiTransactionId) {
            this.originalPoiTransactionId = originalPoiTransactionId;
            return this;
        }

        public Builder originalPoiTransactionTimestamp(Instant originalPoiTransactionTimestamp) {
            this.originalPoiTransactionTimestamp = originalPoiTransactionTimestamp;
            return this;
        }

        public Builder storedValueCard(StoredValueCard storedValueCard) {
            this.storedValueCard = storedValueCard;
            return this;
        }

        public Builder memberId(String memberId) {
            this.memberId = memberId;
            return this;
        }

        public RefundAllocation build() {
            return new RefundAllocation(this);
        }
    }
}
