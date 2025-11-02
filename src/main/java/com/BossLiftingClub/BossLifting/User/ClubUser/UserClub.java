package com.BossLiftingClub.BossLifting.User.ClubUser;


import com.BossLiftingClub.BossLifting.Club.Club;
import com.BossLiftingClub.BossLifting.User.Membership.Membership;
import com.BossLiftingClub.BossLifting.User.User;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Entity
@Table(name = "user_clubs")
public class UserClub {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "club_id", nullable = false)
    private Club club;

    // Deprecated: Kept for backward compatibility, use userClubMemberships instead
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "membership_id")
    @Deprecated
    private Membership membership;

    @OneToMany(mappedBy = "userClub", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    private List<UserClubMembership> userClubMemberships = new ArrayList<>();

    @Column(name = "stripe_id")
    private String stripeId;

    @Column(name = "status")
    private String status;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public UserClub() {
        this.createdAt = LocalDateTime.now();
    }

    public UserClub(User user, Club club, Membership membership, String stripeId, String status) {
        this.user = user;
        this.club = club;
        this.membership = membership;
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

    public Club getClub() {
        return club;
    }

    public void setClub(Club club) {
        this.club = club;
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

    public Membership getMembership() {
        return membership;
    }

    public void setMembership(Membership membership) {
        this.membership = membership;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public List<UserClubMembership> getUserClubMemberships() {
        return userClubMemberships;
    }

    public void setUserClubMemberships(List<UserClubMembership> userClubMemberships) {
        this.userClubMemberships = userClubMemberships;
    }

    public void addMembership(UserClubMembership membership) {
        userClubMemberships.add(membership);
        membership.setUserClub(this);
    }

    public void removeMembership(UserClubMembership membership) {
        userClubMemberships.remove(membership);
        membership.setUserClub(null);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        UserClub userClub = (UserClub) o;
        return user != null && club != null && user.equals(userClub.user) && club.equals(userClub.club);
    }

    @Override
    public int hashCode() {
        return Objects.hash(user, club);
    }
}