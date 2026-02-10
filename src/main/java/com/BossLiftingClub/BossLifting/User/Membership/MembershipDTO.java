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

    private String membershipType = "UNLIMITED"; // UNLIMITED, PUNCH_CARD, ONE_OFF

    private Integer punchCount; // For punch cards

    private Integer expiryDays; // For punch cards (null = permanent)

    private String oneOffType; // Format: "DURATION_UNIT" (e.g., "1_WEEK", "2_MONTH", "3_DAY", "1_YEAR") or legacy "WEEK_PASS", "MONTH_PASS"

    private java.math.BigDecimal processingFee; // Optional processing fee

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
        dto.setMembershipType(membership.getMembershipType());
        dto.setPunchCount(membership.getPunchCount());
        dto.setExpiryDays(membership.getExpiryDays());
        dto.setOneOffType(membership.getOneOffType());
        dto.setProcessingFee(membership.getProcessingFee());
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
        membership.setMembershipType(this.membershipType);
        membership.setPunchCount(this.punchCount);
        membership.setExpiryDays(this.expiryDays);
        membership.setOneOffType(this.oneOffType);
        membership.setProcessingFee(this.processingFee);
        return membership;
    }

    // Getters and Setters for new fields
    public String getMembershipType() {
        return membershipType;
    }

    public void setMembershipType(String membershipType) {
        this.membershipType = membershipType;
    }

    public Integer getPunchCount() {
        return punchCount;
    }

    public void setPunchCount(Integer punchCount) {
        this.punchCount = punchCount;
    }

    public Integer getExpiryDays() {
        return expiryDays;
    }

    public void setExpiryDays(Integer expiryDays) {
        this.expiryDays = expiryDays;
    }

    public String getOneOffType() {
        return oneOffType;
    }

    public void setOneOffType(String oneOffType) {
        this.oneOffType = oneOffType;
    }

    public java.math.BigDecimal getProcessingFee() {
        return processingFee;
    }

    public void setProcessingFee(java.math.BigDecimal processingFee) {
        this.processingFee = processingFee;
    }
}
