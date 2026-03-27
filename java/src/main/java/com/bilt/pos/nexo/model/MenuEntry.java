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
 * A single entry in a menu presented to the user during a GetMenuEntry input command.
 */
public class MenuEntry {
    private Boolean defaultSelectedFlag;
    private MenuEntryTagEnum menuEntryTag;
    private OutputFormatEnum outputFormat;
    private OutputText[] outputText;
    private String outputXHTML;
    private PredefinedContent predefinedContent;

    /**
     * When true, this entry is pre-selected before any user action. Default false.
     */
    @JsonProperty("DefaultSelectedFlag")
    public Boolean getDefaultSelectedFlag() { return defaultSelectedFlag; }
    @JsonProperty("DefaultSelectedFlag")
    public void setDefaultSelectedFlag(Boolean value) { this.defaultSelectedFlag = value; }

    /**
     * Characteristics of this menu entry (selectable, non-selectable, sub-menu). Default
     * Selectable.
     */
    @JsonProperty("MenuEntryTag")
    public MenuEntryTagEnum getMenuEntryTag() { return menuEntryTag; }
    @JsonProperty("MenuEntryTag")
    public void setMenuEntryTag(MenuEntryTagEnum value) { this.menuEntryTag = value; }

    @JsonProperty("OutputFormat")
    public OutputFormatEnum getOutputFormat() { return outputFormat; }
    @JsonProperty("OutputFormat")
    public void setOutputFormat(OutputFormatEnum value) { this.outputFormat = value; }

    @JsonProperty("OutputText")
    public OutputText[] getOutputText() { return outputText; }
    @JsonProperty("OutputText")
    public void setOutputText(OutputText[] value) { this.outputText = value; }

    /**
     * Base64-encoded XHTML content for this menu entry.
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
        private Boolean defaultSelectedFlag;
        private MenuEntryTagEnum menuEntryTag;
        private OutputFormatEnum outputFormat;
        private OutputText[] outputText;
        private String outputXHTML;
        private PredefinedContent predefinedContent;
        
        private Builder() {}
        
        public Builder defaultSelectedFlag(Boolean defaultSelectedFlag) {
            this.defaultSelectedFlag = defaultSelectedFlag;
            return this;
        }
        
        public Builder menuEntryTag(MenuEntryTagEnum menuEntryTag) {
            this.menuEntryTag = menuEntryTag;
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
        
        public MenuEntry build() {
            MenuEntry result = new MenuEntry();
            result.setDefaultSelectedFlag(this.defaultSelectedFlag);
            result.setMenuEntryTag(this.menuEntryTag);
            result.setOutputFormat(this.outputFormat);
            result.setOutputText(this.outputText);
            result.setOutputXHTML(this.outputXHTML);
            result.setPredefinedContent(this.predefinedContent);
            return result;
        }
    }
}
