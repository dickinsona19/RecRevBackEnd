package com.BossLiftingClub.BossLifting.Security;

/**
 * Permission constants for role-based access control
 */
public class Permissions {
    
    // Member Management
    public static final String MEMBER_VIEW = "member:view";
    public static final String MEMBER_CREATE = "member:create";
    public static final String MEMBER_EDIT = "member:edit";
    public static final String MEMBER_DELETE = "member:delete";
    
    // Membership Management
    public static final String MEMBERSHIP_VIEW = "membership:view";
    public static final String MEMBERSHIP_CREATE = "membership:create";
    public static final String MEMBERSHIP_EDIT = "membership:edit";
    public static final String MEMBERSHIP_DELETE = "membership:delete";
    public static final String MEMBERSHIP_ASSIGN = "membership:assign";
    
    // Analytics
    public static final String ANALYTICS_VIEW_SIMPLE = "analytics:view_simple";
    public static final String ANALYTICS_VIEW_REVENUE = "analytics:view_revenue";
    
    // Staff Management
    public static final String STAFF_VIEW = "staff:view";
    public static final String STAFF_MANAGE = "staff:manage";
    public static final String STAFF_DELETE = "staff:delete";
    
    // Products
    public static final String PRODUCT_VIEW = "product:view";
    public static final String PRODUCT_MANAGE = "product:manage";
    
    // Payments
    public static final String PAYMENT_VIEW_HISTORY = "payment:view_history";
    public static final String PAYMENT_PROCESS = "payment:process";
    public static final String PAYMENT_REFUND = "payment:refund";
    
    // Stripe
    public static final String STRIPE_ONBOARDING_MANAGE = "stripe:onboarding_manage";
    
    /**
     * Get permissions for a role
     */
    public static java.util.Set<String> getPermissionsForRole(String role) {
        java.util.Set<String> permissions = new java.util.HashSet<>();
        
        if (role == null) {
            return permissions;
        }
        
        String upperRole = role.toUpperCase();
        
        switch (upperRole) {
            case "ADMIN":
                // ADMIN has all permissions
                permissions.add(MEMBER_VIEW);
                permissions.add(MEMBER_CREATE);
                permissions.add(MEMBER_EDIT);
                permissions.add(MEMBER_DELETE);
                permissions.add(MEMBERSHIP_VIEW);
                permissions.add(MEMBERSHIP_CREATE);
                permissions.add(MEMBERSHIP_EDIT);
                permissions.add(MEMBERSHIP_DELETE);
                permissions.add(MEMBERSHIP_ASSIGN);
                permissions.add(ANALYTICS_VIEW_SIMPLE);
                permissions.add(ANALYTICS_VIEW_REVENUE);
                permissions.add(STAFF_VIEW);
                permissions.add(STAFF_MANAGE);
                permissions.add(STAFF_DELETE);
                permissions.add(PRODUCT_VIEW);
                permissions.add(PRODUCT_MANAGE);
                permissions.add(PAYMENT_VIEW_HISTORY);
                permissions.add(PAYMENT_PROCESS);
                permissions.add(PAYMENT_REFUND);
                permissions.add(STRIPE_ONBOARDING_MANAGE);
                break;
                
            case "MANAGER":
                // MANAGER has most permissions except revenue analytics
                permissions.add(MEMBER_VIEW);
                permissions.add(MEMBER_CREATE);
                permissions.add(MEMBER_EDIT);
                permissions.add(MEMBER_DELETE);
                permissions.add(MEMBERSHIP_VIEW);
                permissions.add(MEMBERSHIP_CREATE);
                permissions.add(MEMBERSHIP_EDIT);
                permissions.add(MEMBERSHIP_DELETE);
                permissions.add(MEMBERSHIP_ASSIGN);
                permissions.add(ANALYTICS_VIEW_SIMPLE); // Simple analytics only, no revenue
                permissions.add(STAFF_VIEW);
                permissions.add(STAFF_MANAGE);
                permissions.add(STAFF_DELETE);
                permissions.add(PRODUCT_VIEW);
                permissions.add(PRODUCT_MANAGE);
                permissions.add(PAYMENT_VIEW_HISTORY);
                permissions.add(PAYMENT_PROCESS);
                permissions.add(PAYMENT_REFUND);
                // No STRIPE_ONBOARDING_MANAGE for managers
                break;
                
            case "TEAM_MEMBER":
            case "STAFF":
                // TEAM_MEMBER has limited permissions
                permissions.add(MEMBER_VIEW);
                permissions.add(MEMBER_CREATE);
                permissions.add(MEMBER_EDIT);
                // No MEMBER_DELETE for team members
                permissions.add(MEMBERSHIP_VIEW);
                permissions.add(MEMBERSHIP_ASSIGN);
                // No CREATE/EDIT/DELETE memberships for team members
                permissions.add(ANALYTICS_VIEW_SIMPLE); // Simple analytics only
                // No staff management for team members
                permissions.add(PRODUCT_VIEW);
                // No product management for team members
                permissions.add(PAYMENT_VIEW_HISTORY);
                permissions.add(PAYMENT_PROCESS);
                // No refunds for team members
                break;
                
            case "CLIENT":
                // CLIENT (Owner) has all permissions like ADMIN
                permissions.add(MEMBER_VIEW);
                permissions.add(MEMBER_CREATE);
                permissions.add(MEMBER_EDIT);
                permissions.add(MEMBER_DELETE);
                permissions.add(MEMBERSHIP_VIEW);
                permissions.add(MEMBERSHIP_CREATE);
                permissions.add(MEMBERSHIP_EDIT);
                permissions.add(MEMBERSHIP_DELETE);
                permissions.add(MEMBERSHIP_ASSIGN);
                permissions.add(ANALYTICS_VIEW_SIMPLE);
                permissions.add(ANALYTICS_VIEW_REVENUE);
                permissions.add(STAFF_VIEW);
                permissions.add(STAFF_MANAGE);
                permissions.add(STAFF_DELETE);
                permissions.add(PRODUCT_VIEW);
                permissions.add(PRODUCT_MANAGE);
                permissions.add(PAYMENT_VIEW_HISTORY);
                permissions.add(PAYMENT_PROCESS);
                permissions.add(PAYMENT_REFUND);
                permissions.add(STRIPE_ONBOARDING_MANAGE);
                break;
        }
        
        return permissions;
    }
    
    /**
     * Check if a role has a specific permission
     */
    public static boolean hasPermission(String role, String permission) {
        return getPermissionsForRole(role).contains(permission);
    }
}

