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
 * Functional profile of the Sale Terminal, declaring the generic profile and optional
 * service profiles supported during the session.
 *
 * Functional profile of the POI Terminal for this session.
 */
public class SaleProfile {
    private GenericProfileType genericProfile;
    private ServiceProfilesType[] serviceProfiles;

    @JsonProperty("GenericProfile")
    public GenericProfileType getGenericProfile() { return genericProfile; }
    @JsonProperty("GenericProfile")
    public void setGenericProfile(GenericProfileType value) { this.genericProfile = value; }

    @JsonProperty("ServiceProfiles")
    public ServiceProfilesType[] getServiceProfiles() { return serviceProfiles; }
    @JsonProperty("ServiceProfiles")
    public void setServiceProfiles(ServiceProfilesType[] value) { this.serviceProfiles = value; }
    
    public static Builder builder() { return new Builder(); }
    
    public static final class Builder {
        private GenericProfileType genericProfile;
        private ServiceProfilesType[] serviceProfiles;
        
        private Builder() {}
        
        public Builder genericProfile(GenericProfileType genericProfile) {
            this.genericProfile = genericProfile;
            return this;
        }
        
        public Builder serviceProfiles(ServiceProfilesType[] serviceProfiles) {
            this.serviceProfiles = serviceProfiles;
            return this;
        }
        
        public SaleProfile build() {
            SaleProfile result = new SaleProfile();
            result.setGenericProfile(this.genericProfile);
            result.setServiceProfiles(this.serviceProfiles);
            return result;
        }
    }
}
