package com.BossLiftingClub.BossLifting.Client;

import com.BossLiftingClub.BossLifting.Business.BusinessDTO;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Data
public class ClientDTO {
    private Integer id;
    
    @Email(message = "Please provide a valid email address")
    @NotBlank(message = "Email is required")
    private String email;
    
    @NotBlank(message = "Password is required")
    private String password;
    private LocalDateTime createdAt;
    private String status;
    private Set<BusinessDTO> businesses;
    private Set<BusinessDTO> clubs; // Backward compatibility

    // Getters and Setters
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

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Set<BusinessDTO> getBusinesses() {
        return businesses;
    }

    public void setBusinesses(Set<BusinessDTO> businesses) {
        this.businesses = businesses;
        this.clubs = businesses; // Backward compatibility
    }

    // Backward compatibility getters/setters
    @Deprecated
    public Set<BusinessDTO> getClubs() {
        return businesses != null ? businesses : clubs;
    }

    @Deprecated
    public void setClubs(Set<BusinessDTO> clubs) {
        this.businesses = clubs;
        this.clubs = clubs;
    }
}