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
 * Content of the Admin Request message, used to select and start customised administrative
 * services on the POI.
 */
public class AdminRequest {
    private String serviceIdentification;

    /**
     * Direct identification of the administrative service to execute, bypassing the interactive
     * menu. May be a name or CSV path of menu items.
     */
    @JsonProperty("ServiceIdentification")
    public String getServiceIdentification() { return serviceIdentification; }
    @JsonProperty("ServiceIdentification")
    public void setServiceIdentification(String value) { this.serviceIdentification = value; }
    
    public static Builder builder() { return new Builder(); }
    
    public static final class Builder {
        private String serviceIdentification;
        
        private Builder() {}
        
        public Builder serviceIdentification(String serviceIdentification) {
            this.serviceIdentification = serviceIdentification;
            return this;
        }
        
        public AdminRequest build() {
            AdminRequest result = new AdminRequest();
            result.setServiceIdentification(this.serviceIdentification);
            return result;
        }
    }
}
