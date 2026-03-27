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
 * Content of the Logout Request message, ending the association between a Sale Terminal and
 * a POI Terminal.
 */
public class LogoutRequest {
    private Boolean maintenanceAllowed;

    /**
     * When true, indicates the POI may enter maintenance mode after the session is closed.
     * Default false.
     */
    @JsonProperty("MaintenanceAllowed")
    public Boolean getMaintenanceAllowed() { return maintenanceAllowed; }
    @JsonProperty("MaintenanceAllowed")
    public void setMaintenanceAllowed(Boolean value) { this.maintenanceAllowed = value; }
    
    public static Builder builder() { return new Builder(); }
    
    public static final class Builder {
        private Boolean maintenanceAllowed;
        
        private Builder() {}
        
        public Builder maintenanceAllowed(Boolean maintenanceAllowed) {
            this.maintenanceAllowed = maintenanceAllowed;
            return this;
        }
        
        public LogoutRequest build() {
            LogoutRequest result = new LogoutRequest();
            result.setMaintenanceAllowed(this.maintenanceAllowed);
            return result;
        }
    }
}
