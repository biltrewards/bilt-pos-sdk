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
 * Content of the Card Acquisition Response message, conveying the read card data and
 * available payment/loyalty brands.
 */
public class CardAcquisitionResponse {
    private String customerLanguage;
    private CustomerOrder[] customerOrder;
    private LoyaltyAccount[] loyaltyAccount;
    private String[] paymentBrand;
    private PaymentInstrumentData paymentInstrumentData;
    private POIData poiData;
    private Response response;
    private SaleData saleData;

    @JsonProperty("CustomerLanguage")
    public String getCustomerLanguage() { return customerLanguage; }
    @JsonProperty("CustomerLanguage")
    public void setCustomerLanguage(String value) { this.customerLanguage = value; }

    @JsonProperty("CustomerOrder")
    public CustomerOrder[] getCustomerOrder() { return customerOrder; }
    @JsonProperty("CustomerOrder")
    public void setCustomerOrder(CustomerOrder[] value) { this.customerOrder = value; }

    /**
     * Loyalty accounts identified on the presented card(s).
     */
    @JsonProperty("LoyaltyAccount")
    public LoyaltyAccount[] getLoyaltyAccount() { return loyaltyAccount; }
    @JsonProperty("LoyaltyAccount")
    public void setLoyaltyAccount(LoyaltyAccount[] value) { this.loyaltyAccount = value; }

    /**
     * Payment brands available on the presented card. Multiple values when the customer has not
     * yet selected one.
     */
    @JsonProperty("PaymentBrand")
    public String[] getPaymentBrand() { return paymentBrand; }
    @JsonProperty("PaymentBrand")
    public void setPaymentBrand(String[] value) { this.paymentBrand = value; }

    @JsonProperty("PaymentInstrumentData")
    public PaymentInstrumentData getPaymentInstrumentData() { return paymentInstrumentData; }
    @JsonProperty("PaymentInstrumentData")
    public void setPaymentInstrumentData(PaymentInstrumentData value) { this.paymentInstrumentData = value; }

    @JsonProperty("POIData")
    public POIData getPoiData() { return poiData; }
    @JsonProperty("POIData")
    public void setPoiData(POIData value) { this.poiData = value; }

    @JsonProperty("Response")
    public Response getResponse() { return response; }
    @JsonProperty("Response")
    public void setResponse(Response value) { this.response = value; }

    @JsonProperty("SaleData")
    public SaleData getSaleData() { return saleData; }
    @JsonProperty("SaleData")
    public void setSaleData(SaleData value) { this.saleData = value; }
    
    public static Builder builder() { return new Builder(); }
    
    public static final class Builder {
        private String customerLanguage;
        private CustomerOrder[] customerOrder;
        private LoyaltyAccount[] loyaltyAccount;
        private String[] paymentBrand;
        private PaymentInstrumentData paymentInstrumentData;
        private POIData poiData;
        private Response response;
        private SaleData saleData;
        
        private Builder() {}
        
        public Builder customerLanguage(String customerLanguage) {
            this.customerLanguage = customerLanguage;
            return this;
        }
        
        public Builder customerOrder(CustomerOrder[] customerOrder) {
            this.customerOrder = customerOrder;
            return this;
        }
        
        public Builder loyaltyAccount(LoyaltyAccount[] loyaltyAccount) {
            this.loyaltyAccount = loyaltyAccount;
            return this;
        }
        
        public Builder paymentBrand(String[] paymentBrand) {
            this.paymentBrand = paymentBrand;
            return this;
        }
        
        public Builder paymentInstrumentData(PaymentInstrumentData paymentInstrumentData) {
            this.paymentInstrumentData = paymentInstrumentData;
            return this;
        }
        
        public Builder poiData(POIData poiData) {
            this.poiData = poiData;
            return this;
        }
        
        public Builder response(Response response) {
            this.response = response;
            return this;
        }
        
        public Builder saleData(SaleData saleData) {
            this.saleData = saleData;
            return this;
        }
        
        public CardAcquisitionResponse build() {
            CardAcquisitionResponse result = new CardAcquisitionResponse();
            result.setCustomerLanguage(this.customerLanguage);
            result.setCustomerOrder(this.customerOrder);
            result.setLoyaltyAccount(this.loyaltyAccount);
            result.setPaymentBrand(this.paymentBrand);
            result.setPaymentInstrumentData(this.paymentInstrumentData);
            result.setPoiData(this.poiData);
            result.setResponse(this.response);
            result.setSaleData(this.saleData);
            return result;
        }
    }
}
