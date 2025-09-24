package com.BossLiftingClub.BossLifting.User;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import com.BossLiftingClub.BossLifting.User.SignInLog.SignInLogDto;
import com.BossLiftingClub.BossLifting.User.UserTitles.UserTitles;
import com.BossLiftingClub.BossLifting.User.Membership.Membership;

public class UserDTO {

    private Long id;
    private String firstName;
    private String lastName;
    private String phoneNumber;
    private Boolean isInGoodStanding;
    private String entryQrcodeToken;
    private String userStripeMemberId;
    private String referralCode;
    private Set<ReferredUserDto> referredMembersDto;
    private LocalDateTime createdAt;
    private UserTitles userTitles;
    private Boolean isOver18;
    private String lockedInRate;
    private String signatureData;
    private LocalDateTime waiverSignedDate;
    private String profilePictureUrl;
    private Long parentId;
    private Set<UserDTO> childrenDto;
    private Long referredById;
    private Membership membership;
    private Set<SignInLogDto> signInLogs;
    private List<String> clubTags;
    public UserDTO() {}

    public UserDTO(User user) {
        this.id = user.getId();
        this.firstName = user.getFirstName();
        this.lastName = user.getLastName();
        this.phoneNumber = user.getPhoneNumber();
        this.isInGoodStanding = user.getIsInGoodStanding();
        this.entryQrcodeToken = user.getEntryQrcodeToken();
        this.userStripeMemberId = user.getUserStripeMemberId();
        this.referralCode = user.getReferralCode();
        this.referredMembersDto = user.getReferredMembersDto();  // safe: this avoids the LOBs
        this.createdAt = user.getCreatedAt();
        this.userTitles = user.getUserTitles();
        this.isOver18 = user.isOver18();
        this.lockedInRate = user.getLockedInRate();
        this.signatureData = user.getSignatureData();
        this.waiverSignedDate = user.getWaiverSignedDate();
        this.profilePictureUrl = user.getProfilePictureUrl();
        this.parentId = user.getParent() != null ? user.getParent().getId() : null;
        this.childrenDto = user.getChildrenDto();
        this.referredById = user.getReferredBy() != null ? user.getReferredBy().getId() : null;
        this.membership = user.getMembership();
        if (user.getSignInLogs() != null) {
            this.signInLogs = user.getSignInLogs().stream()
                    .map(SignInLogDto::new)
                    .collect(Collectors.toSet());
        }
        this.clubTags = user.getClubTags();
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

    public Set<SignInLogDto> getSignInLogs() {
        return signInLogs;
    }
    public void setSignInLogs(Set<SignInLogDto> signInLogs) {
        this.signInLogs = signInLogs;
    }

    public String getPhoneNumber() {
        return phoneNumber;
    }

    public void setPhoneNumber(String phoneNumber) {
        this.phoneNumber = phoneNumber;
    }

    public Boolean getIsInGoodStanding() {
        return isInGoodStanding;
    }

    public void setIsInGoodStanding(Boolean isInGoodStanding) {
        this.isInGoodStanding = isInGoodStanding;
    }

    public String getEntryQrcodeToken() {
        return entryQrcodeToken;
    }

    public void setEntryQrcodeToken(String entryQrcodeToken) {
        this.entryQrcodeToken = entryQrcodeToken;
    }

    public String getUserStripeMemberId() {
        return userStripeMemberId;
    }

    public void setUserStripeMemberId(String userStripeMemberId) {
        this.userStripeMemberId = userStripeMemberId;
    }

    public String getReferralCode() {
        return referralCode;
    }

    public void setReferralCode(String referralCode) {
        this.referralCode = referralCode;
    }

    public Set<ReferredUserDto> getReferredMembersDto() {
        return referredMembersDto;
    }

    public void setReferredMembersDto(Set<ReferredUserDto> referredMembersDto) {
        this.referredMembersDto = referredMembersDto;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public UserTitles getUserTitles() {
        return userTitles;
    }

    public void setUserTitles(UserTitles userTitles) {
        this.userTitles = userTitles;
    }

    public Boolean isOver18() {
        return isOver18;
    }

    public void setIsOver18(Boolean isOver18) {
        this.isOver18 = isOver18;
    }

    public String isLockedInRate() {
        return lockedInRate;
    }

    public void setLockedInRate(String lockedInRate) {
        this.lockedInRate = lockedInRate;
    }

    public String getSignatureData() {
        return signatureData;
    }

    public void setSignatureData(String signatureData) {
        this.signatureData = signatureData;
    }

    public LocalDateTime getWaiverSignedDate() {
        return waiverSignedDate;
    }

    public void setWaiverSignedDate(LocalDateTime waiverSignedDate) {
        this.waiverSignedDate = waiverSignedDate;
    }

    public String getProfilePictureUrl() {
        return profilePictureUrl;
    }

    public void setProfilePictureUrl(String profilePictureUrl) {
        this.profilePictureUrl = profilePictureUrl;
    }

    public Long getParentId() {
        return parentId;
    }

    public void setParentId(Long parentId) {
        this.parentId = parentId;
    }

    public Set<UserDTO> getChildrenDto() {
        return childrenDto;
    }

    public void setChildrenDto(Set<UserDTO> childrenDto) {
        this.childrenDto = childrenDto;
    }

    public Long getReferredById() {
        return referredById;
    }

    public void setReferredById(Long referredById) {
        this.referredById = referredById;
    }

    public Membership getMembership() {
        return membership;
    }

    public void setMembership(Membership membership) {
        this.membership = membership;
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

    public String getLockedInRate() {
        return lockedInRate;
    }

    public List<String> getClubTags() {
        return clubTags;
    }

    public void setClubTags(List<String> clubTags) {
        this.clubTags = clubTags;
    }
}