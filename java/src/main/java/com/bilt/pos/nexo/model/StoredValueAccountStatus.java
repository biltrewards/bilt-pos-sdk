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
 * Result of a stored value card operation, including account identification and current
 * balance. StoredValueAccountID is [0..1] per Nexo spec section 4.3.5.3 — a successful
 * Reverse typically omits it.
 */
public class StoredValueAccountStatus {
    private Double currentBalance;
    private StoredValueAccountID storedValueAccountID;

    /**
     * Current balance of the stored value account after the operation, when known.
     */
    @JsonProperty("CurrentBalance")
    public Double getCurrentBalance() { return currentBalance; }
    @JsonProperty("CurrentBalance")
    public void setCurrentBalance(Double value) { this.currentBalance = value; }

    @JsonProperty("StoredValueAccountID")
    public StoredValueAccountID getStoredValueAccountID() { return storedValueAccountID; }
    @JsonProperty("StoredValueAccountID")
    public void setStoredValueAccountID(StoredValueAccountID value) { this.storedValueAccountID = value; }
    
    public static Builder builder() { return new Builder(); }
    
    public static final class Builder {
        private Double currentBalance;
        private StoredValueAccountID storedValueAccountID;
        
        private Builder() {}
        
        public Builder currentBalance(Double currentBalance) {
            this.currentBalance = currentBalance;
            return this;
        }
        
        public Builder storedValueAccountID(StoredValueAccountID storedValueAccountID) {
            this.storedValueAccountID = storedValueAccountID;
            return this;
        }
        
        public StoredValueAccountStatus build() {
            StoredValueAccountStatus result = new StoredValueAccountStatus();
            result.setCurrentBalance(this.currentBalance);
            result.setStoredValueAccountID(this.storedValueAccountID);
            return result;
        }
    }
}
