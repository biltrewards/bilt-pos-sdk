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
 * Data entered by the user in response to the input command.
 */
public class Input {
    private Boolean confirmedFlag;
    private String digitInput;
    private String functionKey;
    private InputCommandEnum inputCommand;
    private long[] menuEntryNumber;
    private ContentInformationType password;
    private String textInput;

    /**
     * User's yes/no response to GetConfirmation or SiteManager. Mandatory for those commands.
     */
    @JsonProperty("ConfirmedFlag")
    public Boolean getConfirmedFlag() { return confirmedFlag; }
    @JsonProperty("ConfirmedFlag")
    public void setConfirmedFlag(Boolean value) { this.confirmedFlag = value; }

    /**
     * Digit string entered by the user. Mandatory for DigitString.
     */
    @JsonProperty("DigitInput")
    public String getDigitInput() { return digitInput; }
    @JsonProperty("DigitInput")
    public void setDigitInput(String value) { this.digitInput = value; }

    /**
     * Number of the function key pressed. Mandatory for GetFunctionKey.
     */
    @JsonProperty("FunctionKey")
    public String getFunctionKey() { return functionKey; }
    @JsonProperty("FunctionKey")
    public void setFunctionKey(String value) { this.functionKey = value; }

    @JsonProperty("InputCommand")
    public InputCommandEnum getInputCommand() { return inputCommand; }
    @JsonProperty("InputCommand")
    public void setInputCommand(InputCommandEnum value) { this.inputCommand = value; }

    /**
     * Index(es) of selected menu entries (1-based). Value -1 means Back, 0 means Home.
     * Mandatory for GetMenuEntry.
     */
    @JsonProperty("MenuEntryNumber")
    public long[] getMenuEntryNumber() { return menuEntryNumber; }
    @JsonProperty("MenuEntryNumber")
    public void setMenuEntryNumber(long[] value) { this.menuEntryNumber = value; }

    /**
     * CMS-protected password. Mandatory for Password command when encryption is used.
     */
    @JsonProperty("Password")
    public ContentInformationType getPassword() { return password; }
    @JsonProperty("Password")
    public void setPassword(ContentInformationType value) { this.password = value; }

    /**
     * Alphanumeric string entered by the user. Mandatory for TextString and DecimalString, or
     * for plaintext Password.
     */
    @JsonProperty("TextInput")
    public String getTextInput() { return textInput; }
    @JsonProperty("TextInput")
    public void setTextInput(String value) { this.textInput = value; }
    
    public static Builder builder() { return new Builder(); }
    
    public static final class Builder {
        private Boolean confirmedFlag;
        private String digitInput;
        private String functionKey;
        private InputCommandEnum inputCommand;
        private long[] menuEntryNumber;
        private ContentInformationType password;
        private String textInput;
        
        private Builder() {}
        
        public Builder confirmedFlag(Boolean confirmedFlag) {
            this.confirmedFlag = confirmedFlag;
            return this;
        }
        
        public Builder digitInput(String digitInput) {
            this.digitInput = digitInput;
            return this;
        }
        
        public Builder functionKey(String functionKey) {
            this.functionKey = functionKey;
            return this;
        }
        
        public Builder inputCommand(InputCommandEnum inputCommand) {
            this.inputCommand = inputCommand;
            return this;
        }
        
        public Builder menuEntryNumber(long[] menuEntryNumber) {
            this.menuEntryNumber = menuEntryNumber;
            return this;
        }
        
        public Builder password(ContentInformationType password) {
            this.password = password;
            return this;
        }
        
        public Builder textInput(String textInput) {
            this.textInput = textInput;
            return this;
        }
        
        public Input build() {
            Input result = new Input();
            result.setConfirmedFlag(this.confirmedFlag);
            result.setDigitInput(this.digitInput);
            result.setFunctionKey(this.functionKey);
            result.setInputCommand(this.inputCommand);
            result.setMenuEntryNumber(this.menuEntryNumber);
            result.setPassword(this.password);
            result.setTextInput(this.textInput);
            return result;
        }
    }
}
