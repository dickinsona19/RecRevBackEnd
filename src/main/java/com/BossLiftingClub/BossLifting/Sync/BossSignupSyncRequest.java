package com.BossLiftingClub.BossLifting.Sync;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Request from Boss-Lifting-Club-API when a member signs up.
 * Creates a corresponding member in RecRev with the same entryQrcodeToken.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class BossSignupSyncRequest {

    private String firstName;
    private String lastName;
    private String email;
    private String phoneNumber;
    private String entryQrcodeToken;
    private String userStripeMemberId;
    private String membershipName;
    private String lockedInRate;

    public String getFirstName() { return firstName; }
    public void setFirstName(String firstName) { this.firstName = firstName; }
    public String getLastName() { return lastName; }
    public void setLastName(String lastName) { this.lastName = lastName; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getPhoneNumber() { return phoneNumber; }
    public void setPhoneNumber(String phoneNumber) { this.phoneNumber = phoneNumber; }
    public String getEntryQrcodeToken() { return entryQrcodeToken; }
    public void setEntryQrcodeToken(String entryQrcodeToken) { this.entryQrcodeToken = entryQrcodeToken; }
    public String getUserStripeMemberId() { return userStripeMemberId; }
    public void setUserStripeMemberId(String userStripeMemberId) { this.userStripeMemberId = userStripeMemberId; }
    public String getMembershipName() { return membershipName; }
    public void setMembershipName(String membershipName) { this.membershipName = membershipName; }
    public String getLockedInRate() { return lockedInRate; }
    public void setLockedInRate(String lockedInRate) { this.lockedInRate = lockedInRate; }
}
