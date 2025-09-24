package com.BossLiftingClub.BossLifting.User.ClubUser;

public class UserCreateDTO {
    private String firstName;
    private String lastName;
    private String email;
    private Long membershipId;
    private ClubMembershipDTO clubMembership;

    public static class ClubMembershipDTO {
        private Long clubId;
        private String stripeId;
        private String status;

        public Long getClubId() { return clubId; }
        public void setClubId(Long clubId) { this.clubId = clubId; }
        public String getStripeId() { return stripeId; }
        public void setStripeId(String stripeId) { this.stripeId = stripeId; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
    }

    // Getters and setters
    public String getFirstName() { return firstName; }
    public void setFirstName(String firstName) { this.firstName = firstName; }
    public String getLastName() { return lastName; }
    public void setLastName(String lastName) { this.lastName = lastName; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public Long getMembershipId() { return membershipId; }
    public void setMembershipId(Long membershipId) { this.membershipId = membershipId; }
    public ClubMembershipDTO getClubMembership() { return clubMembership; }
    public void setClubMembership(ClubMembershipDTO clubMembership) { this.clubMembership = clubMembership; }
}