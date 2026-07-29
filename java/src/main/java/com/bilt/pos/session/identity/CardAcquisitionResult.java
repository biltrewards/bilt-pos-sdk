/*
 *    ____  _ _ _
 *   | __ )(_) | |_
 *   |  _ \| | | __|
 *   | |_) | | | |_
 *   |____/|_|_|\__|
 *
 *   Bilt POS SDK
 */
package com.bilt.pos.session.identity;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Card data read by {@code acquireCard()} without initiating a payment.
 */
public final class CardAcquisitionResult {

    private final String maskedPan;
    private final String rawPan;
    private final String truncatedPan;
    private final String paymentBrand;
    private final EntryMode entryMode;
    private final String cardToken;
    private final String expiryDate;
    private final Map<String, String> additionalData;

    private CardAcquisitionResult(Builder builder) {
        this.maskedPan = builder.maskedPan;
        this.rawPan = builder.rawPan;
        this.truncatedPan = builder.truncatedPan;
        this.paymentBrand = builder.paymentBrand;
        this.entryMode = builder.entryMode;
        this.cardToken = builder.cardToken;
        this.expiryDate = builder.expiryDate;
        this.additionalData = builder.additionalData == null
                ? Collections.emptyMap()
                : Collections.unmodifiableMap(new LinkedHashMap<>(builder.additionalData));
    }

    public static Builder builder() {
        return new Builder();
    }

    /** e.g. {@code "****1234"}. */
    public String getMaskedPan() {
        return maskedPan;
    }

    /** Full PAN; only returned for PLCC cards, otherwise {@code null}. */
    public String getRawPan() {
        return rawPan;
    }

    public String getTruncatedPan() {
        return truncatedPan;
    }

    /** e.g. {@code "Visa"}, {@code "Mastercard"}. */
    public String getPaymentBrand() {
        return paymentBrand;
    }

    /** How the card was read. */
    public EntryMode getEntryMode() {
        return entryMode;
    }

    /** Tokenized card reference, if available. */
    public String getCardToken() {
        return cardToken;
    }

    /** {@code "MMYY"}, or {@code null}. */
    public String getExpiryDate() {
        return expiryDate;
    }

    /** Raw {@code AdditionalResponse} fields. Never {@code null}. */
    public Map<String, String> getAdditionalData() {
        return additionalData;
    }

    /** Builder for {@link CardAcquisitionResult}. Intended for SDK use. */
    public static final class Builder {

        private String maskedPan;
        private String rawPan;
        private String truncatedPan;
        private String paymentBrand;
        private EntryMode entryMode;
        private String cardToken;
        private String expiryDate;
        private Map<String, String> additionalData;

        private Builder() {
        }

        public Builder maskedPan(String maskedPan) {
            this.maskedPan = maskedPan;
            return this;
        }

        public Builder rawPan(String rawPan) {
            this.rawPan = rawPan;
            return this;
        }

        public Builder truncatedPan(String truncatedPan) {
            this.truncatedPan = truncatedPan;
            return this;
        }

        public Builder paymentBrand(String paymentBrand) {
            this.paymentBrand = paymentBrand;
            return this;
        }

        public Builder entryMode(EntryMode entryMode) {
            this.entryMode = entryMode;
            return this;
        }

        public Builder cardToken(String cardToken) {
            this.cardToken = cardToken;
            return this;
        }

        public Builder expiryDate(String expiryDate) {
            this.expiryDate = expiryDate;
            return this;
        }

        public Builder additionalData(Map<String, String> additionalData) {
            this.additionalData = additionalData;
            return this;
        }

        public CardAcquisitionResult build() {
            return new CardAcquisitionResult(this);
        }
    }
}
