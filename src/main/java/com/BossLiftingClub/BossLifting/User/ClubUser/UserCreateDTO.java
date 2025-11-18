package com.BossLiftingClub.BossLifting.User.ClubUser;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;
import java.util.List;

public class UserCreateDTO {
    @NotBlank(message = "First name is required")
    private String firstName;
    
    @NotBlank(message = "Last name is required")
    private String lastName;
    
    @NotBlank(message = "Email is required")
    @Email(message = "Please provide a valid email address")
    private String email;
    
    private String paymentMethodId; // Stripe payment method token

    // Deprecated: Use memberships instead
    @Deprecated
    private Long membershipId;

    @Valid
    @NotNull(message = "Club membership is required")
    private ClubMembershipDTO clubMembership;

    // New field for multiple memberships
    private List<MembershipRequestDTO> memberships;

    public static class ClubMembershipDTO {
        @NotNull(message = "Club ID is required")
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

    public static class MembershipRequestDTO {
        private Long membershipId;
        private String status;
        private LocalDateTime anchorDate;
        private String stripeSubscriptionId;

        public Long getMembershipId() { return membershipId; }
        public void setMembershipId(Long membershipId) { this.membershipId = membershipId; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public LocalDateTime getAnchorDate() { return anchorDate; }
        public void setAnchorDate(LocalDateTime anchorDate) { this.anchorDate = anchorDate; }
        public String getStripeSubscriptionId() { return stripeSubscriptionId; }
        public void setStripeSubscriptionId(String stripeSubscriptionId) { this.stripeSubscriptionId = stripeSubscriptionId; }
    }

    // Getters and setters
    public String getFirstName() { return firstName; }
    public void setFirstName(String firstName) { this.firstName = firstName; }
    public String getLastName() { return lastName; }
    public void setLastName(String lastName) { this.lastName = lastName; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getPaymentMethodId() { return paymentMethodId; }
    public void setPaymentMethodId(String paymentMethodId) { this.paymentMethodId = paymentMethodId; }

    @Deprecated
    public Long getMembershipId() { return membershipId; }
    @Deprecated
    public void setMembershipId(Long membershipId) { this.membershipId = membershipId; }

    public ClubMembershipDTO getClubMembership() { return clubMembership; }
    public void setClubMembership(ClubMembershipDTO clubMembership) { this.clubMembership = clubMembership; }

    public List<MembershipRequestDTO> getMemberships() { return memberships; }
    public void setMemberships(List<MembershipRequestDTO> memberships) { this.memberships = memberships; }
}