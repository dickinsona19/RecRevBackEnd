package com.BossLiftingClub.BossLifting.Club;

import jakarta.validation.constraints.NotBlank;

import java.time.LocalDateTime;

public class ClubDTO {
    private Long id;

    @NotBlank(message = "Title is required")
    private String title;

    private String logoUrl;

    private String status;

    private LocalDateTime createdAt;

    private String clubTag; // Removed @NotBlank to allow automatic generation

    private Integer clientId;

    private Integer staffId;

    private String onboardingStatus;

    public static ClubDTO mapToClubDTO(Club club) {
        ClubDTO dto = new ClubDTO();
        dto.setId(club.getId());
        dto.setTitle(club.getTitle());
        dto.setLogoUrl(club.getLogoUrl());
        dto.setStatus(club.getStatus());
        dto.setCreatedAt(club.getCreatedAt());
        dto.setClubTag(club.getClubTag());
        dto.setClientId(club.getClient() != null ? club.getClient().getId() : null);
        dto.setStaffId(club.getStaff() != null ? club.getStaff().getId() : null);
        dto.setOnboardingStatus(club.getOnboardingStatus());
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

    public String getClubTag() {
        return clubTag;
    }

    public void setClubTag(String clubTag) {
        this.clubTag = clubTag;
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
}