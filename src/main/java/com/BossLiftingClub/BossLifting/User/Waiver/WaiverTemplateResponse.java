package com.BossLiftingClub.BossLifting.User.Waiver;

import java.time.LocalDateTime;

public class WaiverTemplateResponse {

    private Long id;
    private Long businessId;
    private String fileUrl;
    private Integer version;
    private Boolean active;
    private LocalDateTime createdAt;

    public WaiverTemplateResponse() {
    }

    public WaiverTemplateResponse(Long id, Long businessId, String fileUrl, Integer version, Boolean active, LocalDateTime createdAt) {
        this.id = id;
        this.businessId = businessId;
        this.fileUrl = fileUrl;
        this.version = version;
        this.active = active;
        this.createdAt = createdAt;
    }

    public static WaiverTemplateResponse fromEntity(WaiverTemplate template) {
        return new WaiverTemplateResponse(
                template.getId(),
                template.getBusiness() != null ? template.getBusiness().getId() : null,
                template.getFileUrl(),
                template.getVersion(),
                template.getActive(),
                template.getCreatedAt()
        );
    }

    public Long getId() {
        return id;
    }

    public Long getBusinessId() {
        return businessId;
    }

    public String getFileUrl() {
        return fileUrl;
    }

    public Integer getVersion() {
        return version;
    }

    public Boolean getActive() {
        return active;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}

