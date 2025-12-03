package com.BossLiftingClub.BossLifting.User.BusinessUser;

import com.BossLiftingClub.BossLifting.User.Membership.Membership;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Junction table entity representing the relationship between a UserBusiness and a Membership.
 * This allows a user to have multiple memberships within a single business.
 */
@Entity
@Table(name = "user_business_membership")
public class UserBusinessMembership {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_business_id", nullable = false)
    @JsonIgnore
    private UserBusiness userBusiness;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "membership_id", nullable = false)
    private Membership membership;

    @Column(name = "status", nullable = false)
    private String status; // e.g., "ACTIVE", "INACTIVE", "CANCELLED", "PENDING"

    @Column(name = "anchor_date", nullable = false)
    private LocalDateTime anchorDate; // The date when the membership starts/renews

    @Column(name = "end_date")
    private LocalDateTime endDate; // Optional: when the membership ends

    @Column(name = "stripe_subscription_id")
    private String stripeSubscriptionId;

    @Column(name = "pause_start_date")
    private LocalDateTime pauseStartDate;

    @Column(name = "pause_end_date")
    private LocalDateTime pauseEndDate;

    @Column(name = "actual_price", nullable = false)
    private BigDecimal actualPrice = BigDecimal.ZERO;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    // Constructors
    public UserBusinessMembership() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    public UserBusinessMembership(UserBusiness userBusiness, Membership membership, String status, LocalDateTime anchorDate) {
        this.userBusiness = userBusiness;
        this.membership = membership;
        this.status = status;
        this.anchorDate = anchorDate;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    @PrePersist
    public void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public UserBusiness getUserBusiness() {
        return userBusiness;
    }

    public void setUserBusiness(UserBusiness userBusiness) {
        this.userBusiness = userBusiness;
    }

    public Membership getMembership() {
        return membership;
    }

    public void setMembership(Membership membership) {
        this.membership = membership;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public LocalDateTime getAnchorDate() {
        return anchorDate;
    }

    public void setAnchorDate(LocalDateTime anchorDate) {
        this.anchorDate = anchorDate;
    }

    public LocalDateTime getEndDate() {
        return endDate;
    }

    public void setEndDate(LocalDateTime endDate) {
        this.endDate = endDate;
    }

    public String getStripeSubscriptionId() {
        return stripeSubscriptionId;
    }

    public void setStripeSubscriptionId(String stripeSubscriptionId) {
        this.stripeSubscriptionId = stripeSubscriptionId;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public LocalDateTime getPauseStartDate() {
        return pauseStartDate;
    }

    public void setPauseStartDate(LocalDateTime pauseStartDate) {
        this.pauseStartDate = pauseStartDate;
    }

    public LocalDateTime getPauseEndDate() {
        return pauseEndDate;
    }

    public void setPauseEndDate(LocalDateTime pauseEndDate) {
        this.pauseEndDate = pauseEndDate;
    }

    public BigDecimal getActualPrice() {
        return actualPrice;
    }

    public void setActualPrice(BigDecimal actualPrice) {
        this.actualPrice = actualPrice;
    }

    @PreUpdate
    public void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
