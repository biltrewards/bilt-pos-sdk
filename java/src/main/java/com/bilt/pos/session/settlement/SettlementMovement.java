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

import java.math.BigDecimal;
import java.time.Instant;

/** One money or loyalty movement committed as part of a settlement. */
public final class SettlementMovement {

    private final SettlementStep step;
    private final BigDecimal amount;
    private final String saleTransactionId;
    private final String poiTransactionId;
    private final Instant poiTransactionTimestamp;
    private final String memberId;
    private final Integer points;
    private final Integer pointBalance;

    private SettlementMovement(Builder builder) {
        this.step = builder.step;
        this.amount = builder.amount;
        this.saleTransactionId = builder.saleTransactionId;
        this.poiTransactionId = builder.poiTransactionId;
        this.poiTransactionTimestamp = builder.poiTransactionTimestamp;
        this.memberId = builder.memberId;
        this.points = builder.points;
        this.pointBalance = builder.pointBalance;
    }

    public static Builder builder() {
        return new Builder();
    }

    public SettlementStep getStep() {
        return step;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getSaleTransactionId() {
        return saleTransactionId;
    }

    public String getPoiTransactionId() {
        return poiTransactionId;
    }

    public Instant getPoiTransactionTimestamp() {
        return poiTransactionTimestamp;
    }

    public String getMemberId() {
        return memberId;
    }

    public Integer getPoints() {
        return points;
    }

    public Integer getPointBalance() {
        return pointBalance;
    }

    /** Builder for {@link SettlementMovement}. */
    public static final class Builder {

        private SettlementStep step;
        private BigDecimal amount = BigDecimal.ZERO;
        private String saleTransactionId;
        private String poiTransactionId;
        private Instant poiTransactionTimestamp;
        private String memberId;
        private Integer points;
        private Integer pointBalance;

        private Builder() {
        }

        public Builder step(SettlementStep step) {
            this.step = step;
            return this;
        }

        public Builder amount(BigDecimal amount) {
            this.amount = amount;
            return this;
        }

        public Builder saleTransactionId(String saleTransactionId) {
            this.saleTransactionId = saleTransactionId;
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

        public Builder memberId(String memberId) {
            this.memberId = memberId;
            return this;
        }

        public Builder points(Integer points) {
            this.points = points;
            return this;
        }

        public Builder pointBalance(Integer pointBalance) {
            this.pointBalance = pointBalance;
            return this;
        }

        public SettlementMovement build() {
            return new SettlementMovement(this);
        }
    }
}
