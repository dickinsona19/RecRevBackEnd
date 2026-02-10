package com.BossLiftingClub.BossLifting.User.Membership;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "membership")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Membership {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column
    private String title;

    @Column
    private String price;

    @Column(name = "charge_interval")
    private String chargeInterval;

    @Column(name = "business_tag")
    private String businessTag;
    
    // Backward compatibility - map club_tag to business_tag in database
    @Deprecated
    @Column(name = "club_tag", insertable = false, updatable = false)
    private String clubTag;

    @Column(name = "stripe_price_id")
    private String stripePriceId;

    @Column(columnDefinition = "BOOLEAN DEFAULT FALSE")
    private boolean archived = false;

    @Column(name = "is_public", columnDefinition = "BOOLEAN DEFAULT FALSE")
    private boolean isPublic = false;

    @Column(name = "public_display_name")
    private String publicDisplayName;

    @Column(name = "public_description", columnDefinition = "TEXT")
    private String publicDescription;

    @Column(name = "public_benefits", columnDefinition = "TEXT")
    private String publicBenefits; // JSON array stored as text

    @Column(name = "membership_type")
    private String membershipType = "UNLIMITED"; // UNLIMITED, PUNCH_CARD, ONE_OFF

    @Column(name = "punch_count")
    private Integer punchCount;

    @Column(name = "expiry_days")
    private Integer expiryDays; // null = permanent

    @Column(name = "one_off_type")
    private String oneOffType; // Format: "DURATION_UNIT" (e.g., "1_WEEK", "2_MONTH", "3_DAY", "1_YEAR") or legacy "WEEK_PASS", "MONTH_PASS"

    @Column(name = "processing_fee", precision = 10, scale = 2)
    private java.math.BigDecimal processingFee;

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
    
    // Backward compatibility getter
    @Deprecated
    public String getClubTag() {
        return businessTag;
    }

    @Deprecated
    public void setClubTag(String clubTag) {
        this.businessTag = clubTag;
    }

    public boolean isArchived() {
        return archived;
    }

    public void setArchived(boolean archived) {
        this.archived = archived;
    }

    public String getStripePriceId() {
        return stripePriceId;
    }

    public void setStripePriceId(String stripePriceId) {
        this.stripePriceId = stripePriceId;
    }

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