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
 * Content of the Print Request message, used to print a document on a printer managed by
 * the receiving system.
 */
public class PrintRequest {
    private PrintOutput printOutput;

    @JsonProperty("PrintOutput")
    public PrintOutput getPrintOutput() { return printOutput; }
    @JsonProperty("PrintOutput")
    public void setPrintOutput(PrintOutput value) { this.printOutput = value; }
    
    public static Builder builder() { return new Builder(); }
    
    public static final class Builder {
        private PrintOutput printOutput;
        
        private Builder() {}
        
        public Builder printOutput(PrintOutput printOutput) {
            this.printOutput = printOutput;
            return this;
        }
        
        public PrintRequest build() {
            PrintRequest result = new PrintRequest();
            result.setPrintOutput(this.printOutput);
            return result;
        }
    }
}
