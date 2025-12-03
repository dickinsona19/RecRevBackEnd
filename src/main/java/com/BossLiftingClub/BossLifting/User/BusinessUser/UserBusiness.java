package com.BossLiftingClub.BossLifting.User.BusinessUser;

import com.BossLiftingClub.BossLifting.Business.Business;
import com.BossLiftingClub.BossLifting.User.User;
import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Entity
@Table(name = "user_business")
public class UserBusiness {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "business_id", nullable = false)
    private Business business;

    @OneToMany(mappedBy = "userBusiness", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    private List<UserBusinessMembership> userBusinessMemberships = new ArrayList<>();

    @OneToMany(mappedBy = "userBusiness", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<MemberLog> memberLogs = new ArrayList<>();

    @Column(name = "stripe_id")
    private String stripeId;

    @Column(name = "status")
    private String status;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public UserBusiness() {
        this.createdAt = LocalDateTime.now();
    }

    public UserBusiness(User user, Business business, String stripeId, String status) {
        this.user = user;
        this.business = business;
        this.stripeId = stripeId;
        this.status = status;
        this.createdAt = LocalDateTime.now();
    }

    // Getters and setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public Business getBusiness() {
        return business;
    }

    public void setBusiness(Business business) {
        this.business = business;
    }

    public String getStripeId() {
        return stripeId;
    }

    public void setStripeId(String stripeId) {
        this.stripeId = stripeId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public List<UserBusinessMembership> getUserBusinessMemberships() {
        return userBusinessMemberships;
    }

    public void setUserBusinessMemberships(List<UserBusinessMembership> userBusinessMemberships) {
        this.userBusinessMemberships = userBusinessMemberships;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public void addMembership(UserBusinessMembership membership) {
        userBusinessMemberships.add(membership);
        membership.setUserBusiness(this);
    }

    public void removeMembership(UserBusinessMembership membership) {
        userBusinessMemberships.remove(membership);
        membership.setUserBusiness(null);
    }

    public List<MemberLog> getMemberLogs() {
        return memberLogs;
    }

    public void setMemberLogs(List<MemberLog> memberLogs) {
        this.memberLogs = memberLogs;
    }

    public void addMemberLog(MemberLog log) {
        memberLogs.add(log);
        log.setUserBusiness(this);
    }

    public void removeMemberLog(MemberLog log) {
        memberLogs.remove(log);
        log.setUserBusiness(null);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        UserBusiness that = (UserBusiness) o;
        return user != null && business != null && user.equals(that.user) && business.equals(that.business);
    }

    @Override
    public int hashCode() {
        return Objects.hash(user, business);
    }
}
