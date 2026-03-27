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
 * Barcode content to display or print.
 */
public class OutputBarcode {
    private BarcodeTypeEnum barcodeType;
    private String barcodeValue;

    @JsonProperty("BarcodeType")
    public BarcodeTypeEnum getBarcodeType() { return barcodeType; }
    @JsonProperty("BarcodeType")
    public void setBarcodeType(BarcodeTypeEnum value) { this.barcodeType = value; }

    /**
     * Value to encode in the barcode.
     */
    @JsonProperty("BarcodeValue")
    public String getBarcodeValue() { return barcodeValue; }
    @JsonProperty("BarcodeValue")
    public void setBarcodeValue(String value) { this.barcodeValue = value; }
    
    public static Builder builder() { return new Builder(); }
    
    public static final class Builder {
        private BarcodeTypeEnum barcodeType;
        private String barcodeValue;
        
        private Builder() {}
        
        public Builder barcodeType(BarcodeTypeEnum barcodeType) {
            this.barcodeType = barcodeType;
            return this;
        }
        
        public Builder barcodeValue(String barcodeValue) {
            this.barcodeValue = barcodeValue;
            return this;
        }
        
        public OutputBarcode build() {
            OutputBarcode result = new OutputBarcode();
            result.setBarcodeType(this.barcodeType);
            result.setBarcodeValue(this.barcodeValue);
            return result;
        }
    }
}
