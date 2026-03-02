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
 * Content of the Event Notification message, sent by the POI to inform the Sale System of
 * an unsolicited event.
 */
public class EventNotification {
    private String customerLanguage;
    private DisplayOutput[] displayOutput;
    private String eventDetails;
    private EventToNotifyEnum eventToNotify;
    private Boolean maintenanceRequiredFlag;
    private String rejectedMessage;
    private String timeStamp;

    /**
     * New language selected by the customer. Mandatory when EventToNotify is CustomerLanguage.
     */
    @JsonProperty("CustomerLanguage")
    public String getCustomerLanguage() { return customerLanguage; }
    @JsonProperty("CustomerLanguage")
    public void setCustomerLanguage(String value) { this.customerLanguage = value; }

    @JsonProperty("DisplayOutput")
    public DisplayOutput[] getDisplayOutput() { return displayOutput; }
    @JsonProperty("DisplayOutput")
    public void setDisplayOutput(DisplayOutput[] value) { this.displayOutput = value; }

    /**
     * Additional information about the event for logging. Mandatory for SaleWakeUp (transaction
     * reference), KeyPressed (key ID), and SaleAdmin (service name).
     */
    @JsonProperty("EventDetails")
    public String getEventDetails() { return eventDetails; }
    @JsonProperty("EventDetails")
    public void setEventDetails(String value) { this.eventDetails = value; }

    @JsonProperty("EventToNotify")
    public EventToNotifyEnum getEventToNotify() { return eventToNotify; }
    @JsonProperty("EventToNotify")
    public void setEventToNotify(EventToNotifyEnum value) { this.eventToNotify = value; }

    /**
     * When true, the event requires a maintenance call or action. Default false.
     */
    @JsonProperty("MaintenanceRequiredFlag")
    public Boolean getMaintenanceRequiredFlag() { return maintenanceRequiredFlag; }
    @JsonProperty("MaintenanceRequiredFlag")
    public void setMaintenanceRequiredFlag(Boolean value) { this.maintenanceRequiredFlag = value; }

    /**
     * Base64-encoded content of the rejected message. Mandatory when EventToNotify is Reject.
     */
    @JsonProperty("RejectedMessage")
    public String getRejectedMessage() { return rejectedMessage; }
    @JsonProperty("RejectedMessage")
    public void setRejectedMessage(String value) { this.rejectedMessage = value; }

    @JsonProperty("TimeStamp")
    public String getTimeStamp() { return timeStamp; }
    @JsonProperty("TimeStamp")
    public void setTimeStamp(String value) { this.timeStamp = value; }
    
    public static Builder builder() { return new Builder(); }
    
    public static final class Builder {
        private String customerLanguage;
        private DisplayOutput[] displayOutput;
        private String eventDetails;
        private EventToNotifyEnum eventToNotify;
        private Boolean maintenanceRequiredFlag;
        private String rejectedMessage;
        private String timeStamp;
        
        private Builder() {}
        
        public Builder customerLanguage(String customerLanguage) {
            this.customerLanguage = customerLanguage;
            return this;
        }
        
        public Builder displayOutput(DisplayOutput[] displayOutput) {
            this.displayOutput = displayOutput;
            return this;
        }
        
        public Builder eventDetails(String eventDetails) {
            this.eventDetails = eventDetails;
            return this;
        }
        
        public Builder eventToNotify(EventToNotifyEnum eventToNotify) {
            this.eventToNotify = eventToNotify;
            return this;
        }
        
        public Builder maintenanceRequiredFlag(Boolean maintenanceRequiredFlag) {
            this.maintenanceRequiredFlag = maintenanceRequiredFlag;
            return this;
        }
        
        public Builder rejectedMessage(String rejectedMessage) {
            this.rejectedMessage = rejectedMessage;
            return this;
        }
        
        public Builder timeStamp(String timeStamp) {
            this.timeStamp = timeStamp;
            return this;
        }
        
        public EventNotification build() {
            EventNotification result = new EventNotification();
            result.setCustomerLanguage(this.customerLanguage);
            result.setDisplayOutput(this.displayOutput);
            result.setEventDetails(this.eventDetails);
            result.setEventToNotify(this.eventToNotify);
            result.setMaintenanceRequiredFlag(this.maintenanceRequiredFlag);
            result.setRejectedMessage(this.rejectedMessage);
            result.setTimeStamp(this.timeStamp);
            return result;
        }
    }
}
