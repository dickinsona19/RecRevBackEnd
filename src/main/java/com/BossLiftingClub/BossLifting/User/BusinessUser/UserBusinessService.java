package com.BossLiftingClub.BossLifting.User.BusinessUser;

import com.BossLiftingClub.BossLifting.Business.Business;
import com.BossLiftingClub.BossLifting.Business.BusinessRepository;
import com.BossLiftingClub.BossLifting.Email.EmailService;
import com.BossLiftingClub.BossLifting.Stripe.StripeService;
import com.BossLiftingClub.BossLifting.User.Membership.Membership;
import com.BossLiftingClub.BossLifting.User.Membership.MembershipRepository;
import com.BossLiftingClub.BossLifting.User.Membership.MembershipService;
import com.BossLiftingClub.BossLifting.User.User;
import com.BossLiftingClub.BossLifting.User.UserRepository;
import com.stripe.exception.StripeException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
public class UserBusinessService {

    private static final Logger logger = LoggerFactory.getLogger(UserBusinessService.class);
    private static final BigDecimal ZERO_DOLLARS = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

    @Autowired
    private UserBusinessRepository userBusinessRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private BusinessRepository businessRepository;

    @Autowired
    private MembershipRepository membershipRepository;

    @Autowired
    private StripeService stripeService;

    @Autowired
    private MemberLogRepository memberLogRepository;

    @Autowired
    private EmailService emailService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    /**
     * Create a new user-business relationship
     */
    @Transactional
    public UserBusiness createUserBusinessRelationship(Long userId, String businessTag, Long membershipId, String status) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found with id: " + userId));

        Business business = businessRepository.findByBusinessTag(businessTag)
                .orElseThrow(() -> new RuntimeException("Business not found with tag: " + businessTag));

        // Check if relationship already exists
        Optional<UserBusiness> existingRelationship = userBusinessRepository.findByUserIdAndBusinessTag(userId, businessTag);
        if (existingRelationship.isPresent()) {
            throw new RuntimeException("User is already a member of this business");
        }

        UserBusiness userBusiness = new UserBusiness();
        userBusiness.setUser(user);
        userBusiness.setBusiness(business);
        userBusiness.setStatus(status);
        userBusiness.setStripeId(null); // Will be set when payment is processed

        // Membership can be null initially
        if (membershipId != null) {
            // You'll need to fetch the membership if needed
            // For now, we'll set it later
        }

        userBusiness = userBusinessRepository.save(userBusiness);
        
        // Calculate initial status and user type
        calculateAndUpdateStatus(userBusiness);
        
        // Reload to get updated calculated fields
        return userBusinessRepository.findById(userBusiness.getId()).orElse(userBusiness);
    }

    /**
     * Get all users for a specific business by businessTag
     */
    @Transactional(readOnly = true)
    public List<UserBusiness> getUsersByBusinessTag(String businessTag) {
        return userBusinessRepository.findAllByBusinessTag(businessTag);
    }

    /**
     * Update the status of a user-business relationship
     */
    @Transactional
    public UserBusiness updateUserBusinessStatus(Long userId, String businessTag, String newStatus) {
        UserBusiness userBusiness = userBusinessRepository.findByUserIdAndBusinessTag(userId, businessTag)
                .orElseThrow(() -> new RuntimeException("User-business relationship not found"));

        userBusiness.setStatus(newStatus);
        return userBusinessRepository.save(userBusiness);
    }

    /**
     * Update the Stripe ID for a user-business relationship
     */
    @Transactional
    public UserBusiness updateUserBusinessStripeId(Long userId, String businessTag, String stripeId) {
        UserBusiness userBusiness = userBusinessRepository.findByUserIdAndBusinessTag(userId, businessTag)
                .orElseThrow(() -> new RuntimeException("User-business relationship not found"));

        userBusiness.setStripeId(stripeId);
        return userBusinessRepository.save(userBusiness);
    }

    /**
     * Remove a user from a business
     */
    @Transactional
    public void removeUserFromBusiness(Long userId, String businessTag) {
        UserBusiness userBusiness = userBusinessRepository.findByUserIdAndBusinessTag(userId, businessTag)
                .orElseThrow(() -> new RuntimeException("User-business relationship not found"));

        userBusinessRepository.delete(userBusiness);
    }

    /**
     * Get all businesses for a specific user
     */
    @Transactional(readOnly = true)
    public List<UserBusiness> getBusinessesForUser(Long userId) {
        return userBusinessRepository.findAllByUserId(userId);
    }

    /**
     * Get a specific user-business relationship
     */
    @Transactional(readOnly = true)
    public Optional<UserBusiness> getUserBusinessRelationship(Long userId, String businessTag) {
        return userBusinessRepository.findByUserIdAndBusinessTag(userId, businessTag);
    }

    /**
     * Get a specific UserBusiness by ID
     */
    @Transactional(readOnly = true)
    public Optional<UserBusiness> getUserBusinessById(Long userBusinessId) {
        return userBusinessRepository.findById(userBusinessId);
    }

    // ===== UserBusinessMembership Management Methods =====

    /**
     * Get all memberships for a user in a specific business
     */
    @Transactional(readOnly = true)
    public List<UserBusinessMembership> getUserMembershipsInBusiness(Long userId, String businessTag) {
        UserBusiness userBusiness = userBusinessRepository.findByUserIdAndBusinessTag(userId, businessTag)
                .orElseThrow(() -> new RuntimeException("User is not a member of business: " + businessTag));

        return userBusiness.getUserBusinessMemberships();
    }

    /**
     * Add a new membership to an existing user-business relationship
     */
    @Transactional
    public UserBusinessMembership addMembershipToUser(
            Long userId,
            String businessTag,
            Long membershipId,
            String status,
            LocalDateTime anchorDate,
            LocalDateTime endDate,
            String stripeSubscriptionId,
            BigDecimal overridePrice) {

        // Get the user-business relationship
        UserBusiness userBusiness = userBusinessRepository.findByUserIdAndBusinessTag(userId, businessTag)
                .orElseThrow(() -> new RuntimeException("User is not a member of business: " + businessTag));

        // Get the membership plan
        Membership membership = membershipRepository.findById(membershipId)
                .orElseThrow(() -> new RuntimeException("Membership not found with id: " + membershipId));

        // Check if user already has this membership
        boolean alreadyHasMembership = userBusiness.getUserBusinessMemberships().stream()
                .anyMatch(ucm -> ucm.getMembership().getId().equals(membershipId)
                        && "ACTIVE".equals(ucm.getStatus()));

        if (alreadyHasMembership) {
            throw new RuntimeException("User already has an active membership with id: " + membershipId);
        }

        // Create the new UserBusinessMembership
        UserBusinessMembership userBusinessMembership = new UserBusinessMembership(
                userBusiness,
                membership,
                status,
                anchorDate
        );
        userBusinessMembership.setEndDate(endDate);
        userBusinessMembership.setStripeSubscriptionId(stripeSubscriptionId);
        userBusinessMembership.setActualPrice(resolveActualPrice(membership, overridePrice));

        // Add to the UserBusiness (this will cascade save)
        userBusiness.addMembership(userBusinessMembership);

        // Save and return
        userBusinessRepository.save(userBusiness);
        return userBusinessMembership;
    }

    /**
     * Update an existing UserBusinessMembership
     */
    @Transactional
    public UserBusinessMembership updateUserBusinessMembership(
            Long userId,
            String businessTag,
            Long userBusinessMembershipId,
            String status,
            LocalDateTime anchorDate,
            LocalDateTime endDate,
            String stripeSubscriptionId) {

        // Get the user-business relationship
        UserBusiness userBusiness = userBusinessRepository.findByUserIdAndBusinessTag(userId, businessTag)
                .orElseThrow(() -> new RuntimeException("User is not a member of business: " + businessTag));

        // Find the specific UserBusinessMembership
        UserBusinessMembership membership = userBusiness.getUserBusinessMemberships().stream()
                .filter(ucm -> ucm.getId().equals(userBusinessMembershipId))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("Membership not found with id: " + userBusinessMembershipId));

        // Update fields (only if provided)
        if (status != null) {
            membership.setStatus(status);
        }
        if (anchorDate != null) {
            membership.setAnchorDate(anchorDate);
        }
        if (endDate != null) {
            membership.setEndDate(endDate);
        }
        if (stripeSubscriptionId != null) {
            membership.setStripeSubscriptionId(stripeSubscriptionId);
        }

        // Save and return
        userBusinessRepository.save(userBusiness);
        return membership;
    }
    private static final Logger log = LoggerFactory.getLogger(MembershipService.class);
    /**
     * Remove a specific membership from a user in a business
     */
    @Transactional
    public void removeMembershipFromUser(Long userId, String businessTag, Long userBusinessMembershipId) {
        // 1. Fetch UserBusiness
        UserBusiness userBusiness = userBusinessRepository.findByUserIdAndBusinessTag(userId, businessTag)
                .orElseThrow(() -> new RuntimeException("User is not a member of business: " + businessTag));

        // 2. Find the specific membership
        UserBusinessMembership membership = userBusiness.getUserBusinessMemberships().stream()
                .filter(ucm -> ucm.getId().equals(userBusinessMembershipId))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("Membership not found with id: " + userBusinessMembershipId));

        // 3. Get Stripe Subscription ID (assuming it's stored in membership)
        String stripeSubscriptionId = membership.getStripeSubscriptionId();
        if (stripeSubscriptionId == null || stripeSubscriptionId.isBlank()) {
            throw new RuntimeException("No Stripe subscription linked to this membership.");
        }

        // 4. Delete in Stripe
        try {
            // Get the connected account ID from the business
            Business business = userBusiness.getBusiness();
            if (!"COMPLETED".equals(business.getOnboardingStatus())) {
                throw new IllegalStateException("Stripe integration not complete. Please complete Stripe onboarding first.");
            }
            String stripeAccountId = business.getStripeAccountId();

            stripeService.cancelSubscription(stripeSubscriptionId, stripeAccountId); // Your Stripe wrapper
        } catch (StripeException e) {
            // Log the error (use your logger)
            log.error("Failed to cancel Stripe subscription {}: {}", stripeSubscriptionId, e.getMessage());

            // Throw a clear, user-friendly exception
            throw new RuntimeException(
                    "Failed to cancel membership: Could not cancel subscription in Stripe. " +
                            "Please contact support. (Error: " + e.getStripeError().getCode() + ")"
            );
        } catch (Exception e) {
            log.error("Unexpected error canceling Stripe subscription {}", stripeSubscriptionId, e);
            throw new RuntimeException("Failed to cancel membership due to payment processor error. Please try again or contact support.");
        }

        // 5. Remove membership from JPA entity
        userBusiness.removeMembership(membership);

        // 6. Save → cascade delete + remove relationship
        userBusinessRepository.save(userBusiness);
    }

    /**
     * Add a membership to a user using the UserBusiness ID directly
     */
    @Transactional
    public UserBusinessMembership addMembershipByUserBusinessId(Long userBusinessId, Long membershipId, String status, LocalDateTime anchorDate, BigDecimal overridePrice, String promoCode, String signatureDataUrl, String signerName, Boolean sendOnboardingEmail, Long referredById) {
        // Get the user-business relationship by ID
        UserBusiness userBusiness = userBusinessRepository.findById(userBusinessId)
                .orElseThrow(() -> new RuntimeException("UserBusiness relationship not found with id: " + userBusinessId));
        
        // Set referredBy if provided
        if (referredById != null) {
            User user = userBusiness.getUser();
            if (user != null) {
                User referrer = userRepository.findById(referredById)
                        .orElseThrow(() -> new RuntimeException("Referrer not found with id: " + referredById));
                user.setReferredBy(referrer);
                userRepository.save(user);
            }
        }

        // Get the membership plan
        Membership membership = membershipRepository.findById(membershipId)
                .orElseThrow(() -> new RuntimeException("Membership not found with id: " + membershipId));

        // Check if user already has this membership
        boolean alreadyHasMembership = userBusiness.getUserBusinessMemberships().stream()
                .anyMatch(ucm -> ucm.getMembership().getId().equals(membershipId)
                        && ("ACTIVE".equalsIgnoreCase(ucm.getStatus()) || "PENDING".equalsIgnoreCase(ucm.getStatus())));

        if (alreadyHasMembership) {
            throw new RuntimeException("User already has an active or pending membership with id: " + membershipId);
        }

        // Get business for later use
        Business business = userBusiness.getBusiness();

        // Determine if membership should be activated immediately (only if signature is provided)
        boolean shouldActivateNow = signatureDataUrl != null && !signatureDataUrl.isEmpty();
        String finalStatus = shouldActivateNow ? (status != null ? status.toUpperCase() : "ACTIVE") : "PENDING";
        
        BigDecimal actualPrice = resolveActualPrice(membership, overridePrice);
        String stripeSubscriptionId = null;

        // Only create Stripe subscription if signature is provided (membership is being activated)
        // For PENDING memberships, subscription will be created when user signs
        if (shouldActivateNow) {
            // Check onboarding status and payment method only when activating
            if (!"COMPLETED".equals(business.getOnboardingStatus())) {
                throw new IllegalStateException("Stripe integration not complete. Please complete Stripe onboarding first.");
            }

            // Check if user has a payment method
            String stripeCustomerId = userBusiness.getStripeId();
            if (stripeCustomerId == null || stripeCustomerId.isEmpty()) {
                throw new RuntimeException("User must have a payment method before activating a membership. Please add payment method first.");
            }

            // Verify payment method exists in Stripe
            String stripeAccountId = business.getStripeAccountId();
            try {
                boolean hasPaymentMethod = stripeService.hasDefaultPaymentMethod(stripeCustomerId, stripeAccountId);
                if (!hasPaymentMethod) {
                    throw new RuntimeException("User must have a valid payment method before activating a membership. Please add payment method first.");
                }
            } catch (StripeException e) {
                System.err.println("Error checking payment method: " + e.getMessage());
                throw new RuntimeException("Failed to verify payment method: " + e.getMessage());
            }

            // Create Stripe subscription only when signature is provided (activating)
            String stripePriceId = membership.getStripePriceId();
            if (stripePriceId == null || stripePriceId.isEmpty()) {
                throw new RuntimeException("Membership does not have a valid Stripe price ID configured");
            }

            // Apply referral discount if user was referred
            String finalPromoCode = promoCode;
            if (referredById != null && business.getReferredUserDiscountMonths() != null && business.getReferredUserDiscountMonths() > 0) {
                try {
                    // Create a referral discount coupon: 100% off for the specified number of months
                    String referralCouponCode = "REFERRAL_" + referredById + "_" + System.currentTimeMillis();
                    stripeService.createPromoCode(
                            referralCouponCode,
                            "percent",
                            100.0, // 100% off
                            "repeating",
                            business.getReferredUserDiscountMonths(),
                            stripeAccountId
                    );
                    // Use the referral promo code if no other promo code was provided
                    if (finalPromoCode == null || finalPromoCode.trim().isEmpty()) {
                        finalPromoCode = referralCouponCode;
                        logger.info("Applied referral discount: {} months free for referred user", business.getReferredUserDiscountMonths());
                    }
                } catch (Exception e) {
                    logger.error("Failed to create referral discount coupon: {}", e.getMessage());
                    // Don't fail the subscription creation if coupon creation fails
                }
            }

            try {
                com.stripe.model.Subscription subscription = stripeService.createSubscription(
                        stripeCustomerId,
                        stripePriceId,
                        stripeAccountId,
                        anchorDate != null ? anchorDate : LocalDateTime.now(),
                        finalPromoCode,
                        actualPrice
                );
                stripeSubscriptionId = subscription.getId();
                System.out.println("Created Stripe subscription: " + stripeSubscriptionId + " for membership " + membershipId);
            } catch (StripeException e) {
                System.err.println("Failed to create Stripe subscription: " + e.getMessage());
                throw new RuntimeException("Failed to create Stripe subscription: " + e.getMessage());
            }
        }

        // Create the new UserBusinessMembership
        UserBusinessMembership userBusinessMembership = new UserBusinessMembership(
                userBusiness,
                membership,
                finalStatus,
                anchorDate != null ? anchorDate : LocalDateTime.now()
        );
        
        if (stripeSubscriptionId != null) {
            userBusinessMembership.setStripeSubscriptionId(stripeSubscriptionId);
        }
        userBusinessMembership.setActualPrice(actualPrice);
        
        // Set signature data if provided
        if (signatureDataUrl != null && !signatureDataUrl.isEmpty()) {
            userBusinessMembership.setSignatureDataUrl(signatureDataUrl);
            userBusinessMembership.setSignedAt(LocalDateTime.now());
            if (signerName != null && !signerName.isEmpty()) {
                userBusinessMembership.setSignerName(signerName);
            }
        }

        // Add to the UserBusiness (this will cascade save)
        userBusiness.addMembership(userBusinessMembership);
        
        // Mark that this user has ever had a membership
        userBusiness.setHasEverHadMembership(true);

        // Save and return
        userBusinessRepository.save(userBusiness);
        
        // Check if user needs onboarding email (if they agreed to sign up and don't have an account)
        if (sendOnboardingEmail != null && sendOnboardingEmail) {
            User user = userBusiness.getUser();
            if (user != null && !hasAccount(user)) {
                try {
                    sendOnboardingEmail(user, business);
                } catch (Exception e) {
                    logger.error("Failed to send onboarding email to user {}: {}", user.getId(), e.getMessage());
                    // Don't fail the membership creation if email fails
                }
            }
        }
        
        // Recalculate status and user type after membership is added
        calculateAndUpdateStatus(userBusiness);
        
        return userBusinessMembership;
    }
    
    /**
     * Check if user has an account (password is not the default "userpass1")
     */
    public boolean hasAccount(User user) {
        if (user.getPassword() == null) {
            return false;
        }
        // Check if password is the default temporary password
        // We need to check if it matches the encoded version of "userpass1"
        try {
            // Try to match against the encoded default password
            String defaultPassword = "userpass1";
            // If password matches the encoded default, user doesn't have an account
            return !passwordEncoder.matches(defaultPassword, user.getPassword());
        } catch (Exception e) {
            logger.error("Error checking user account status: {}", e.getMessage());
            // If we can't check, assume they don't have an account to be safe
            return false;
        }
    }
    
    /**
     * Send onboarding email with account credentials
     */
    private void sendOnboardingEmail(User user, Business business) {
        if (user.getEmail() == null || user.getEmail().isEmpty()) {
            logger.warn("Cannot send onboarding email: user {} has no email address", user.getId());
            return;
        }
        
        String businessName = business.getTitle() != null ? business.getTitle() : "CLT Lifting Club";
        String businessContactEmail = business.getContactEmail() != null ? business.getContactEmail() : "contact@cltliftingclub.com";
        
        // Generate a temporary password (user can change it later)
        String tempPassword = "userpass1"; // This is the default password they already have
        
        String subject = "Welcome to " + businessName + " - Your Account Details";
        String htmlContent = String.format(
            "<!DOCTYPE html>" +
            "<html>" +
            "<head>" +
            "<style>" +
            "body { font-family: Arial, sans-serif; color: #333; line-height: 1.6; }" +
            ".container { max-width: 600px; margin: 0 auto; padding: 20px; }" +
            ".header { background-color: #f8f8f8; padding: 20px; text-align: center; border-radius: 5px 5px 0 0; }" +
            ".content { padding: 20px; background-color: #fff; border: 1px solid #ddd; border-top: none; }" +
            ".credentials { background-color: #f0f0f0; padding: 15px; border-radius: 5px; margin: 20px 0; }" +
            ".credential-item { margin: 10px 0; }" +
            ".label { font-weight: bold; color: #555; }" +
            ".value { color: #333; font-family: monospace; }" +
            ".button { display: inline-block; padding: 12px 24px; background-color: #007BFF; color: white !important; text-decoration: none; border-radius: 5px; font-weight: bold; margin: 20px 0; }" +
            ".footer { font-size: 12px; color: #777; text-align: center; margin-top: 20px; padding-top: 20px; border-top: 1px solid #ddd; }" +
            "</style>" +
            "</head>" +
            "<body>" +
            "<div class=\"container\">" +
            "<div class=\"header\">" +
            "<h2>Welcome to %s!</h2>" +
            "</div>" +
            "<div class=\"content\">" +
            "<p>Hello %s,</p>" +
            "<p>Your account has been created! Here are your login credentials:</p>" +
            "<div class=\"credentials\">" +
            "<div class=\"credential-item\">" +
            "<span class=\"label\">Email:</span> <span class=\"value\">%s</span>" +
            "</div>" +
            "<div class=\"credential-item\">" +
            "<span class=\"label\">Password:</span> <span class=\"value\">%s</span>" +
            "</div>" +
            "%s" + // Phone number if available
            "</div>" +
            "<p><strong>Important:</strong> Please change your password after your first login for security.</p>" +
            "<p>You can now log in to your account and manage your membership.</p>" +
            "<p>If you have any questions, please contact us at %s.</p>" +
            "<p>Welcome aboard!</p>" +
            "</div>" +
            "<div class=\"footer\">" +
            "<p>This is an automated message. Please do not reply to this email.</p>" +
            "</div>" +
            "</div>" +
            "</body>" +
            "</html>",
            businessName,
            user.getFirstName() != null ? user.getFirstName() : "Member",
            user.getEmail(),
            tempPassword,
            user.getPhoneNumber() != null ? 
                String.format("<div class=\"credential-item\"><span class=\"label\">Phone:</span> <span class=\"value\">%s</span></div>", user.getPhoneNumber()) : "",
            businessContactEmail
        );
        
        emailService.sendBlastEmail(user.getEmail(), subject, htmlContent, businessName, businessContactEmail, businessContactEmail);
        logger.info("Onboarding email sent to user {} ({})", user.getId(), user.getEmail());
    }

    /**
     * Apply a promo code to an existing membership's Stripe subscription
     */
    @Transactional
    public UserBusinessMembership applyPromoCodeToMembership(Long userBusinessMembershipId, String promoCode) {
        // Find the UserBusinessMembership by ID
        UserBusinessMembership membership = userBusinessRepository.findAll().stream()
                .flatMap(uc -> uc.getUserBusinessMemberships().stream())
                .filter(ucm -> ucm.getId().equals(userBusinessMembershipId))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("Membership not found with id: " + userBusinessMembershipId));

        String stripeSubscriptionId = membership.getStripeSubscriptionId();
        if (stripeSubscriptionId == null || stripeSubscriptionId.isEmpty()) {
            throw new RuntimeException("Membership does not have a linked Stripe subscription");
        }

        Business business = membership.getUserBusiness().getBusiness();
        String stripeAccountId = business.getStripeAccountId();

        try {
            stripeService.applyPromoCodeToSubscription(stripeSubscriptionId, promoCode, stripeAccountId);
            System.out.println("Applied promo code " + promoCode + " to subscription " + stripeSubscriptionId);
        } catch (StripeException e) {
            System.err.println("Failed to apply promo code: " + e.getMessage());
            throw new RuntimeException("Failed to apply promo code: " + e.getMessage());
        }

        return membership;
    }

    /**
     * Update a UserBusinessMembership by its ID directly
     */
    @Transactional
    public UserBusinessMembership updateUserBusinessMembershipById(
            Long userBusinessMembershipId,
            String status,
            LocalDateTime anchorDate,
            LocalDateTime endDate,
            String stripeSubscriptionId) {

        // Find the UserBusinessMembership by ID
        UserBusinessMembership membership = userBusinessRepository.findAll().stream()
                .flatMap(uc -> uc.getUserBusinessMemberships().stream())
                .filter(ucm -> ucm.getId().equals(userBusinessMembershipId))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("Membership not found with id: " + userBusinessMembershipId));

        // Update fields (only if provided)
        if (status != null) {
            membership.setStatus(status.toUpperCase());
        }
        if (anchorDate != null) {
            membership.setAnchorDate(anchorDate);
        }
        if (endDate != null) {
            membership.setEndDate(endDate);
        }
        if (stripeSubscriptionId != null) {
            membership.setStripeSubscriptionId(stripeSubscriptionId);
        }

        // Save and return (find the parent UserBusiness to save)
        UserBusiness userBusiness = membership.getUserBusiness();
        userBusinessRepository.save(userBusiness);
        
        // Recalculate status and user type after membership status changes
        calculateAndUpdateStatus(userBusiness);
        
        return membership;
    }

    /**
     * Get a UserBusinessMembership by its ID
     */
    @Transactional(readOnly = true)
    public UserBusinessMembership getUserBusinessMembershipById(Long id) {
        return userBusinessRepository.findAll().stream()
                .flatMap(uc -> uc.getUserBusinessMemberships().stream())
                .filter(ucm -> ucm.getId().equals(id))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("Membership not found with id: " + id));
    }

    /**
     * Remove a UserBusinessMembership by its ID
     * @param userBusinessMembershipId The ID of the membership to remove
     * @param cancelAtPeriodEnd If true, cancel at end of billing period; if false, cancel immediately
     * @return The membership with updated cancellation info
     */
    @Transactional
    public UserBusinessMembership removeMembershipById(Long userBusinessMembershipId, boolean cancelAtPeriodEnd) {
        // Find the UserBusinessMembership by ID
        UserBusinessMembership membership = userBusinessRepository.findAll().stream()
                .flatMap(uc -> uc.getUserBusinessMemberships().stream())
                .filter(ucm -> ucm.getId().equals(userBusinessMembershipId))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("Membership not found with id: " + userBusinessMembershipId));

        // Cancel the Stripe subscription if one exists
        String stripeSubscriptionId = membership.getStripeSubscriptionId();
        if (stripeSubscriptionId != null && !stripeSubscriptionId.isEmpty()) {
            try {
                // Get the connected account ID from the business
                UserBusiness userBusiness = membership.getUserBusiness();
                Business business = userBusiness.getBusiness();
                if (!"COMPLETED".equals(business.getOnboardingStatus())) {
                    throw new IllegalStateException("Stripe integration not complete. Please complete Stripe onboarding first.");
                }
                String stripeAccountId = business.getStripeAccountId();

                if (cancelAtPeriodEnd) {
                    // Cancel at period end - get current period end date
                    LocalDateTime periodEnd = stripeService.cancelSubscriptionAtPeriodEnd(stripeSubscriptionId, stripeAccountId);
                    membership.setStatus("CANCELLING");
                    membership.setEndDate(periodEnd);
                    System.out.println("Scheduled Stripe subscription cancellation: " + stripeSubscriptionId + " at period end: " + periodEnd);

                    // Save and return - DON'T delete from database yet
                    userBusinessRepository.save(userBusiness);
                    return membership;
                } else {
                    // Cancel immediately
                    stripeService.cancelSubscription(stripeSubscriptionId, stripeAccountId);
                    System.out.println("Cancelled Stripe subscription immediately: " + stripeSubscriptionId + " on account: " + stripeAccountId);
                }
            } catch (StripeException e) {
                System.err.println("Failed to cancel Stripe subscription: " + e.getMessage());
                throw new RuntimeException("Failed to cancel Stripe subscription: " + e.getMessage());
            }
        }

        // Get the parent UserBusiness
        UserBusiness userBusiness = membership.getUserBusiness();

        // Remove the membership immediately (cascade will handle deletion)
        userBusiness.removeMembership(membership);

        // Save
        userBusinessRepository.save(userBusiness);

        return membership;
    }

    /**
     * Pause a membership for a specified duration in weeks
     * The membership will be scheduled to pause at the next billing cycle
     */
    @Transactional
    public UserBusinessMembership pauseMembership(Long userBusinessMembershipId, Integer pauseDurationWeeks) {
        // Find the UserBusinessMembership by ID
        UserBusinessMembership membership = userBusinessRepository.findAll().stream()
                .flatMap(uc -> uc.getUserBusinessMemberships().stream())
                .filter(ucm -> ucm.getId().equals(userBusinessMembershipId))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("Membership not found with id: " + userBusinessMembershipId));

        // Check if membership is active
        if (!"ACTIVE".equalsIgnoreCase(membership.getStatus())) {
            throw new RuntimeException("Only active memberships can be paused. Current status: " + membership.getStatus());
        }

        // The pause will start at the next billing cycle (anchor date)
        // Calculate when the pause should end based on the anchor date
        LocalDateTime anchorDate = membership.getAnchorDate();
        LocalDateTime pauseStart = anchorDate; // Pause starts at next billing cycle
        LocalDateTime pauseEnd = pauseStart.plusWeeks(pauseDurationWeeks);

        membership.setPauseStartDate(pauseStart);
        membership.setPauseEndDate(pauseEnd);
        // Keep status as ACTIVE until Stripe actually pauses at the billing cycle
        // We can use a different status to indicate it's scheduled for pause
        membership.setStatus("PAUSE_SCHEDULED");

        // Pause the Stripe subscription if a subscription ID exists
        // Stripe will pause at the next billing cycle
        String stripeSubscriptionId = membership.getStripeSubscriptionId();
        if (stripeSubscriptionId != null && !stripeSubscriptionId.isEmpty()) {
            try {
                // Get the connected account ID from the business
                UserBusiness userBusiness = membership.getUserBusiness();
                Business business = userBusiness.getBusiness();
                if (!"COMPLETED".equals(business.getOnboardingStatus())) {
                    throw new IllegalStateException("Stripe integration not complete. Please complete Stripe onboarding first.");
                }
                String stripeAccountId = business.getStripeAccountId();

                // Pass the pause end date to Stripe - it will pause at next billing cycle
                stripeService.pauseSubscription(stripeSubscriptionId, pauseEnd, stripeAccountId);
                System.out.println("Scheduled Stripe subscription pause: " + stripeSubscriptionId + " from " + pauseStart + " until " + pauseEnd + " on account: " + stripeAccountId);
            } catch (StripeException e) {
                System.err.println("Failed to pause Stripe subscription: " + e.getMessage());
                throw new RuntimeException("Failed to pause Stripe subscription: " + e.getMessage());
            }
        }

        // Save
        UserBusiness userBusiness = membership.getUserBusiness();
        userBusinessRepository.save(userBusiness);

        return membership;
    }

    /**
     * Resume a paused membership
     */
    @Transactional
    public UserBusinessMembership resumeMembership(Long userBusinessMembershipId) {
        // Find the UserBusinessMembership by ID
        UserBusinessMembership membership = userBusinessRepository.findAll().stream()
                .flatMap(uc -> uc.getUserBusinessMemberships().stream())
                .filter(ucm -> ucm.getId().equals(userBusinessMembershipId))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("Membership not found with id: " + userBusinessMembershipId));

        // Check if membership is paused
        if (!"PAUSED".equalsIgnoreCase(membership.getStatus())) {
            throw new RuntimeException("Only paused memberships can be resumed. Current status: " + membership.getStatus());
        }

        // Calculate how much pause time was used
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime pauseStart = membership.getPauseStartDate();
        LocalDateTime pauseEnd = membership.getPauseEndDate();

        if (pauseStart != null && pauseEnd != null) {
            // Calculate the duration of the pause
            long pausedDays = java.time.temporal.ChronoUnit.DAYS.between(pauseStart, now);

            // Extend the anchor date by the number of days paused
            LocalDateTime currentAnchor = membership.getAnchorDate();
            membership.setAnchorDate(currentAnchor.plusDays(pausedDays));
        }

        // Resume the Stripe subscription if a subscription ID exists
        String stripeSubscriptionId = membership.getStripeSubscriptionId();
        if (stripeSubscriptionId != null && !stripeSubscriptionId.isEmpty()) {
            try {
                // Get the connected account ID from the business
                UserBusiness userBusiness = membership.getUserBusiness();
                Business business = userBusiness.getBusiness();
                if (!"COMPLETED".equals(business.getOnboardingStatus())) {
                    throw new IllegalStateException("Stripe integration not complete. Please complete Stripe onboarding first.");
                }
                String stripeAccountId = business.getStripeAccountId();

                stripeService.resumeSubscription(stripeSubscriptionId, stripeAccountId);
                System.out.println("Resumed Stripe subscription: " + stripeSubscriptionId + " on account: " + stripeAccountId);
            } catch (StripeException e) {
                System.err.println("Failed to resume Stripe subscription: " + e.getMessage());
                throw new RuntimeException("Failed to resume Stripe subscription: " + e.getMessage());
            }
        }

        // Clear pause dates and set status back to active
        membership.setPauseStartDate(null);
        membership.setPauseEndDate(null);
        membership.setStatus("ACTIVE");

        // Save
        UserBusiness userBusiness = membership.getUserBusiness();
        userBusinessRepository.save(userBusiness);

        return membership;
    }

    // ===== Notes Management Methods =====

    private BigDecimal resolveActualPrice(Membership membership, BigDecimal overridePrice) {
        if (overridePrice != null && overridePrice.compareTo(BigDecimal.ZERO) > 0) {
            return overridePrice.setScale(2, RoundingMode.HALF_UP);
        }
        return parsePrice(membership.getPrice());
    }

    private BigDecimal parsePrice(String priceStr) {
        if (priceStr == null || priceStr.isEmpty()) {
            return ZERO_DOLLARS;
        }
        String normalized = priceStr.replaceAll("[^0-9.]", "");
        if (normalized.isEmpty()) {
            return ZERO_DOLLARS;
        }
        try {
            return new BigDecimal(normalized).setScale(2, RoundingMode.HALF_UP);
        } catch (NumberFormatException e) {
            return ZERO_DOLLARS;
        }
    }

    /**
     * Update notes for a UserBusiness
     */
    @Transactional
    public UserBusiness updateMemberNotes(Long userBusinessId, String notes) {
        UserBusiness userBusiness = userBusinessRepository.findById(userBusinessId)
                .orElseThrow(() -> new RuntimeException("UserBusiness not found with id: " + userBusinessId));

        userBusiness.setNotes(notes);
        return userBusinessRepository.save(userBusiness);
    }

    /**
     * Update notes for a UserBusiness by userId and businessTag
     */
    @Transactional
    public UserBusiness updateMemberNotesByUserAndBusiness(Long userId, String businessTag, String notes) {
        UserBusiness userBusiness = userBusinessRepository.findByUserIdAndBusinessTag(userId, businessTag)
                .orElseThrow(() -> new RuntimeException("User is not a member of business: " + businessTag));
        
        userBusiness.setNotes(notes);
        return userBusinessRepository.save(userBusiness);
    }

    // ===== Member Logs Management Methods =====

    /**
     * Get all logs for a specific member
     */
    @Transactional(readOnly = true)
    public List<MemberLog> getMemberLogs(Long userBusinessId) {
        return memberLogRepository.findByUserBusiness_IdOrderByCreatedAtDesc(userBusinessId);
    }

    /**
     * Add a new log for a member
     */
    @Transactional
    public MemberLog addMemberLog(Long userBusinessId, String logText, String createdBy) {
        UserBusiness userBusiness = userBusinessRepository.findById(userBusinessId)
                .orElseThrow(() -> new RuntimeException("UserBusiness not found with id: " + userBusinessId));

        MemberLog log = new MemberLog(userBusiness, logText, createdBy);
        userBusiness.addMemberLog(log);
        userBusinessRepository.save(userBusiness);
        return log;
    }

    /**
     * Update an existing log
     */
    @Transactional
    public MemberLog updateMemberLog(Long logId, String logText, String updatedBy) {
        MemberLog log = memberLogRepository.findById(logId)
                .orElseThrow(() -> new RuntimeException("Log not found with id: " + logId));

        log.setLogText(logText);
        log.setUpdatedBy(updatedBy);
        return memberLogRepository.save(log);
    }

    /**
     * Delete a log
     */
    @Transactional
    public void deleteMemberLog(Long logId) {
        MemberLog log = memberLogRepository.findById(logId)
                .orElseThrow(() -> new RuntimeException("Log not found with id: " + logId));

        UserBusiness userBusiness = log.getUserBusiness();
        userBusiness.removeMemberLog(log);
        userBusinessRepository.save(userBusiness);
    }

    /**
     * Calculate and update the status and user type for a UserBusiness relationship
     * This method implements the same logic as the frontend calculateMemberStatus function
     * 
     * Rules:
     * 1. Waiver Required: Always override if waiver not signed
     * 2. Cancelled: All memberships cancelled (takes precedence over everything except waiver)
     * 3. Free Pass User: NO card AND waiver signed AND no memberships = Inactive
     * 4. Pending: Has card + No memberships + Never had memberships
     * 5. Paused: Any membership is paused
     * 6. Delinquent: isDelinquent = true AND has memberships
     * 7. Active: Has active membership(s)
     * 8. Inactive: Default fallback
     */
    @Transactional
    public void calculateAndUpdateStatus(UserBusiness userBusiness) {
        User user = userBusiness.getUser();
        
        // Check if user has a card/payment method (using stripeId as proxy)
        boolean hasCard = userBusiness.getStripeId() != null && 
                         !userBusiness.getStripeId().isEmpty() && 
                         !userBusiness.getStripeId().equals("null");
        
        // Check if waiver is signed
        boolean hasWaiver = user.getWaiverSignedDate() != null;
        
        // Get memberships
        List<UserBusinessMembership> memberships = userBusiness.getUserBusinessMemberships();
        boolean hasMemberships = memberships != null && !memberships.isEmpty();
        
        // Get flags
        Boolean hasEverHadMembership = userBusiness.getHasEverHadMembership();
        boolean hasEverHadMembershipFlag = hasEverHadMembership != null && hasEverHadMembership;
        Boolean isDelinquent = userBusiness.getIsDelinquent();
        boolean isDelinquentFlag = isDelinquent != null && isDelinquent;
        
        String calculatedStatus;
        String calculatedUserType;
        
        // RULE 1: Check waiver first - if no waiver, ALWAYS show "Waiver Required"
        if (!hasWaiver) {
            calculatedStatus = "Waiver Required";
            calculatedUserType = !hasCard ? "Free Pass User" : "Member";
            userBusiness.setCalculatedStatus(calculatedStatus);
            userBusiness.setCalculatedUserType(calculatedUserType);
            userBusinessRepository.save(userBusiness);
            return;
        }
        
        // Waiver is signed - continue with status determination
        
        // RULE 2: Check if all memberships are cancelled FIRST
        if (hasMemberships && memberships != null) {
            boolean allCanceled = memberships.stream()
                    .allMatch(m -> "CANCELLED".equalsIgnoreCase(m.getStatus()) || 
                                 "CANCELED".equalsIgnoreCase(m.getStatus()));
            
            if (allCanceled) {
                calculatedStatus = "Canceled";
                calculatedUserType = "Member";
                userBusiness.setCalculatedStatus(calculatedStatus);
                userBusiness.setCalculatedUserType(calculatedUserType);
                userBusinessRepository.save(userBusiness);
                return;
            }
        }
        
        // RULE 3: Free Pass User - NO card AND waiver signed AND no memberships
        if (!hasCard && !hasMemberships) {
            calculatedStatus = "Inactive";
            calculatedUserType = "Free Pass User";
            userBusiness.setCalculatedStatus(calculatedStatus);
            userBusiness.setCalculatedUserType(calculatedUserType);
            userBusinessRepository.save(userBusiness);
            return;
        }
        
        // Has card but NO membership
        if (hasCard && !hasMemberships) {
            // RULE 4: Pending - Has card + No memberships + Never had memberships
            if (!hasEverHadMembershipFlag) {
                calculatedStatus = "Pending";
                calculatedUserType = "Member";
                userBusiness.setCalculatedStatus(calculatedStatus);
                userBusiness.setCalculatedUserType(calculatedUserType);
                userBusinessRepository.save(userBusiness);
                return;
            }
            
            // Had memberships before but all are cancelled/removed
            calculatedStatus = "Inactive";
            calculatedUserType = "Member";
            userBusiness.setCalculatedStatus(calculatedStatus);
            userBusiness.setCalculatedUserType(calculatedUserType);
            userBusinessRepository.save(userBusiness);
            return;
        }
        
        // Has card and memberships - check other membership statuses
        if (hasCard && hasMemberships && memberships != null) {
            // RULE 5: Check if any membership is paused
            boolean hasPausedMembership = memberships.stream()
                    .anyMatch(m -> "PAUSED".equalsIgnoreCase(m.getStatus()) || 
                                 "PAUSE_SCHEDULED".equalsIgnoreCase(m.getStatus()));
            
            if (hasPausedMembership) {
                calculatedStatus = "Paused / On Hold";
                calculatedUserType = "Member";
                userBusiness.setCalculatedStatus(calculatedStatus);
                userBusiness.setCalculatedUserType(calculatedUserType);
                userBusinessRepository.save(userBusiness);
                return;
            }
            
            // RULE 6: Check if user is delinquent
            if (isDelinquentFlag) {
                calculatedStatus = "Delinquent";
                calculatedUserType = "Member";
                userBusiness.setCalculatedStatus(calculatedStatus);
                userBusiness.setCalculatedUserType(calculatedUserType);
                userBusinessRepository.save(userBusiness);
                return;
            }
            
            // RULE 7: Has active membership(s)
            boolean hasActiveMembership = memberships.stream()
                    .anyMatch(m -> "ACTIVE".equalsIgnoreCase(m.getStatus()));
            
            if (hasActiveMembership) {
                calculatedStatus = "Active";
                calculatedUserType = "Member";
                userBusiness.setCalculatedStatus(calculatedStatus);
                userBusiness.setCalculatedUserType(calculatedUserType);
                userBusinessRepository.save(userBusiness);
                return;
            }
        }
        
        // Default fallback
        calculatedStatus = "Inactive";
        calculatedUserType = hasCard ? "Member" : "Free Pass User";
        userBusiness.setCalculatedStatus(calculatedStatus);
        userBusiness.setCalculatedUserType(calculatedUserType);
        userBusinessRepository.save(userBusiness);
    }
    
    /**
     * Convenience method to recalculate status for a UserBusiness by ID
     */
    @Transactional
    public void recalculateStatus(Long userBusinessId) {
        UserBusiness userBusiness = userBusinessRepository.findById(userBusinessId)
                .orElseThrow(() -> new RuntimeException("UserBusiness not found with id: " + userBusinessId));
        calculateAndUpdateStatus(userBusiness);
    }
}
