package com.BossLiftingClub.BossLifting.Business.Staff;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class StaffDTO {
    private Integer id;
    private String firstName;
    private String lastName;
    private String email;
    private String password;
    private String role; // ADMIN, MANAGER, STAFF
    private String type; // Backward compatibility
    private Long businessId;
    private Long clubId; // Backward compatibility
    private String inviteToken;
    private LocalDateTime inviteTokenExpiry;
    private Boolean isActive;
    private Integer invitedBy;
    private LocalDateTime createdAt;
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

    public String getRole() {
        return role != null ? role : type; // Fallback to type for backward compatibility
    }

    public void setRole(String role) {
        this.role = role;
        this.type = role; // Set type for backward compatibility
    }

    // Backward compatibility getter/setter
    @Deprecated
    public String getType() {
        return role != null ? role : type;
    }

    @Deprecated
    public void setType(String type) {
        this.type = type;
        if (this.role == null) {
            this.role = type;
        }
    }
}
