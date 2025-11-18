package com.BossLiftingClub.BossLifting.Security;

import com.BossLiftingClub.BossLifting.Club.Staff.Staff;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.Collections;

/**
 * StaffPrincipal implements UserDetails for Spring Security
 * Used for Staff authentication with role-based access control
 */
public class StaffPrincipal implements UserDetails {
    
    private final Staff staff;
    private final String role;
    
    public StaffPrincipal(Staff staff) {
        this.staff = staff;
        this.role = staff.getRole() != null ? staff.getRole() : "TEAM_MEMBER";
    }
    
    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return Collections.singletonList(new SimpleGrantedAuthority("ROLE_STAFF_" + role));
    }
    
    @Override
    public String getPassword() {
        return staff.getPassword();
    }
    
    @Override
    public String getUsername() {
        return staff.getEmail();
    }
    
    @Override
    public boolean isAccountNonExpired() {
        return true;
    }
    
    @Override
    public boolean isAccountNonLocked() {
        return staff.getIsActive() != null && staff.getIsActive();
    }
    
    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }
    
    @Override
    public boolean isEnabled() {
        return isAccountNonLocked();
    }
    
    public Staff getStaff() {
        return staff;
    }
    
    public Integer getStaffId() {
        return staff.getId();
    }
    
    public String getRole() {
        return role;
    }
    
    public Long getBusinessId() {
        return staff.getBusiness() != null ? staff.getBusiness().getId() : null;
    }
}

