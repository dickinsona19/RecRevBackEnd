package com.BossLiftingClub.BossLifting.User.BusinessUser;

import com.BossLiftingClub.BossLifting.User.User;
import com.BossLiftingClub.BossLifting.User.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Service for checking user access based on Subscription status.
 * Implements the "Dictatorship Model" where Dependents inherit the Primary Owner's subscription status.
 */
@Service
public class AccessControlService {

    private static final Logger logger = LoggerFactory.getLogger(AccessControlService.class);

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserBusinessMembershipRepository membershipRepository;

    /**
     * Check if a user can enter the gym based on their Primary Owner's Subscription status.
     * 
     * Business Rules:
     * - If user is a Dependent (has a parent), check the Primary Owner's subscription
     * - If user is a Primary Owner (no parent), check their own subscription
     * - Access is denied if ANY subscription status is 'past_due' or 'canceled'
     * 
     * @param userId The user ID to check access for
     * @param businessTag Optional: specific business to check. If null, checks all businesses.
     * @return Map with "access" (boolean) and "reason" (string) if denied
     */
    @Transactional(readOnly = true)
    public Map<String, Object> checkAccess(Long userId, String businessTag) {
        try {
            // Find the user
            Optional<User> userOpt = userRepository.findById(userId);
            if (userOpt.isEmpty()) {
                return createAccessResponse(false, "User not found");
            }

            User user = userOpt.get();
            
            // Determine the Primary Owner (the one who pays)
            User primaryOwner = user;
            if (user.getParent() != null) {
                // This is a Dependent - use the Primary Owner's subscription
                primaryOwner = user.getParent();
                logger.debug("User {} is a Dependent, checking Primary Owner {} subscription", userId, primaryOwner.getId());
            }

            // Find all subscriptions (UserBusinessMembership) for the Primary Owner
            List<UserBusinessMembership> subscriptions;
            if (businessTag != null && !businessTag.isEmpty()) {
                // Check specific business
                subscriptions = membershipRepository.findActiveByUserIdAndBusinessTag(
                    primaryOwner.getId(), businessTag);
            } else {
                // Check all businesses
                subscriptions = membershipRepository.findByUserId(primaryOwner.getId());
            }

            // If no subscriptions found, deny access
            if (subscriptions.isEmpty()) {
                logger.debug("No subscriptions found for Primary Owner {}", primaryOwner.getId());
                return createAccessResponse(false, "No active subscription found");
            }

            // Check each subscription status
            for (UserBusinessMembership subscription : subscriptions) {
                String status = subscription.getStatus();
                
                // Block access if subscription is past_due or canceled
                if ("past_due".equalsIgnoreCase(status) || 
                    "PAST_DUE".equalsIgnoreCase(status) ||
                    "canceled".equalsIgnoreCase(status) || 
                    "CANCELLED".equalsIgnoreCase(status) ||
                    "CANCELED".equalsIgnoreCase(status)) {
                    
                    String reason = String.format("Family Account Delinquent: Subscription status is '%s'", status);
                    logger.warn("Access denied for user {} (Primary Owner: {}): {}", 
                        userId, primaryOwner.getId(), reason);
                    return createAccessResponse(false, reason);
                }
            }

            // All subscriptions are valid - grant access
            logger.debug("Access granted for user {} (Primary Owner: {})", userId, primaryOwner.getId());
            return createAccessResponse(true, null);

        } catch (Exception e) {
            logger.error("Error checking access for user {}: {}", userId, e.getMessage(), e);
            return createAccessResponse(false, "Error checking access: " + e.getMessage());
        }
    }

    /**
     * Check access by Stripe Customer ID (for webhook handlers)
     */
    @Transactional(readOnly = true)
    public Map<String, Object> checkAccessByStripeCustomerId(String stripeCustomerId, String businessTag) {
        try {
            Optional<User> userOpt = userRepository.findByUserStripeMemberId(stripeCustomerId);
            if (userOpt.isEmpty()) {
                return createAccessResponse(false, "User not found for Stripe customer ID");
            }

            return checkAccess(userOpt.get().getId(), businessTag);
        } catch (Exception e) {
            logger.error("Error checking access by Stripe customer ID {}: {}", stripeCustomerId, e.getMessage(), e);
            return createAccessResponse(false, "Error checking access: " + e.getMessage());
        }
    }

    private Map<String, Object> createAccessResponse(boolean access, String reason) {
        Map<String, Object> response = new HashMap<>();
        response.put("access", access);
        if (reason != null) {
            response.put("reason", reason);
        }
        return response;
    }
}
