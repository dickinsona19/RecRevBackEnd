package com.BossLiftingClub.BossLifting.Business;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

public class BusinessDTO {
    private Long id;

    @NotBlank(message = "Title is required")
    private String title;

    private String logoUrl;

    private String status;

    private LocalDateTime createdAt;

    private String businessTag; // Renamed from clubTag

    private Integer clientId;

    private Integer staffId;

    private String onboardingStatus;

    private String stripeAccountId;

    private String contactEmail;

    public static BusinessDTO mapToBusinessDTO(Business business) {
        BusinessDTO dto = new BusinessDTO();
        dto.setId(business.getId());
        dto.setTitle(business.getTitle());
        dto.setLogoUrl(business.getLogoUrl());
        dto.setStatus(business.getStatus());
        dto.setCreatedAt(business.getCreatedAt());
        dto.setBusinessTag(business.getBusinessTag());
        dto.setClientId(business.getClient() != null ? business.getClient().getId() : null);
        dto.setStaffId(business.getStaff() != null ? business.getStaff().getId() : null);
        dto.setOnboardingStatus(business.getOnboardingStatus());
        dto.setStripeAccountId(business.getStripeAccountId());
        dto.setContactEmail(business.getContactEmail());
        return dto;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getLogoUrl() {
        return logoUrl;
    }

    public void setLogoUrl(String logoUrl) {
        this.logoUrl = logoUrl;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public String getBusinessTag() {
        return businessTag;
    }

    public void setBusinessTag(String businessTag) {
        this.businessTag = businessTag;
    }

    public Integer getClientId() {
        return clientId;
    }

    public void setClientId(Integer clientId) {
        this.clientId = clientId;
    }

    public Integer getStaffId() {
        return staffId;
    }

    public void setStaffId(Integer staffId) {
        this.staffId = staffId;
    }

    public String getOnboardingStatus() {
        return onboardingStatus;
    }

    public void setOnboardingStatus(String onboardingStatus) {
        this.onboardingStatus = onboardingStatus;
    }

    public String getStripeAccountId() {
        return stripeAccountId;
    }

    public void setStripeAccountId(String stripeAccountId) {
        this.stripeAccountId = stripeAccountId;
    }

    public String getContactEmail() {
        return contactEmail;
    }

    public void setContactEmail(String contactEmail) {
        this.contactEmail = contactEmail;
    }
}





