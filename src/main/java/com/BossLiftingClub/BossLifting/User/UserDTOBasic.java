package com.BossLiftingClub.BossLifting.User;

import com.BossLiftingClub.BossLifting.User.Membership.Membership;
import com.BossLiftingClub.BossLifting.User.SignInLog.SignInLogDto;
import com.BossLiftingClub.BossLifting.User.UserTitles.UserTitles;

import java.time.LocalDateTime;
import java.util.Set;
import java.util.stream.Collectors;

public class UserDTOBasic {
    private Long id;
    private String firstName;
    private String lastName;
    private Boolean isInGoodStanding;
    private Boolean isOver18;
    private String signatureData;
    private String profilePictureUrl;
    private Membership membership;

    public UserDTOBasic() {}

    public UserDTOBasic(Long id, String firstName, String lastName,
                        Boolean isInGoodStanding, Boolean isOver18,
                        String signatureData, String profilePictureUrl,
                        Membership membership) {
        this.id = id;
        this.firstName = firstName;
        this.lastName = lastName;
        this.isInGoodStanding = isInGoodStanding;
        this.isOver18 = isOver18;
        this.signatureData = signatureData;
        this.profilePictureUrl = profilePictureUrl;
        this.membership = membership;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getFirstName() {
        return firstName;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public void setLastName(String lastName) {
        this.lastName = lastName;
    }

    public Boolean getInGoodStanding() {
        return isInGoodStanding;
    }

    public void setInGoodStanding(Boolean inGoodStanding) {
        isInGoodStanding = inGoodStanding;
    }

    public Boolean getOver18() {
        return isOver18;
    }

    public void setOver18(Boolean over18) {
        isOver18 = over18;
    }

    public String getSignatureData() {
        return signatureData;
    }

    public void setSignatureData(String signatureData) {
        this.signatureData = signatureData;
    }

    public String getProfilePictureUrl() {
        return profilePictureUrl;
    }

    public void setProfilePictureUrl(String profilePictureUrl) {
        this.profilePictureUrl = profilePictureUrl;
    }

    public Membership getMembership() {
        return membership;
    }

    public void setMembership(Membership membership) {
        this.membership = membership;
    }
}
