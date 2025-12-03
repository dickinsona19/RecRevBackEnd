package com.BossLiftingClub.BossLifting.Security;

import com.BossLiftingClub.BossLifting.Client.Client;
import com.BossLiftingClub.BossLifting.Business.Staff.Staff;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Utility class for security-related operations
 */
public class SecurityUtils {
    
    /**
     * Get the currently authenticated client
     * @return Client object or null if not authenticated or not a client
     */
    public static Client getCurrentClient() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        
        if (authentication != null && authentication.getPrincipal() instanceof UserPrincipal) {
            UserPrincipal userPrincipal = (UserPrincipal) authentication.getPrincipal();
            return userPrincipal.getClient();
        }
        
        return null;
    }
    
    /**
     * Get the currently authenticated staff
     * @return Staff object or null if not authenticated or not staff
     */
    public static Staff getCurrentStaff() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        
        if (authentication != null && authentication.getPrincipal() instanceof StaffPrincipal) {
            StaffPrincipal staffPrincipal = (StaffPrincipal) authentication.getPrincipal();
            return staffPrincipal.getStaff();
        }
        
        return null;
    }
    
    /**
     * Get the current user type (CLIENT or STAFF)
     * @return User type or null if not authenticated
     */
    public static String getCurrentUserType() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        
        if (authentication != null && authentication.getPrincipal() instanceof UserPrincipal) {
            return "CLIENT";
        } else if (authentication != null && authentication.getPrincipal() instanceof StaffPrincipal) {
            return "STAFF";
        }
        
        return null;
    }
    
    /**
     * Get the current user's role
     * @return Role (CLIENT, ADMIN, MANAGER, TEAM_MEMBER) or null if not authenticated
     */
    public static String getCurrentUserRole() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        
        if (authentication != null && authentication.getPrincipal() instanceof UserPrincipal) {
            return "CLIENT";
        } else if (authentication != null && authentication.getPrincipal() instanceof StaffPrincipal) {
            StaffPrincipal staffPrincipal = (StaffPrincipal) authentication.getPrincipal();
            return staffPrincipal.getRole();
        }
        
        return null;
    }
    
    /**
     * Get the current client ID
     * @return Client ID or null if not authenticated
     */
    public static Integer getCurrentClientId() {
        Client client = getCurrentClient();
        return client != null ? client.getId() : null;
    }
    
    /**
     * Get the current staff ID
     * @return Staff ID or null if not authenticated or not staff
     */
    public static Integer getCurrentStaffId() {
        Staff staff = getCurrentStaff();
        return staff != null ? staff.getId() : null;
    }
    
    /**
     * Check if the current user is authenticated
     * @return true if authenticated, false otherwise
     */
    public static boolean isAuthenticated() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null && authentication.isAuthenticated() 
            && !"anonymousUser".equals(authentication.getPrincipal().toString());
    }
    
    /**
     * Check if the current user has a specific role
     * @param role The role to check (e.g., "CLIENT", "STAFF", "ADMIN")
     * @return true if user has the role, false otherwise
     */
    public static boolean hasRole(String role) {
        String currentRole = getCurrentUserRole();
        return currentRole != null && currentRole.equalsIgnoreCase(role);
    }
    
    /**
     * Check if the current user has a specific permission
     * @param permission The permission to check
     * @return true if user has the permission, false otherwise
     */
    public static boolean hasPermission(String permission) {
        String role = getCurrentUserRole();
        if (role == null) {
            return false;
        }
        return Permissions.hasPermission(role, permission);
    }
}

