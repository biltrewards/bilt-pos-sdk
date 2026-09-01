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

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * References from a completed sale that a later checkout session can use to
 * void the whole original transaction or allocate return settlement.
 */
public final class OriginalSaleRecord {

    private final String cardPoiTransactionId;
    private final Instant cardPoiTransactionTimestamp;
    private final String storedValuePoiTransactionId;
    private final Instant storedValuePoiTransactionTimestamp;
    private final List<StoredValueLoadRecord> storedValueLoads;
    private final String rebatePoiTransactionId;
    private final Instant rebatePoiTransactionTimestamp;
    private final String redemptionPoiTransactionId;
    private final Instant redemptionPoiTransactionTimestamp;
    private final String awardPoiTransactionId;
    private final Instant awardPoiTransactionTimestamp;
    private final String memberId;

    private OriginalSaleRecord(Builder builder) {
        this.cardPoiTransactionId = builder.cardPoiTransactionId;
        this.cardPoiTransactionTimestamp = builder.cardPoiTransactionTimestamp;
        this.storedValuePoiTransactionId = builder.storedValuePoiTransactionId;
        this.storedValuePoiTransactionTimestamp = builder.storedValuePoiTransactionTimestamp;
        this.storedValueLoads = Collections.unmodifiableList(
                new ArrayList<>(builder.storedValueLoads));
        this.rebatePoiTransactionId = builder.rebatePoiTransactionId;
        this.rebatePoiTransactionTimestamp = builder.rebatePoiTransactionTimestamp;
        this.redemptionPoiTransactionId = builder.redemptionPoiTransactionId;
        this.redemptionPoiTransactionTimestamp = builder.redemptionPoiTransactionTimestamp;
        this.awardPoiTransactionId = builder.awardPoiTransactionId;
        this.awardPoiTransactionTimestamp = builder.awardPoiTransactionTimestamp;
        this.memberId = builder.memberId;
    }

    public static Builder builder() {
        return new Builder();
    }

    /** Builds a record from a completed settlement result. */
    public static OriginalSaleRecord from(SettlementResult result, String memberId) {
        return builder()
                .cardPoiTransactionId(result.getPoiTransactionId())
                .cardPoiTransactionTimestamp(result.getPoiTransactionTimestamp())
                .storedValuePoiTransactionId(result.getStoredValuePoiTransactionId())
                .storedValuePoiTransactionTimestamp(
                        result.getStoredValuePoiTransactionTimestamp())
                .storedValueLoads(result.getStoredValueLoads())
                .rebatePoiTransactionId(result.getRebatePoiTransactionId())
                .rebatePoiTransactionTimestamp(result.getRebatePoiTransactionTimestamp())
                .redemptionPoiTransactionId(result.getRedemptionPoiTransactionId())
                .redemptionPoiTransactionTimestamp(
                        result.getRedemptionPoiTransactionTimestamp())
                .awardPoiTransactionId(result.getAwardPoiTransactionId())
                .awardPoiTransactionTimestamp(result.getAwardPoiTransactionTimestamp())
                .memberId(memberId)
                .build();
    }

    public String getCardPoiTransactionId() {
        return cardPoiTransactionId;
    }

    public Instant getCardPoiTransactionTimestamp() {
        return cardPoiTransactionTimestamp;
    }

    public String getStoredValuePoiTransactionId() {
        return storedValuePoiTransactionId;
    }

    public Instant getStoredValuePoiTransactionTimestamp() {
        return storedValuePoiTransactionTimestamp;
    }

    /** Stored value basket-line fulfillments that must be reversed by a whole-sale void. */
    public List<StoredValueLoadRecord> getStoredValueLoads() {
        return storedValueLoads;
    }

    public String getRebatePoiTransactionId() {
        return rebatePoiTransactionId;
    }

    public Instant getRebatePoiTransactionTimestamp() {
        return rebatePoiTransactionTimestamp;
    }

    public String getRedemptionPoiTransactionId() {
        return redemptionPoiTransactionId;
    }

    public Instant getRedemptionPoiTransactionTimestamp() {
        return redemptionPoiTransactionTimestamp;
    }

    public String getAwardPoiTransactionId() {
        return awardPoiTransactionId;
    }

    public Instant getAwardPoiTransactionTimestamp() {
        return awardPoiTransactionTimestamp;
    }

    public String getMemberId() {
        return memberId;
    }

    /** Whether the record contains at least one movement reference. */
    public boolean hasMovement() {
        return cardPoiTransactionId != null
                || storedValuePoiTransactionId != null
                || !storedValueLoads.isEmpty()
                || rebatePoiTransactionId != null
                || redemptionPoiTransactionId != null
                || awardPoiTransactionId != null;
    }

    /**
     * Whether this record and {@code other} reference any of the same POI
     * transactions. Movement type is deliberately ignored: a transaction ID
     * reused in different legs still identifies an overlapping sale target.
     *
     * @param other record to compare with this one
     * @return whether the records share at least one POI transaction ID
     * @throws NullPointerException if {@code other} is null
     */
    public boolean sharesMovementWith(OriginalSaleRecord other) {
        Objects.requireNonNull(other, "other");
        if (references(other.cardPoiTransactionId)
                || references(other.storedValuePoiTransactionId)
                || references(other.rebatePoiTransactionId)
                || references(other.redemptionPoiTransactionId)
                || references(other.awardPoiTransactionId)) {
            return true;
        }
        for (StoredValueLoadRecord load : other.storedValueLoads) {
            if (references(load.getPoiTransactionId())) {
                return true;
            }
        }
        return false;
    }

    private boolean references(String poiTransactionId) {
        if (poiTransactionId == null) {
            return false;
        }
        if (poiTransactionId.equals(cardPoiTransactionId)
                || poiTransactionId.equals(storedValuePoiTransactionId)
                || poiTransactionId.equals(rebatePoiTransactionId)
                || poiTransactionId.equals(redemptionPoiTransactionId)
                || poiTransactionId.equals(awardPoiTransactionId)) {
            return true;
        }
        for (StoredValueLoadRecord load : storedValueLoads) {
            if (poiTransactionId.equals(load.getPoiTransactionId())) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof OriginalSaleRecord)) {
            return false;
        }
        OriginalSaleRecord that = (OriginalSaleRecord) other;
        return Objects.equals(cardPoiTransactionId, that.cardPoiTransactionId)
                && Objects.equals(cardPoiTransactionTimestamp, that.cardPoiTransactionTimestamp)
                && Objects.equals(storedValuePoiTransactionId,
                        that.storedValuePoiTransactionId)
                && Objects.equals(storedValuePoiTransactionTimestamp,
                        that.storedValuePoiTransactionTimestamp)
                && Objects.equals(storedValueLoads, that.storedValueLoads)
                && Objects.equals(rebatePoiTransactionId, that.rebatePoiTransactionId)
                && Objects.equals(rebatePoiTransactionTimestamp,
                        that.rebatePoiTransactionTimestamp)
                && Objects.equals(redemptionPoiTransactionId,
                        that.redemptionPoiTransactionId)
                && Objects.equals(redemptionPoiTransactionTimestamp,
                        that.redemptionPoiTransactionTimestamp)
                && Objects.equals(awardPoiTransactionId, that.awardPoiTransactionId)
                && Objects.equals(awardPoiTransactionTimestamp,
                        that.awardPoiTransactionTimestamp)
                && Objects.equals(memberId, that.memberId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(cardPoiTransactionId, cardPoiTransactionTimestamp,
                storedValuePoiTransactionId, storedValuePoiTransactionTimestamp,
                storedValueLoads,
                rebatePoiTransactionId, rebatePoiTransactionTimestamp,
                redemptionPoiTransactionId, redemptionPoiTransactionTimestamp,
                awardPoiTransactionId, awardPoiTransactionTimestamp, memberId);
    }

    /** Builder for {@link OriginalSaleRecord}. */
    public static final class Builder {

        private String cardPoiTransactionId;
        private Instant cardPoiTransactionTimestamp;
        private String storedValuePoiTransactionId;
        private Instant storedValuePoiTransactionTimestamp;
        private List<StoredValueLoadRecord> storedValueLoads = new ArrayList<>();
        private String rebatePoiTransactionId;
        private Instant rebatePoiTransactionTimestamp;
        private String redemptionPoiTransactionId;
        private Instant redemptionPoiTransactionTimestamp;
        private String awardPoiTransactionId;
        private Instant awardPoiTransactionTimestamp;
        private String memberId;

        private Builder() {
        }

        public Builder cardPoiTransactionId(String cardPoiTransactionId) {
            this.cardPoiTransactionId = cardPoiTransactionId;
            return this;
        }

        public Builder cardPoiTransactionTimestamp(Instant cardPoiTransactionTimestamp) {
            this.cardPoiTransactionTimestamp = cardPoiTransactionTimestamp;
            return this;
        }

        public Builder storedValuePoiTransactionId(String storedValuePoiTransactionId) {
            this.storedValuePoiTransactionId = storedValuePoiTransactionId;
            return this;
        }

        public Builder storedValuePoiTransactionTimestamp(
                Instant storedValuePoiTransactionTimestamp) {
            this.storedValuePoiTransactionTimestamp = storedValuePoiTransactionTimestamp;
            return this;
        }

        public Builder storedValueLoads(List<StoredValueLoadRecord> storedValueLoads) {
            this.storedValueLoads = storedValueLoads == null
                    ? new ArrayList<>() : new ArrayList<>(storedValueLoads);
            return this;
        }

        public Builder addStoredValueLoad(StoredValueLoadRecord storedValueLoad) {
            this.storedValueLoads.add(Objects.requireNonNull(storedValueLoad,
                    "storedValueLoad"));
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

        public Builder redemptionPoiTransactionTimestamp(
                Instant redemptionPoiTransactionTimestamp) {
            this.redemptionPoiTransactionTimestamp = redemptionPoiTransactionTimestamp;
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

        public Builder memberId(String memberId) {
            this.memberId = memberId;
            return this;
        }

        public OriginalSaleRecord build() {
            return new OriginalSaleRecord(this);
        }
    }
}
