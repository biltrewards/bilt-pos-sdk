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
 * Reference to a previous message request, used in Abort and TransactionStatus requests to
 * identify the target transaction.
 */
public class MessageReference {
    private String deviceID;
    private MessageCategoryType messageCategory;
    private String poiid;
    private String saleID;
    private String serviceID;

    /**
     * DeviceID copied from the original message request header.
     */
    @JsonProperty("DeviceID")
    public String getDeviceID() { return deviceID; }
    @JsonProperty("DeviceID")
    public void setDeviceID(String value) { this.deviceID = value; }

    @JsonProperty("MessageCategory")
    public MessageCategoryType getMessageCategory() { return messageCategory; }
    @JsonProperty("MessageCategory")
    public void setMessageCategory(MessageCategoryType value) { this.messageCategory = value; }

    /**
     * Identification of the POI Terminal that received the original message. Default is
     * MessageHeader.POIID.
     */
    @JsonProperty("POIID")
    public String getPoiid() { return poiid; }
    @JsonProperty("POIID")
    public void setPoiid(String value) { this.poiid = value; }

    /**
     * Identification of the Sale Terminal that sent the original message. Default is
     * MessageHeader.SaleID.
     */
    @JsonProperty("SaleID")
    public String getSaleID() { return saleID; }
    @JsonProperty("SaleID")
    public void setSaleID(String value) { this.saleID = value; }

    /**
     * ServiceID copied from the original message request header.
     */
    @JsonProperty("ServiceID")
    public String getServiceID() { return serviceID; }
    @JsonProperty("ServiceID")
    public void setServiceID(String value) { this.serviceID = value; }
    
    public static Builder builder() { return new Builder(); }
    
    public static final class Builder {
        private String deviceID;
        private MessageCategoryType messageCategory;
        private String poiid;
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
        
        public Builder poiid(String poiid) {
            this.poiid = poiid;
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
        
        public MessageReference build() {
            MessageReference result = new MessageReference();
            result.setDeviceID(this.deviceID);
            result.setMessageCategory(this.messageCategory);
            result.setPoiid(this.poiid);
            result.setSaleID(this.saleID);
            result.setServiceID(this.serviceID);
            return result;
        }
    }
}
