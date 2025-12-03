package com.BossLiftingClub.BossLifting.Business.Staff;

import com.BossLiftingClub.BossLifting.Business.Business;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "staff")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Staff {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "first_name")
    private String firstName;

    @Column(name = "last_name")
    private String lastName;

    @Column
    private String email;

    @Column
    private String password;

    @Column
    private String role; // ADMIN, MANAGER, STAFF (replaces type)

    @Column(name = "type") // Backward compatibility
    @Deprecated
    private String type;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "business_id")
    private Business business;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "club_id") // Backward compatibility
    @Deprecated
    private Business club;

    @Column(name = "invite_token")
    private String inviteToken;

    @Column(name = "invite_token_expiry")
    private LocalDateTime inviteTokenExpiry;

    @Column(name = "is_active")
    private Boolean isActive = true;

    @Column(name = "invited_by")
    private Integer invitedBy; // Client ID who invited

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "last_login_at")
    private LocalDateTime lastLoginAt;


    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }
}
