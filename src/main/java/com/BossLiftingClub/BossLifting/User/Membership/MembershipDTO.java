package com.BossLiftingClub.BossLifting.User.Membership;

import lombok.Data;

@Data
public class MembershipDTO {
    private Long id;
    private String title;
    private String price;
    private String chargeInterval;
    private String clubTag;

    public MembershipDTO() {}

    public MembershipDTO(Membership membership) {
        if (membership != null) {
            this.id = membership.getId();
            this.title = membership.getTitle();
            this.price = membership.getPrice();
            this.chargeInterval = membership.getChargeInterval();
            this.clubTag = membership.getClubTag();
        }
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

    public String getClubTag() {
        return clubTag;
    }

    public void setClubTag(String clubTag) {
        this.clubTag = clubTag;
    }
}