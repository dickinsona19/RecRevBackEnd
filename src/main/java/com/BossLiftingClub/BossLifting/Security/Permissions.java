package com.BossLiftingClub.BossLifting.Security;

/**
 * Permission constants for role-based access control
 */
public class Permissions {
    
    // Member Management
    public static final String VIEW_MEMBERS = "VIEW_MEMBERS";
    public static final String CREATE_MEMBERS = "CREATE_MEMBERS";
    public static final String EDIT_MEMBERS = "EDIT_MEMBERS";
    public static final String DELETE_MEMBERS = "DELETE_MEMBERS";
    
    // Membership Management
    public static final String VIEW_MEMBERSHIPS = "VIEW_MEMBERSHIPS";
    public static final String CREATE_MEMBERSHIPS = "CREATE_MEMBERSHIPS";
    public static final String EDIT_MEMBERSHIPS = "EDIT_MEMBERSHIPS";
    public static final String DELETE_MEMBERSHIPS = "DELETE_MEMBERSHIPS";
    public static final String ASSIGN_MEMBERSHIPS = "ASSIGN_MEMBERSHIPS";
    
    // Analytics
    public static final String VIEW_ANALYTICS = "VIEW_ANALYTICS";
    public static final String VIEW_REVENUE = "VIEW_REVENUE";
    public static final String VIEW_SIMPLE_ANALYTICS = "VIEW_SIMPLE_ANALYTICS";
    
    // Staff Management
    public static final String VIEW_STAFF = "VIEW_STAFF";
    public static final String CREATE_STAFF = "CREATE_STAFF";
    public static final String EDIT_STAFF = "EDIT_STAFF";
    public static final String DELETE_STAFF = "DELETE_STAFF";
    public static final String MANAGE_STAFF_ROLES = "MANAGE_STAFF_ROLES";
    
    // Products
    public static final String VIEW_PRODUCTS = "VIEW_PRODUCTS";
    public static final String CREATE_PRODUCTS = "CREATE_PRODUCTS";
    public static final String EDIT_PRODUCTS = "EDIT_PRODUCTS";
    public static final String DELETE_PRODUCTS = "DELETE_PRODUCTS";
    
    // Payments
    public static final String VIEW_PAYMENTS = "VIEW_PAYMENTS";
    public static final String PROCESS_PAYMENTS = "PROCESS_PAYMENTS";
    public static final String PROCESS_REFUNDS = "PROCESS_REFUNDS";
    
    // Stripe
    public static final String MANAGE_STRIPE = "MANAGE_STRIPE";
    
    // Business Settings
    public static final String MANAGE_BUSINESS_SETTINGS = "MANAGE_BUSINESS_SETTINGS";
    
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
                permissions.add(VIEW_MEMBERS);
                permissions.add(CREATE_MEMBERS);
                permissions.add(EDIT_MEMBERS);
                permissions.add(DELETE_MEMBERS);
                permissions.add(VIEW_MEMBERSHIPS);
                permissions.add(CREATE_MEMBERSHIPS);
                permissions.add(EDIT_MEMBERSHIPS);
                permissions.add(DELETE_MEMBERSHIPS);
                permissions.add(ASSIGN_MEMBERSHIPS);
                permissions.add(VIEW_ANALYTICS);
                permissions.add(VIEW_REVENUE);
                permissions.add(VIEW_SIMPLE_ANALYTICS);
                permissions.add(VIEW_STAFF);
                permissions.add(CREATE_STAFF);
                permissions.add(EDIT_STAFF);
                permissions.add(DELETE_STAFF);
                permissions.add(MANAGE_STAFF_ROLES);
                permissions.add(VIEW_PRODUCTS);
                permissions.add(CREATE_PRODUCTS);
                permissions.add(EDIT_PRODUCTS);
                permissions.add(DELETE_PRODUCTS);
                permissions.add(VIEW_PAYMENTS);
                permissions.add(PROCESS_PAYMENTS);
                permissions.add(PROCESS_REFUNDS);
                permissions.add(MANAGE_STRIPE);
                permissions.add(MANAGE_BUSINESS_SETTINGS);
                break;
                
            case "MANAGER":
                // MANAGER has most permissions except revenue analytics
                permissions.add(VIEW_MEMBERS);
                permissions.add(CREATE_MEMBERS);
                permissions.add(EDIT_MEMBERS);
                permissions.add(DELETE_MEMBERS);
                permissions.add(VIEW_MEMBERSHIPS);
                permissions.add(CREATE_MEMBERSHIPS);
                permissions.add(EDIT_MEMBERSHIPS);
                permissions.add(DELETE_MEMBERSHIPS);
                permissions.add(ASSIGN_MEMBERSHIPS);
                permissions.add(VIEW_SIMPLE_ANALYTICS); // Simple analytics only, no revenue
                permissions.add(VIEW_STAFF);
                permissions.add(CREATE_STAFF);
                permissions.add(EDIT_STAFF);
                permissions.add(DELETE_STAFF);
                permissions.add(MANAGE_STAFF_ROLES);
                permissions.add(VIEW_PRODUCTS);
                permissions.add(CREATE_PRODUCTS);
                permissions.add(EDIT_PRODUCTS);
                permissions.add(DELETE_PRODUCTS);
                permissions.add(VIEW_PAYMENTS);
                permissions.add(PROCESS_PAYMENTS);
                permissions.add(PROCESS_REFUNDS);
                // No MANAGE_STRIPE for managers
                // No MANAGE_BUSINESS_SETTINGS for managers
                break;
                
            case "TEAM_MEMBER":
            case "STAFF":
                // TEAM_MEMBER has limited permissions
                permissions.add(VIEW_MEMBERS);
                permissions.add(CREATE_MEMBERS);
                permissions.add(EDIT_MEMBERS);
                // No DELETE_MEMBERS for team members
                permissions.add(VIEW_MEMBERSHIPS);
                permissions.add(ASSIGN_MEMBERSHIPS);
                // No CREATE/EDIT/DELETE memberships for team members
                permissions.add(VIEW_SIMPLE_ANALYTICS); // Simple analytics only
                // No staff management for team members
                permissions.add(VIEW_PRODUCTS);
                // No product management for team members
                permissions.add(VIEW_PAYMENTS);
                permissions.add(PROCESS_PAYMENTS);
                // No refunds for team members
                break;
                
            case "CLIENT":
                // CLIENT (Owner) has all permissions like ADMIN
                permissions.add(VIEW_MEMBERS);
                permissions.add(CREATE_MEMBERS);
                permissions.add(EDIT_MEMBERS);
                permissions.add(DELETE_MEMBERS);
                permissions.add(VIEW_MEMBERSHIPS);
                permissions.add(CREATE_MEMBERSHIPS);
                permissions.add(EDIT_MEMBERSHIPS);
                permissions.add(DELETE_MEMBERSHIPS);
                permissions.add(ASSIGN_MEMBERSHIPS);
                permissions.add(VIEW_ANALYTICS);
                permissions.add(VIEW_REVENUE);
                permissions.add(VIEW_SIMPLE_ANALYTICS);
                permissions.add(VIEW_STAFF);
                permissions.add(CREATE_STAFF);
                permissions.add(EDIT_STAFF);
                permissions.add(DELETE_STAFF);
                permissions.add(MANAGE_STAFF_ROLES);
                permissions.add(VIEW_PRODUCTS);
                permissions.add(CREATE_PRODUCTS);
                permissions.add(EDIT_PRODUCTS);
                permissions.add(DELETE_PRODUCTS);
                permissions.add(VIEW_PAYMENTS);
                permissions.add(PROCESS_PAYMENTS);
                permissions.add(PROCESS_REFUNDS);
                permissions.add(MANAGE_STRIPE);
                permissions.add(MANAGE_BUSINESS_SETTINGS);
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

