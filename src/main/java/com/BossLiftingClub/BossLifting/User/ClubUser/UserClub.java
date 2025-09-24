package com.BossLiftingClub.BossLifting.User.ClubUser;


import com.BossLiftingClub.BossLifting.Club.Club;
import com.BossLiftingClub.BossLifting.User.User;
import jakarta.persistence.*;

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

    @Column(name = "stripe_id")
    private String stripeId;

    @Column(name = "status")
    private String status;

    public UserClub() {
    }

    public UserClub(User user, Club club, String stripeId, String status) {
        this.user = user;
        this.club = club;
        this.stripeId = stripeId;
        this.status = status;
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