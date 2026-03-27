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
 * Content of a single APDU command to exchange with an initialised smart card per ISO 7816.
 */
public class CardReaderAPDURequest {
    private String apduClass;
    private String apduData;
    private String apduExpectedLength;
    private String apduInstruction;
    private String apduPar1;
    private String apduPar2;

    /**
     * Class field (CLA) of the APDU command per ISO 7816-4. Base64-encoded 1 byte.
     */
    @JsonProperty("APDUClass")
    public String getApduClass() { return apduClass; }
    @JsonProperty("APDUClass")
    public void setApduClass(String value) { this.apduClass = value; }

    /**
     * Data field (Lc + Data) of the APDU command. Mandatory when the instruction requires data.
     */
    @JsonProperty("APDUData")
    public String getApduData() { return apduData; }
    @JsonProperty("APDUData")
    public void setApduData(String value) { this.apduData = value; }

    /**
     * Expected length (Le) of the data in the APDU response. Base64-encoded 1 byte. Absent
     * means maximum available bytes are requested.
     */
    @JsonProperty("APDUExpectedLength")
    public String getApduExpectedLength() { return apduExpectedLength; }
    @JsonProperty("APDUExpectedLength")
    public void setApduExpectedLength(String value) { this.apduExpectedLength = value; }

    /**
     * Instruction field (INS) of the APDU command per ISO 7816-4. Base64-encoded 1 byte.
     */
    @JsonProperty("APDUInstruction")
    public String getApduInstruction() { return apduInstruction; }
    @JsonProperty("APDUInstruction")
    public void setApduInstruction(String value) { this.apduInstruction = value; }

    /**
     * Parameter 1 field (P1) of the APDU command per ISO 7816-4. Base64-encoded 1 byte.
     */
    @JsonProperty("APDUPar1")
    public String getApduPar1() { return apduPar1; }
    @JsonProperty("APDUPar1")
    public void setApduPar1(String value) { this.apduPar1 = value; }

    /**
     * Parameter 2 field (P2) of the APDU command per ISO 7816-4. Base64-encoded 1 byte.
     */
    @JsonProperty("APDUPar2")
    public String getApduPar2() { return apduPar2; }
    @JsonProperty("APDUPar2")
    public void setApduPar2(String value) { this.apduPar2 = value; }
    
    public static Builder builder() { return new Builder(); }
    
    public static final class Builder {
        private String apduClass;
        private String apduData;
        private String apduExpectedLength;
        private String apduInstruction;
        private String apduPar1;
        private String apduPar2;
        
        private Builder() {}
        
        public Builder apduClass(String apduClass) {
            this.apduClass = apduClass;
            return this;
        }
        
        public Builder apduData(String apduData) {
            this.apduData = apduData;
            return this;
        }
        
        public Builder apduExpectedLength(String apduExpectedLength) {
            this.apduExpectedLength = apduExpectedLength;
            return this;
        }
        
        public Builder apduInstruction(String apduInstruction) {
            this.apduInstruction = apduInstruction;
            return this;
        }
        
        public Builder apduPar1(String apduPar1) {
            this.apduPar1 = apduPar1;
            return this;
        }
        
        public Builder apduPar2(String apduPar2) {
            this.apduPar2 = apduPar2;
            return this;
        }
        
        public CardReaderAPDURequest build() {
            CardReaderAPDURequest result = new CardReaderAPDURequest();
            result.setApduClass(this.apduClass);
            result.setApduData(this.apduData);
            result.setApduExpectedLength(this.apduExpectedLength);
            result.setApduInstruction(this.apduInstruction);
            result.setApduPar1(this.apduPar1);
            result.setApduPar2(this.apduPar2);
            return result;
        }
    }
}
