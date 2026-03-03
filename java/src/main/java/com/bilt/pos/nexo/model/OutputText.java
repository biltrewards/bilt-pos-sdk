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
 * Content of a formatted text message to display or print, including layout and style
 * parameters.
 */
public class OutputText {
    private AlignmentEnum alignment;
    private CharacterHeightEnum characterHeight;
    private Long characterSet;
    private CharacterStyleEnum characterStyle;
    private CharacterWidthEnum characterWidth;
    private ColorEnum color;
    private Boolean endOfLineFlag;
    private String font;
    private Long startColumn;
    private Long startRow;
    private String text;

    @JsonProperty("Alignment")
    public AlignmentEnum getAlignment() { return alignment; }
    @JsonProperty("Alignment")
    public void setAlignment(AlignmentEnum value) { this.alignment = value; }

    @JsonProperty("CharacterHeight")
    public CharacterHeightEnum getCharacterHeight() { return characterHeight; }
    @JsonProperty("CharacterHeight")
    public void setCharacterHeight(CharacterHeightEnum value) { this.characterHeight = value; }

    /**
     * IANA character encoding number for the text (used for ASN.1 encoding; for XML the
     * document encoding is used).
     */
    @JsonProperty("CharacterSet")
    public Long getCharacterSet() { return characterSet; }
    @JsonProperty("CharacterSet")
    public void setCharacterSet(Long value) { this.characterSet = value; }

    @JsonProperty("CharacterStyle")
    public CharacterStyleEnum getCharacterStyle() { return characterStyle; }
    @JsonProperty("CharacterStyle")
    public void setCharacterStyle(CharacterStyleEnum value) { this.characterStyle = value; }

    @JsonProperty("CharacterWidth")
    public CharacterWidthEnum getCharacterWidth() { return characterWidth; }
    @JsonProperty("CharacterWidth")
    public void setCharacterWidth(CharacterWidthEnum value) { this.characterWidth = value; }

    @JsonProperty("Color")
    public ColorEnum getColor() { return color; }
    @JsonProperty("Color")
    public void setColor(ColorEnum value) { this.color = value; }

    /**
     * When true, a newline and carriage return are appended after the text. Default true.
     */
    @JsonProperty("EndOfLineFlag")
    public Boolean getEndOfLineFlag() { return endOfLineFlag; }
    @JsonProperty("EndOfLineFlag")
    public void setEndOfLineFlag(Boolean value) { this.endOfLineFlag = value; }

    /**
     * Name of the font to use, as agreed between POI and Sale Systems.
     */
    @JsonProperty("Font")
    public String getFont() { return font; }
    @JsonProperty("Font")
    public void setFont(String value) { this.font = value; }

    /**
     * Column position from which the text string is displayed or printed (1-based).
     */
    @JsonProperty("StartColumn")
    public Long getStartColumn() { return startColumn; }
    @JsonProperty("StartColumn")
    public void setStartColumn(Long value) { this.startColumn = value; }

    /**
     * Row position from which the text string is displayed or printed (1-based).
     */
    @JsonProperty("StartRow")
    public Long getStartRow() { return startRow; }
    @JsonProperty("StartRow")
    public void setStartRow(Long value) { this.startRow = value; }

    /**
     * Text content to display or print.
     */
    @JsonProperty("Text")
    public String getText() { return text; }
    @JsonProperty("Text")
    public void setText(String value) { this.text = value; }
    
    public static Builder builder() { return new Builder(); }
    
    public static final class Builder {
        private AlignmentEnum alignment;
        private CharacterHeightEnum characterHeight;
        private Long characterSet;
        private CharacterStyleEnum characterStyle;
        private CharacterWidthEnum characterWidth;
        private ColorEnum color;
        private Boolean endOfLineFlag;
        private String font;
        private Long startColumn;
        private Long startRow;
        private String text;
        
        private Builder() {}
        
        public Builder alignment(AlignmentEnum alignment) {
            this.alignment = alignment;
            return this;
        }
        
        public Builder characterHeight(CharacterHeightEnum characterHeight) {
            this.characterHeight = characterHeight;
            return this;
        }
        
        public Builder characterSet(Long characterSet) {
            this.characterSet = characterSet;
            return this;
        }
        
        public Builder characterStyle(CharacterStyleEnum characterStyle) {
            this.characterStyle = characterStyle;
            return this;
        }
        
        public Builder characterWidth(CharacterWidthEnum characterWidth) {
            this.characterWidth = characterWidth;
            return this;
        }
        
        public Builder color(ColorEnum color) {
            this.color = color;
            return this;
        }
        
        public Builder endOfLineFlag(Boolean endOfLineFlag) {
            this.endOfLineFlag = endOfLineFlag;
            return this;
        }
        
        public Builder font(String font) {
            this.font = font;
            return this;
        }
        
        public Builder startColumn(Long startColumn) {
            this.startColumn = startColumn;
            return this;
        }
        
        public Builder startRow(Long startRow) {
            this.startRow = startRow;
            return this;
        }
        
        public Builder text(String text) {
            this.text = text;
            return this;
        }
        
        public OutputText build() {
            OutputText result = new OutputText();
            result.setAlignment(this.alignment);
            result.setCharacterHeight(this.characterHeight);
            result.setCharacterSet(this.characterSet);
            result.setCharacterStyle(this.characterStyle);
            result.setCharacterWidth(this.characterWidth);
            result.setColor(this.color);
            result.setEndOfLineFlag(this.endOfLineFlag);
            result.setFont(this.font);
            result.setStartColumn(this.startColumn);
            result.setStartRow(this.startRow);
            result.setText(this.text);
            return result;
        }
    }
}
