package com.BossLiftingClub.BossLifting.Promo;

public class PromoCreateDTO {
    private String name;
    private String codeToken;
    private String businessTag;
    private Double discountValue;
    private String discountType; // 'percent' or 'amount'
    private String duration; // 'forever', 'once', 'repeating'
    private Integer durationInMonths;

    // Getters and Setters
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getCodeToken() {
        return codeToken;
    }

    public void setCodeToken(String codeToken) {
        this.codeToken = codeToken;
    }

    public String getBusinessTag() {
        return businessTag;
    }

    public void setBusinessTag(String businessTag) {
        this.businessTag = businessTag;
    }

    public Double getDiscountValue() {
        return discountValue;
    }

    public void setDiscountValue(Double discountValue) {
        this.discountValue = discountValue;
    }

    public String getDiscountType() {
        return discountType;
    }

    public void setDiscountType(String discountType) {
        this.discountType = discountType;
    }

    public String getDuration() {
        return duration;
    }

    public void setDuration(String duration) {
        this.duration = duration;
    }

    public Integer getDurationInMonths() {
        return durationInMonths;
    }

    public void setDurationInMonths(Integer durationInMonths) {
        this.durationInMonths = durationInMonths;
    }
}

