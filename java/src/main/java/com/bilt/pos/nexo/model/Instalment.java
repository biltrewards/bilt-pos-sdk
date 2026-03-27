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
 * Information related to an instalment payment plan, either merchant-managed or
 * issuer-managed.
 */
public class Instalment {
    private Double charges;
    private Double cumulativeAmount;
    private Double firstAmount;
    private String firstPaymentDate;
    private InstalmentTypeEnum instalmentType;
    private Long period;
    private PeriodUnitEnum periodUnit;
    private String planID;
    private Long sequenceNumber;
    private Long totalNbOfPayments;

    /**
     * Charges related to the instalment plan.
     */
    @JsonProperty("Charges")
    public Double getCharges() { return charges; }
    @JsonProperty("Charges")
    public void setCharges(Double value) { this.charges = value; }

    /**
     * Total cumulative amount of all instalments.
     */
    @JsonProperty("CumulativeAmount")
    public Double getCumulativeAmount() { return cumulativeAmount; }
    @JsonProperty("CumulativeAmount")
    public void setCumulativeAmount(Double value) { this.cumulativeAmount = value; }

    /**
     * Amount of the first instalment when different from the others. Mandatory for
     * InequalInstalments.
     */
    @JsonProperty("FirstAmount")
    public Double getFirstAmount() { return firstAmount; }
    @JsonProperty("FirstAmount")
    public void setFirstAmount(Double value) { this.firstAmount = value; }

    /**
     * Date of the first instalment payment. Mandatory for DeferredInstalments.
     */
    @JsonProperty("FirstPaymentDate")
    public String getFirstPaymentDate() { return firstPaymentDate; }
    @JsonProperty("FirstPaymentDate")
    public void setFirstPaymentDate(String value) { this.firstPaymentDate = value; }

    @JsonProperty("InstalmentType")
    public InstalmentTypeEnum getInstalmentType() { return instalmentType; }
    @JsonProperty("InstalmentType")
    public void setInstalmentType(InstalmentTypeEnum value) { this.instalmentType = value; }

    /**
     * Number of PeriodUnit intervals between consecutive instalment payments.
     */
    @JsonProperty("Period")
    public Long getPeriod() { return period; }
    @JsonProperty("Period")
    public void setPeriod(Long value) { this.period = value; }

    @JsonProperty("PeriodUnit")
    public PeriodUnitEnum getPeriodUnit() { return periodUnit; }
    @JsonProperty("PeriodUnit")
    public void setPeriodUnit(PeriodUnitEnum value) { this.periodUnit = value; }

    /**
     * Identification of the instalment plan.
     */
    @JsonProperty("PlanID")
    public String getPlanID() { return planID; }
    @JsonProperty("PlanID")
    public void setPlanID(String value) { this.planID = value; }

    /**
     * Sequence number of this instalment payment (1 to TotalNbOfPayments).
     */
    @JsonProperty("SequenceNumber")
    public Long getSequenceNumber() { return sequenceNumber; }
    @JsonProperty("SequenceNumber")
    public void setSequenceNumber(Long value) { this.sequenceNumber = value; }

    /**
     * Total number of instalment payments including the first one.
     */
    @JsonProperty("TotalNbOfPayments")
    public Long getTotalNbOfPayments() { return totalNbOfPayments; }
    @JsonProperty("TotalNbOfPayments")
    public void setTotalNbOfPayments(Long value) { this.totalNbOfPayments = value; }
    
    public static Builder builder() { return new Builder(); }
    
    public static final class Builder {
        private Double charges;
        private Double cumulativeAmount;
        private Double firstAmount;
        private String firstPaymentDate;
        private InstalmentTypeEnum instalmentType;
        private Long period;
        private PeriodUnitEnum periodUnit;
        private String planID;
        private Long sequenceNumber;
        private Long totalNbOfPayments;
        
        private Builder() {}
        
        public Builder charges(Double charges) {
            this.charges = charges;
            return this;
        }
        
        public Builder cumulativeAmount(Double cumulativeAmount) {
            this.cumulativeAmount = cumulativeAmount;
            return this;
        }
        
        public Builder firstAmount(Double firstAmount) {
            this.firstAmount = firstAmount;
            return this;
        }
        
        public Builder firstPaymentDate(String firstPaymentDate) {
            this.firstPaymentDate = firstPaymentDate;
            return this;
        }
        
        public Builder instalmentType(InstalmentTypeEnum instalmentType) {
            this.instalmentType = instalmentType;
            return this;
        }
        
        public Builder period(Long period) {
            this.period = period;
            return this;
        }
        
        public Builder periodUnit(PeriodUnitEnum periodUnit) {
            this.periodUnit = periodUnit;
            return this;
        }
        
        public Builder planID(String planID) {
            this.planID = planID;
            return this;
        }
        
        public Builder sequenceNumber(Long sequenceNumber) {
            this.sequenceNumber = sequenceNumber;
            return this;
        }
        
        public Builder totalNbOfPayments(Long totalNbOfPayments) {
            this.totalNbOfPayments = totalNbOfPayments;
            return this;
        }
        
        public Instalment build() {
            Instalment result = new Instalment();
            result.setCharges(this.charges);
            result.setCumulativeAmount(this.cumulativeAmount);
            result.setFirstAmount(this.firstAmount);
            result.setFirstPaymentDate(this.firstPaymentDate);
            result.setInstalmentType(this.instalmentType);
            result.setPeriod(this.period);
            result.setPeriodUnit(this.periodUnit);
            result.setPlanID(this.planID);
            result.setSequenceNumber(this.sequenceNumber);
            result.setTotalNbOfPayments(this.totalNbOfPayments);
            return result;
        }
    }
}
