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
 * Message header common to all Sale to POI protocol messages, conveying protocol management
 * information.
 */
public class MessageHeader {
    private String deviceID;
    private MessageCategoryType messageCategory;
    private MessageClassType messageClass;
    private MessageTypeType messageType;
    private String poiid;
    private String protocolVersion;
    private String saleID;
    private String serviceID;

    /**
     * Unique identification of a Device message pair. Mandatory for Device class messages.
     */
    @JsonProperty("DeviceID")
    public String getDeviceID() { return deviceID; }
    @JsonProperty("DeviceID")
    public void setDeviceID(String value) { this.deviceID = value; }

    @JsonProperty("MessageCategory")
    public MessageCategoryType getMessageCategory() { return messageCategory; }
    @JsonProperty("MessageCategory")
    public void setMessageCategory(MessageCategoryType value) { this.messageCategory = value; }

    @JsonProperty("MessageClass")
    public MessageClassType getMessageClass() { return messageClass; }
    @JsonProperty("MessageClass")
    public void setMessageClass(MessageClassType value) { this.messageClass = value; }

    @JsonProperty("MessageType")
    public MessageTypeType getMessageType() { return messageType; }
    @JsonProperty("MessageType")
    public void setMessageType(MessageTypeType value) { this.messageType = value; }

    /**
     * Hierarchical identification of the POI System or POI Terminal. Its scope is limited to
     * the Sale to POI protocol.
     */
    @JsonProperty("POIID")
    public String getPoiid() { return poiid; }
    @JsonProperty("POIID")
    public void setPoiid(String value) { this.poiid = value; }

    /**
     * Highest version of the Sale to POI protocol managed by the sender. Mandatory in Login and
     * Diagnosis messages.
     */
    @JsonProperty("ProtocolVersion")
    public String getProtocolVersion() { return protocolVersion; }
    @JsonProperty("ProtocolVersion")
    public void setProtocolVersion(String value) { this.protocolVersion = value; }

    /**
     * Hierarchical identification of the Sale System or Sale Terminal. Its scope is limited to
     * the Sale to POI protocol.
     */
    @JsonProperty("SaleID")
    public String getSaleID() { return saleID; }
    @JsonProperty("SaleID")
    public void setSaleID(String value) { this.saleID = value; }

    /**
     * Unique identification of a Service or Event message pair between a Sale and POI
     * component. Mandatory for Service and Event class messages.
     */
    @JsonProperty("ServiceID")
    public String getServiceID() { return serviceID; }
    @JsonProperty("ServiceID")
    public void setServiceID(String value) { this.serviceID = value; }
    
    public static Builder builder() { return new Builder(); }
    
    public static final class Builder {
        private String deviceID;
        private MessageCategoryType messageCategory;
        private MessageClassType messageClass;
        private MessageTypeType messageType;
        private String poiid;
        private String protocolVersion;
        private String saleID;
        private String serviceID;
        
        private Builder() {}
        
        public Builder deviceID(String deviceID) {
            this.deviceID = deviceID;
            return this;
        }
        
        public Builder messageCategory(MessageCategoryType messageCategory) {
            this.messageCategory = messageCategory;
            return this;
        }
        
        public Builder messageClass(MessageClassType messageClass) {
            this.messageClass = messageClass;
            return this;
        }
        
        public Builder messageType(MessageTypeType messageType) {
            this.messageType = messageType;
            return this;
        }
        
        public Builder poiid(String poiid) {
            this.poiid = poiid;
            return this;
        }
        
        public Builder protocolVersion(String protocolVersion) {
            this.protocolVersion = protocolVersion;
            return this;
        }
        
        public Builder saleID(String saleID) {
            this.saleID = saleID;
            return this;
        }
        
        public Builder serviceID(String serviceID) {
            this.serviceID = serviceID;
            return this;
        }
        
        public MessageHeader build() {
            MessageHeader result = new MessageHeader();
            result.setDeviceID(this.deviceID);
            result.setMessageCategory(this.messageCategory);
            result.setMessageClass(this.messageClass);
            result.setMessageType(this.messageType);
            result.setPoiid(this.poiid);
            result.setProtocolVersion(this.protocolVersion);
            result.setSaleID(this.saleID);
            result.setServiceID(this.serviceID);
            return result;
        }
    }
}
