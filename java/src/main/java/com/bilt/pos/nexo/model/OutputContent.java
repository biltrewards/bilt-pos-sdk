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
 * Content to display or print, in one of the supported formats: predefined message
 * reference, formatted text, XHTML, or barcode.
 */
public class OutputContent {
    private OutputBarcode outputBarcode;
    private OutputFormatEnum outputFormat;
    private OutputText[] outputText;
    private String outputXHTML;
    private PredefinedContent predefinedContent;

    @JsonProperty("OutputBarcode")
    public OutputBarcode getOutputBarcode() { return outputBarcode; }
    @JsonProperty("OutputBarcode")
    public void setOutputBarcode(OutputBarcode value) { this.outputBarcode = value; }

    @JsonProperty("OutputFormat")
    public OutputFormatEnum getOutputFormat() { return outputFormat; }
    @JsonProperty("OutputFormat")
    public void setOutputFormat(OutputFormatEnum value) { this.outputFormat = value; }

    @JsonProperty("OutputText")
    public OutputText[] getOutputText() { return outputText; }
    @JsonProperty("OutputText")
    public void setOutputText(OutputText[] value) { this.outputText = value; }

    /**
     * Base64-encoded XHTML document body containing the message to display or print.
     */
    @JsonProperty("OutputXHTML")
    public String getOutputXHTML() { return outputXHTML; }
    @JsonProperty("OutputXHTML")
    public void setOutputXHTML(String value) { this.outputXHTML = value; }

    @JsonProperty("PredefinedContent")
    public PredefinedContent getPredefinedContent() { return predefinedContent; }
    @JsonProperty("PredefinedContent")
    public void setPredefinedContent(PredefinedContent value) { this.predefinedContent = value; }
    
    public static Builder builder() { return new Builder(); }
    
    public static final class Builder {
        private OutputBarcode outputBarcode;
        private OutputFormatEnum outputFormat;
        private OutputText[] outputText;
        private String outputXHTML;
        private PredefinedContent predefinedContent;
        
        private Builder() {}
        
        public Builder outputBarcode(OutputBarcode outputBarcode) {
            this.outputBarcode = outputBarcode;
            return this;
        }
        
        public Builder outputFormat(OutputFormatEnum outputFormat) {
            this.outputFormat = outputFormat;
            return this;
        }
        
        public Builder outputText(OutputText[] outputText) {
            this.outputText = outputText;
            return this;
        }
        
        public Builder outputXHTML(String outputXHTML) {
            this.outputXHTML = outputXHTML;
            return this;
        }
        
        public Builder predefinedContent(PredefinedContent predefinedContent) {
            this.predefinedContent = predefinedContent;
            return this;
        }
        
        public OutputContent build() {
            OutputContent result = new OutputContent();
            result.setOutputBarcode(this.outputBarcode);
            result.setOutputFormat(this.outputFormat);
            result.setOutputText(this.outputText);
            result.setOutputXHTML(this.outputXHTML);
            result.setPredefinedContent(this.predefinedContent);
            return result;
        }
    }
}
