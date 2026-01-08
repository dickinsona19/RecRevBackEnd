package com.BossLiftingClub.BossLifting.User.Membership;

import com.fasterxml.jackson.annotation.JsonProperty;
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

    @JsonProperty("isPublic")
    private boolean isPublic = false;

    private String publicDisplayName;

    private String publicDescription;

    private String publicBenefits; // JSON array as string

    private Integer memberCount = 0; // Number of members with this membership

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

    @JsonProperty("isPublic")
    public boolean isPublic() {
        return isPublic;
    }

    public void setPublic(boolean isPublic) {
        this.isPublic = isPublic;
    }

    public String getPublicDisplayName() {
        return publicDisplayName;
    }

    public void setPublicDisplayName(String publicDisplayName) {
        this.publicDisplayName = publicDisplayName;
    }

    public String getPublicDescription() {
        return publicDescription;
    }

    public void setPublicDescription(String publicDescription) {
        this.publicDescription = publicDescription;
    }

    public String getPublicBenefits() {
        return publicBenefits;
    }

    public void setPublicBenefits(String publicBenefits) {
        this.publicBenefits = publicBenefits;
    }

    public Integer getMemberCount() {
        return memberCount;
    }

    public void setMemberCount(Integer memberCount) {
        this.memberCount = memberCount;
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
        dto.setPublic(membership.isPublic());
        dto.setPublicDisplayName(membership.getPublicDisplayName());
        dto.setPublicDescription(membership.getPublicDescription());
        dto.setPublicBenefits(membership.getPublicBenefits());
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
        membership.setPublic(this.isPublic);
        membership.setPublicDisplayName(this.publicDisplayName);
        membership.setPublicDescription(this.publicDescription);
        membership.setPublicBenefits(this.publicBenefits);
        return membership;
    }
}
