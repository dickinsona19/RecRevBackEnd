package com.BossLiftingClub.BossLifting.Security;

import com.BossLiftingClub.BossLifting.Client.Client;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.Collections;

/**
 * UserPrincipal implements UserDetails for Spring Security
 * Currently supports Client authentication, can be extended for Staff and Member roles
 */
public class UserPrincipal implements UserDetails {
    
    private final Client client;
    private final String role;
    
    public UserPrincipal(Client client) {
        this.client = client;
        this.role = "CLIENT"; // Default role for clients
    }
    
    public UserPrincipal(Client client, String role) {
        this.client = client;
        this.role = role;
    }
    
    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return Collections.singletonList(new SimpleGrantedAuthority("ROLE_" + role));
    }
    
    @Override
    public String getPassword() {
        return client.getPassword();
    }
    
    @Override
    public String getUsername() {
        return client.getEmail();
    }
    
    @Override
    public boolean isAccountNonExpired() {
        return true;
    }
    
    @Override
    public boolean isAccountNonLocked() {
        return "ACTIVE".equalsIgnoreCase(client.getStatus()) || client.getStatus() == null;
    }
    
    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }
    
    @Override
    public boolean isEnabled() {
        return isAccountNonLocked();
    }
    
    public Client getClient() {
        return client;
    }
    
    public Integer getClientId() {
        return client.getId();
    }
    
    public String getRole() {
        return role;
    }
}

