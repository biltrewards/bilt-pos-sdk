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
 * Number of coins or bills of a specific denomination remaining in a cash handling device.
 */
public class CoinsOrBills {
    private long number;
    private double unitValue;

    /**
     * Number of coins or bills of this denomination. Value 0 means the denomination is depleted.
     */
    @JsonProperty("Number")
    public long getNumber() { return number; }
    @JsonProperty("Number")
    public void setNumber(long value) { this.number = value; }

    /**
     * Denomination value of the coins or bills.
     */
    @JsonProperty("UnitValue")
    public double getUnitValue() { return unitValue; }
    @JsonProperty("UnitValue")
    public void setUnitValue(double value) { this.unitValue = value; }
    
    public static Builder builder() { return new Builder(); }
    
    public static final class Builder {
        private long number;
        private double unitValue;
        
        private Builder() {}
        
        public Builder number(long number) {
            this.number = number;
            return this;
        }
        
        public Builder unitValue(double unitValue) {
            this.unitValue = unitValue;
            return this;
        }
        
        public CoinsOrBills build() {
            CoinsOrBills result = new CoinsOrBills();
            result.setNumber(this.number);
            result.setUnitValue(this.unitValue);
            return result;
        }
    }
}
