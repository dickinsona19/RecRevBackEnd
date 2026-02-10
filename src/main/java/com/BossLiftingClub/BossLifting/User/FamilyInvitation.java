package com.BossLiftingClub.BossLifting.User;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "family_invitations")
public class FamilyInvitation {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "primary_owner_id", nullable = false)
    private User primaryOwner;

    @Column(name = "invited_email", nullable = false)
    private String invitedEmail;

    @Column(name = "invited_first_name")
    private String invitedFirstName;

    @Column(name = "invited_last_name")
    private String invitedLastName;

    @Column(name = "status", nullable = false)
    @Enumerated(EnumType.STRING)
    private InvitationStatus status = InvitationStatus.PENDING;

    @Column(name = "membership_id")
    private Long membershipId;

    @Column(name = "custom_price", precision = 10, scale = 2)
    private java.math.BigDecimal customPrice;

    @Column(name = "business_tag", nullable = false)
    private String businessTag;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "accepted_at")
    private LocalDateTime acceptedAt;

    @Column(name = "user_id")
    private Long userId; // Set when invitation is accepted

    public enum InvitationStatus {
        PENDING,
        ACCEPTED,
        EXPIRED,
        CANCELLED
    }

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public User getPrimaryOwner() {
        return primaryOwner;
    }

    public void setPrimaryOwner(User primaryOwner) {
        this.primaryOwner = primaryOwner;
    }

    public String getInvitedEmail() {
        return invitedEmail;
    }

    public void setInvitedEmail(String invitedEmail) {
        this.invitedEmail = invitedEmail;
    }

    public String getInvitedFirstName() {
        return invitedFirstName;
    }

    public void setInvitedFirstName(String invitedFirstName) {
        this.invitedFirstName = invitedFirstName;
    }

    public String getInvitedLastName() {
        return invitedLastName;
    }

    public void setInvitedLastName(String invitedLastName) {
        this.invitedLastName = invitedLastName;
    }

    public InvitationStatus getStatus() {
        return status;
    }

    public void setStatus(InvitationStatus status) {
        this.status = status;
    }

    public Long getMembershipId() {
        return membershipId;
    }

    public void setMembershipId(Long membershipId) {
        this.membershipId = membershipId;
    }

    public java.math.BigDecimal getCustomPrice() {
        return customPrice;
    }

    public void setCustomPrice(java.math.BigDecimal customPrice) {
        this.customPrice = customPrice;
    }

    public String getBusinessTag() {
        return businessTag;
    }

    public void setBusinessTag(String businessTag) {
        this.businessTag = businessTag;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getAcceptedAt() {
        return acceptedAt;
    }

    public void setAcceptedAt(LocalDateTime acceptedAt) {
        this.acceptedAt = acceptedAt;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }
}
