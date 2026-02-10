package com.BossLiftingClub.BossLifting.User.Membership;

import com.BossLiftingClub.BossLifting.User.BusinessUser.UserBusinessMembership;
import jakarta.persistence.*;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "punch_card_scans")
@NoArgsConstructor
@AllArgsConstructor
public class PunchCardScan {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_business_membership_id", nullable = false)
    private UserBusinessMembership userBusinessMembership;

    @Column(name = "scanned_at", nullable = false)
    private LocalDateTime scannedAt = LocalDateTime.now();

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public UserBusinessMembership getUserBusinessMembership() {
        return userBusinessMembership;
    }

    public void setUserBusinessMembership(UserBusinessMembership userBusinessMembership) {
        this.userBusinessMembership = userBusinessMembership;
    }

    public LocalDateTime getScannedAt() {
        return scannedAt;
    }

    public void setScannedAt(LocalDateTime scannedAt) {
        this.scannedAt = scannedAt;
    }
}
