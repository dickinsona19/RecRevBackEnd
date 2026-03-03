package com.BossLiftingClub.BossLifting.Migration;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * Input DTO for a member being migrated. Matches the JSON structure from the source system.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class MigrationMemberInput {

    private Long id;
    private String firstName;
    private String lastName;
    private String password;
    private String phoneNumber;
    private Boolean isInGoodStanding;
    private String createdAt;
    private String entryQrcodeToken;
    private String userStripeMemberId;
    private UserTitleInput userTitles;
    private String lockedInRate;
    private String signatureData;
    private String waiverSignedDate;
    private String profilePictureUrl;
    private String referralCode;
    private List<MigrationMemberInput> childrenDto;
    private MigrationMemberInput referredBy;  // Nested object from JSON - flattened to get referrer
    private Long referredById;  // Set during flatten for linking
    private MembershipInput membership;
    private Boolean over18;
    private Boolean inGoodStanding;
    private Long parentId; // For parent-child linking (from source id)

    public static class UserTitleInput {
        private Long id;
        private String title;

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }
    }

    public static class MembershipInput {
        private Long id;
        private String name;
        private String price;
        private String chargeInterval;

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getPrice() { return price; }
        public void setPrice(String price) { this.price = price; }
        public String getChargeInterval() { return chargeInterval; }
        public void setChargeInterval(String chargeInterval) { this.chargeInterval = chargeInterval; }
    }

    // Getters and setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getFirstName() { return firstName; }
    public void setFirstName(String firstName) { this.firstName = firstName; }
    public String getLastName() { return lastName; }
    public void setLastName(String lastName) { this.lastName = lastName; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
    public String getPhoneNumber() { return phoneNumber; }
    public void setPhoneNumber(String phoneNumber) { this.phoneNumber = phoneNumber; }
    public Boolean getIsInGoodStanding() { return isInGoodStanding != null ? isInGoodStanding : (inGoodStanding != null ? inGoodStanding : false); }
    public void setIsInGoodStanding(Boolean isInGoodStanding) { this.isInGoodStanding = isInGoodStanding; }
    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }
    public String getEntryQrcodeToken() { return entryQrcodeToken; }
    public void setEntryQrcodeToken(String entryQrcodeToken) { this.entryQrcodeToken = entryQrcodeToken; }
    public String getUserStripeMemberId() { return userStripeMemberId; }
    public void setUserStripeMemberId(String userStripeMemberId) { this.userStripeMemberId = userStripeMemberId; }
    public UserTitleInput getUserTitles() { return userTitles; }
    public void setUserTitles(UserTitleInput userTitles) { this.userTitles = userTitles; }
    public String getLockedInRate() { return lockedInRate; }
    public void setLockedInRate(String lockedInRate) { this.lockedInRate = lockedInRate; }
    public String getSignatureData() { return signatureData; }
    public void setSignatureData(String signatureData) { this.signatureData = signatureData; }
    public String getWaiverSignedDate() { return waiverSignedDate; }
    public void setWaiverSignedDate(String waiverSignedDate) { this.waiverSignedDate = waiverSignedDate; }
    public String getProfilePictureUrl() { return profilePictureUrl; }
    public void setProfilePictureUrl(String profilePictureUrl) { this.profilePictureUrl = profilePictureUrl; }
    public String getReferralCode() { return referralCode; }
    public void setReferralCode(String referralCode) { this.referralCode = referralCode; }
    public List<MigrationMemberInput> getChildrenDto() { return childrenDto; }
    public void setChildrenDto(List<MigrationMemberInput> childrenDto) { this.childrenDto = childrenDto; }
    public MigrationMemberInput getReferredBy() { return referredBy; }
    public void setReferredBy(MigrationMemberInput referredBy) { this.referredBy = referredBy; }
    public Long getReferredById() { return referredById; }
    public void setReferredById(Long referredById) { this.referredById = referredById; }
    public MembershipInput getMembership() { return membership; }
    public void setMembership(MembershipInput membership) { this.membership = membership; }
    public Boolean getOver18() { return over18 != null ? over18 : false; }
    public void setOver18(Boolean over18) { this.over18 = over18; }
    public Boolean getInGoodStanding() { return inGoodStanding; }
    public void setInGoodStanding(Boolean inGoodStanding) { this.inGoodStanding = inGoodStanding; }
    public Long getParentId() { return parentId; }
    public void setParentId(Long parentId) { this.parentId = parentId; }
}
