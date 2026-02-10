package com.BossLiftingClub.BossLifting.User.Membership;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import java.math.BigDecimal;
import java.util.List;

public class PackageDTO {
    private Long id;

    @NotBlank(message = "Package name is required")
    private String name;

    private Long businessId;

    @NotBlank(message = "Business tag is required")
    private String businessTag;

    private BigDecimal price;

    private String stripeProductId;

    private boolean archived = false;

    private List<Long> membershipIds; // IDs of memberships in this package

    private Integer memberCount = 0; // Number of members with this package

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Long getBusinessId() {
        return businessId;
    }

    public void setBusinessId(Long businessId) {
        this.businessId = businessId;
    }

    public String getBusinessTag() {
        return businessTag;
    }

    public void setBusinessTag(String businessTag) {
        this.businessTag = businessTag;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
    }

    public String getStripeProductId() {
        return stripeProductId;
    }

    public void setStripeProductId(String stripeProductId) {
        this.stripeProductId = stripeProductId;
    }

    public boolean isArchived() {
        return archived;
    }

    public void setArchived(boolean archived) {
        this.archived = archived;
    }

    public List<Long> getMembershipIds() {
        return membershipIds;
    }

    public void setMembershipIds(List<Long> membershipIds) {
        this.membershipIds = membershipIds;
    }

    public Integer getMemberCount() {
        return memberCount;
    }

    public void setMemberCount(Integer memberCount) {
        this.memberCount = memberCount;
    }
}
