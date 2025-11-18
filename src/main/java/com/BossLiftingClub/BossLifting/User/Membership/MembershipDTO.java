package com.BossLiftingClub.BossLifting.User.Membership;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public class MembershipDTO {
    private Long id;

    @NotBlank(message = "Title is required")
    private String title;

    @NotBlank(message = "Price is required")
    private String price;

    @NotBlank(message = "Charge interval is required")
    @Pattern(regexp = "^(MONTHLY|WEEKLY|YEARLY)$", message = "Charge interval must be MONTHLY, WEEKLY, or YEARLY")
    private String chargeInterval;

    @NotBlank(message = "Business tag is required")
    private String businessTag;

    private String stripePriceId;

    private boolean archived = false;

    // Getters and Setters
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

    public String getPrice() {
        return price;
    }

    public void setPrice(String price) {
        this.price = price;
    }

    public String getChargeInterval() {
        return chargeInterval;
    }

    public void setChargeInterval(String chargeInterval) {
        this.chargeInterval = chargeInterval;
    }

    public String getBusinessTag() {
        return businessTag;
    }

    public void setBusinessTag(String businessTag) {
        this.businessTag = businessTag;
    }

    public String getStripePriceId() {
        return stripePriceId;
    }

    public void setStripePriceId(String stripePriceId) {
        this.stripePriceId = stripePriceId;
    }

    public boolean isArchived() {
        return archived;
    }

    public void setArchived(boolean archived) {
        this.archived = archived;
    }

    // Convert from Entity to DTO
    public static MembershipDTO fromEntity(Membership membership) {
        MembershipDTO dto = new MembershipDTO();
        dto.setId(membership.getId());
        dto.setTitle(membership.getTitle());
        dto.setPrice(membership.getPrice());
        dto.setChargeInterval(membership.getChargeInterval());
        dto.setBusinessTag(membership.getBusinessTag());
        dto.setStripePriceId(membership.getStripePriceId());
        dto.setArchived(membership.isArchived());
        return dto;
    }

    // Convert from DTO to Entity
    public Membership toEntity() {
        Membership membership = new Membership();
        membership.setId(this.id);
        membership.setTitle(this.title);
        membership.setPrice(this.price);
        membership.setChargeInterval(this.chargeInterval);
        membership.setBusinessTag(this.businessTag);
        membership.setStripePriceId(this.stripePriceId);
        membership.setArchived(this.archived);
        return membership;
    }
}
