/*
 *    ____  _ _ _
 *   | __ )(_) | |_
 *   |  _ \| | | __|
 *   | |_) | | | |_
 *   |____/|_|_|\__|
 *
 *   Bilt POS SDK
 *
 *   This file is auto-generated from the Nexo Sale to POI v3.0 JSON Schema.
 *   Do not modify manually — re-run code generation instead.
 */
package com.bilt.pos.nexo.model;

import com.fasterxml.jackson.annotation.*;

/**
 * Surrogate of the PAN used to identify a customer's payment mean without exposing the PAN.
 */
public class PaymentToken {
    private String expiryDateTime;
    private TokenRequestedTypeEnum tokenRequestedType;
    private String tokenValue;

    /**
     * Date and time after which the token is no longer valid.
     */
    @JsonProperty("ExpiryDateTime")
    public String getExpiryDateTime() { return expiryDateTime; }
    @JsonProperty("ExpiryDateTime")
    public void setExpiryDateTime(String value) { this.expiryDateTime = value; }

    @JsonProperty("TokenRequestedType")
    public TokenRequestedTypeEnum getTokenRequestedType() { return tokenRequestedType; }
    @JsonProperty("TokenRequestedType")
    public void setTokenRequestedType(TokenRequestedTypeEnum value) { this.tokenRequestedType = value; }

    /**
     * Value of the payment token replacing the PAN.
     */
    @JsonProperty("TokenValue")
    public String getTokenValue() { return tokenValue; }
    @JsonProperty("TokenValue")
    public void setTokenValue(String value) { this.tokenValue = value; }
    
    public static Builder builder() { return new Builder(); }
    
    public static final class Builder {
        private String expiryDateTime;
        private TokenRequestedTypeEnum tokenRequestedType;
        private String tokenValue;
        
        private Builder() {}
        
        public Builder expiryDateTime(String expiryDateTime) {
            this.expiryDateTime = expiryDateTime;
            return this;
        }
        
        public Builder tokenRequestedType(TokenRequestedTypeEnum tokenRequestedType) {
            this.tokenRequestedType = tokenRequestedType;
            return this;
        }
        
        public Builder tokenValue(String tokenValue) {
            this.tokenValue = tokenValue;
            return this;
        }
        
        public PaymentToken build() {
            PaymentToken result = new PaymentToken();
            result.setExpiryDateTime(this.expiryDateTime);
            result.setTokenRequestedType(this.tokenRequestedType);
            result.setTokenValue(this.tokenValue);
            return result;
        }
    }
}
