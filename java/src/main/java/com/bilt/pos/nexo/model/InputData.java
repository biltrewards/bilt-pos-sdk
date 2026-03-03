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
 * Parameters for an input command, defining the target device, type of input, and
 * constraints.
 */
public class InputData {
    private Boolean beepKeyFlag;
    private String defaultInputString;
    private DeviceEnum device;
    private Boolean disableCancelFlag;
    private Boolean disableCorrectFlag;
    private Boolean disableValidFlag;
    private Boolean fromRightToLeftFlag;
    private Boolean globalCorrectionFlag;
    private Boolean immediateResponseFlag;
    private InfoQualifyEnum infoQualify;
    private InputCommandEnum inputCommand;
    private Boolean maskCharactersFlag;
    private Long maxDecimalLength;
    private Long maxInputTime;
    private Long maxLength;
    private Boolean menuBackFlag;
    private Long minLength;
    private Boolean notifyCardInputFlag;
    private String stringMask;
    private Boolean waitUserValidationFlag;

    /**
     * When true, a beep is generated each time the user presses a key. Default false.
     */
    @JsonProperty("BeepKeyFlag")
    public Boolean getBEEPKeyFlag() { return beepKeyFlag; }
    @JsonProperty("BeepKeyFlag")
    public void setBEEPKeyFlag(Boolean value) { this.beepKeyFlag = value; }

    /**
     * Default string pre-filled in the input field. For GetConfirmation: 'Y' or 'N'.
     */
    @JsonProperty("DefaultInputString")
    public String getDefaultInputString() { return defaultInputString; }
    @JsonProperty("DefaultInputString")
    public void setDefaultInputString(String value) { this.defaultInputString = value; }

    @JsonProperty("Device")
    public DeviceEnum getDevice() { return device; }
    @JsonProperty("Device")
    public void setDevice(DeviceEnum value) { this.device = value; }

    /**
     * When true, the Cancel function key is disabled and not shown. Default false.
     */
    @JsonProperty("DisableCancelFlag")
    public Boolean getDisableCancelFlag() { return disableCancelFlag; }
    @JsonProperty("DisableCancelFlag")
    public void setDisableCancelFlag(Boolean value) { this.disableCancelFlag = value; }

    /**
     * When true, the Correct function key is disabled and not shown. Default false.
     */
    @JsonProperty("DisableCorrectFlag")
    public Boolean getDisableCorrectFlag() { return disableCorrectFlag; }
    @JsonProperty("DisableCorrectFlag")
    public void setDisableCorrectFlag(Boolean value) { this.disableCorrectFlag = value; }

    /**
     * When true, the Valid function key is disabled and not shown. Default false.
     */
    @JsonProperty("DisableValidFlag")
    public Boolean getDisableValidFlag() { return disableValidFlag; }
    @JsonProperty("DisableValidFlag")
    public void setDisableValidFlag(Boolean value) { this.disableValidFlag = value; }

    /**
     * When true, entered characters are displayed right-to-left (e.g. for amount entry).
     * Default false.
     */
    @JsonProperty("FromRightToLeftFlag")
    public Boolean getFromRightToLeftFlag() { return fromRightToLeftFlag; }
    @JsonProperty("FromRightToLeftFlag")
    public void setFromRightToLeftFlag(Boolean value) { this.fromRightToLeftFlag = value; }

    /**
     * When true, pressing Correct clears all entered characters; when false, only the last
     * character is removed. Default false.
     */
    @JsonProperty("GlobalCorrectionFlag")
    public Boolean getGlobalCorrectionFlag() { return globalCorrectionFlag; }
    @JsonProperty("GlobalCorrectionFlag")
    public void setGlobalCorrectionFlag(Boolean value) { this.globalCorrectionFlag = value; }

    /**
     * For GetAnyKey: when true, response is sent immediately without waiting for user
     * confirmation. Default true.
     */
    @JsonProperty("ImmediateResponseFlag")
    public Boolean getImmediateResponseFlag() { return immediateResponseFlag; }
    @JsonProperty("ImmediateResponseFlag")
    public void setImmediateResponseFlag(Boolean value) { this.immediateResponseFlag = value; }

    @JsonProperty("InfoQualify")
    public InfoQualifyEnum getInfoQualify() { return infoQualify; }
    @JsonProperty("InfoQualify")
    public void setInfoQualify(InfoQualifyEnum value) { this.infoQualify = value; }

    @JsonProperty("InputCommand")
    public InputCommandEnum getInputCommand() { return inputCommand; }
    @JsonProperty("InputCommand")
    public void setInputCommand(InputCommandEnum value) { this.inputCommand = value; }

    /**
     * When true, entered characters are masked (replaced by '•') in the display. Default false.
     */
    @JsonProperty("MaskCharactersFlag")
    public Boolean getMaskCharactersFlag() { return maskCharactersFlag; }
    @JsonProperty("MaskCharactersFlag")
    public void setMaskCharactersFlag(Boolean value) { this.maskCharactersFlag = value; }

    /**
     * Maximum number of digits after the decimal point for DecimalString input. Must be between
     * MinLength and MaxLength.
     */
    @JsonProperty("MaxDecimalLength")
    public Long getMaxDecimalLength() { return maxDecimalLength; }
    @JsonProperty("MaxDecimalLength")
    public void setMaxDecimalLength(Long value) { this.maxDecimalLength = value; }

    /**
     * Maximum time in seconds to wait for the user to complete the input before automatic
     * cancellation.
     */
    @JsonProperty("MaxInputTime")
    public Long getMaxInputTime() { return maxInputTime; }
    @JsonProperty("MaxInputTime")
    public void setMaxInputTime(Long value) { this.maxInputTime = value; }

    /**
     * Maximum length of the entered string, or maximum number of menu entries to select.
     */
    @JsonProperty("MaxLength")
    public Long getMaxLength() { return maxLength; }
    @JsonProperty("MaxLength")
    public void setMaxLength(Long value) { this.maxLength = value; }

    /**
     * For GetMenuEntry: when true, enables Back (returns -1) and Home (returns 0) navigation
     * keys. Default false.
     */
    @JsonProperty("MenuBackFlag")
    public Boolean getMenuBackFlag() { return menuBackFlag; }
    @JsonProperty("MenuBackFlag")
    public void setMenuBackFlag(Boolean value) { this.menuBackFlag = value; }

    /**
     * Minimum length of the entered string, or minimum number of menu entries to select.
     */
    @JsonProperty("MinLength")
    public Long getMinLength() { return minLength; }
    @JsonProperty("MinLength")
    public void setMinLength(Long value) { this.minLength = value; }

    /**
     * When true, the POI sends an InsertedCard error response if the customer inserts a card
     * instead of completing the input. Default false.
     */
    @JsonProperty("NotifyCardInputFlag")
    public Boolean getNotifyCardInputFlag() { return notifyCardInputFlag; }
    @JsonProperty("NotifyCardInputFlag")
    public void setNotifyCardInputFlag(Boolean value) { this.notifyCardInputFlag = value; }

    /**
     * Format mask for the input. Characters: 'd' (digit), 'a' (alpha), 's' (other printable),
     * any other char displayed but not entered, '\' escapes d/a/s/\.
     */
    @JsonProperty("StringMask")
    public String getStringMask() { return stringMask; }
    @JsonProperty("StringMask")
    public void setStringMask(String value) { this.stringMask = value; }

    /**
     * When true, waits for user confirmation even after reaching MaxLength, allowing
     * corrections. Default false.
     */
    @JsonProperty("WaitUserValidationFlag")
    public Boolean getWaitUserValidationFlag() { return waitUserValidationFlag; }
    @JsonProperty("WaitUserValidationFlag")
    public void setWaitUserValidationFlag(Boolean value) { this.waitUserValidationFlag = value; }
    
    public static Builder builder() { return new Builder(); }
    
    public static final class Builder {
        private Boolean beepKeyFlag;
        private String defaultInputString;
        private DeviceEnum device;
        private Boolean disableCancelFlag;
        private Boolean disableCorrectFlag;
        private Boolean disableValidFlag;
        private Boolean fromRightToLeftFlag;
        private Boolean globalCorrectionFlag;
        private Boolean immediateResponseFlag;
        private InfoQualifyEnum infoQualify;
        private InputCommandEnum inputCommand;
        private Boolean maskCharactersFlag;
        private Long maxDecimalLength;
        private Long maxInputTime;
        private Long maxLength;
        private Boolean menuBackFlag;
        private Long minLength;
        private Boolean notifyCardInputFlag;
        private String stringMask;
        private Boolean waitUserValidationFlag;
        
        private Builder() {}
        
        public Builder beepKeyFlag(Boolean beepKeyFlag) {
            this.beepKeyFlag = beepKeyFlag;
            return this;
        }
        
        public Builder defaultInputString(String defaultInputString) {
            this.defaultInputString = defaultInputString;
            return this;
        }
        
        public Builder device(DeviceEnum device) {
            this.device = device;
            return this;
        }
        
        public Builder disableCancelFlag(Boolean disableCancelFlag) {
            this.disableCancelFlag = disableCancelFlag;
            return this;
        }
        
        public Builder disableCorrectFlag(Boolean disableCorrectFlag) {
            this.disableCorrectFlag = disableCorrectFlag;
            return this;
        }
        
        public Builder disableValidFlag(Boolean disableValidFlag) {
            this.disableValidFlag = disableValidFlag;
            return this;
        }
        
        public Builder fromRightToLeftFlag(Boolean fromRightToLeftFlag) {
            this.fromRightToLeftFlag = fromRightToLeftFlag;
            return this;
        }
        
        public Builder globalCorrectionFlag(Boolean globalCorrectionFlag) {
            this.globalCorrectionFlag = globalCorrectionFlag;
            return this;
        }
        
        public Builder immediateResponseFlag(Boolean immediateResponseFlag) {
            this.immediateResponseFlag = immediateResponseFlag;
            return this;
        }
        
        public Builder infoQualify(InfoQualifyEnum infoQualify) {
            this.infoQualify = infoQualify;
            return this;
        }
        
        public Builder inputCommand(InputCommandEnum inputCommand) {
            this.inputCommand = inputCommand;
            return this;
        }
        
        public Builder maskCharactersFlag(Boolean maskCharactersFlag) {
            this.maskCharactersFlag = maskCharactersFlag;
            return this;
        }
        
        public Builder maxDecimalLength(Long maxDecimalLength) {
            this.maxDecimalLength = maxDecimalLength;
            return this;
        }
        
        public Builder maxInputTime(Long maxInputTime) {
            this.maxInputTime = maxInputTime;
            return this;
        }
        
        public Builder maxLength(Long maxLength) {
            this.maxLength = maxLength;
            return this;
        }
        
        public Builder menuBackFlag(Boolean menuBackFlag) {
            this.menuBackFlag = menuBackFlag;
            return this;
        }
        
        public Builder minLength(Long minLength) {
            this.minLength = minLength;
            return this;
        }
        
        public Builder notifyCardInputFlag(Boolean notifyCardInputFlag) {
            this.notifyCardInputFlag = notifyCardInputFlag;
            return this;
        }
        
        public Builder stringMask(String stringMask) {
            this.stringMask = stringMask;
            return this;
        }
        
        public Builder waitUserValidationFlag(Boolean waitUserValidationFlag) {
            this.waitUserValidationFlag = waitUserValidationFlag;
            return this;
        }
        
        public InputData build() {
            InputData result = new InputData();
            result.setBEEPKeyFlag(this.beepKeyFlag);
            result.setDefaultInputString(this.defaultInputString);
            result.setDevice(this.device);
            result.setDisableCancelFlag(this.disableCancelFlag);
            result.setDisableCorrectFlag(this.disableCorrectFlag);
            result.setDisableValidFlag(this.disableValidFlag);
            result.setFromRightToLeftFlag(this.fromRightToLeftFlag);
            result.setGlobalCorrectionFlag(this.globalCorrectionFlag);
            result.setImmediateResponseFlag(this.immediateResponseFlag);
            result.setInfoQualify(this.infoQualify);
            result.setInputCommand(this.inputCommand);
            result.setMaskCharactersFlag(this.maskCharactersFlag);
            result.setMaxDecimalLength(this.maxDecimalLength);
            result.setMaxInputTime(this.maxInputTime);
            result.setMaxLength(this.maxLength);
            result.setMenuBackFlag(this.menuBackFlag);
            result.setMinLength(this.minLength);
            result.setNotifyCardInputFlag(this.notifyCardInputFlag);
            result.setStringMask(this.stringMask);
            result.setWaitUserValidationFlag(this.waitUserValidationFlag);
            return result;
        }
    }
}
