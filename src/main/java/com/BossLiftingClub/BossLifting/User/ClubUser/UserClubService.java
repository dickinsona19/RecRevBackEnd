package com.BossLiftingClub.BossLifting.User.ClubUser;

import com.BossLiftingClub.BossLifting.Club.Club;
import com.BossLiftingClub.BossLifting.Club.ClubRepository;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
public class UserClubService {

    @Autowired
    private UserClubRepository userClubRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ClubRepository clubRepository;

    @Autowired
    private MembershipRepository membershipRepository;

    @Autowired
    private StripeService stripeService;

    /**
     * Create a new user-club relationship
     */
    @Transactional
    public UserClub createUserClubRelationship(Long userId, String clubTag, Long membershipId, String status) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found with id: " + userId));

        Club club = clubRepository.findByClubTag(clubTag)
                .orElseThrow(() -> new RuntimeException("Club not found with tag: " + clubTag));

        // Check if relationship already exists
        Optional<UserClub> existingRelationship = userClubRepository.findByUserIdAndClubTag(userId, clubTag);
        if (existingRelationship.isPresent()) {
            throw new RuntimeException("User is already a member of this club");
        }

        UserClub userClub = new UserClub();
        userClub.setUser(user);
        userClub.setClub(club);
        userClub.setStatus(status);
        userClub.setStripeId(null); // Will be set when payment is processed

        // Membership can be null initially
        if (membershipId != null) {
            // You'll need to fetch the membership if needed
            // For now, we'll set it later
        }

        return userClubRepository.save(userClub);
    }

    /**
     * Get all users for a specific club by clubTag
     */
    @Transactional(readOnly = true)
    public List<UserClub> getUsersByClubTag(String clubTag) {
        return userClubRepository.findAllByClubTag(clubTag);
    }

    /**
     * Update the status of a user-club relationship
     */
    @Transactional
    public UserClub updateUserClubStatus(Long userId, String clubTag, String newStatus) {
        UserClub userClub = userClubRepository.findByUserIdAndClubTag(userId, clubTag)
                .orElseThrow(() -> new RuntimeException("User-club relationship not found"));

        userClub.setStatus(newStatus);
        return userClubRepository.save(userClub);
    }

    /**
     * Update the Stripe ID for a user-club relationship
     */
    @Transactional
    public UserClub updateUserClubStripeId(Long userId, String clubTag, String stripeId) {
        UserClub userClub = userClubRepository.findByUserIdAndClubTag(userId, clubTag)
                .orElseThrow(() -> new RuntimeException("User-club relationship not found"));

        userClub.setStripeId(stripeId);
        return userClubRepository.save(userClub);
    }

    /**
     * Remove a user from a club
     */
    @Transactional
    public void removeUserFromClub(Long userId, String clubTag) {
        UserClub userClub = userClubRepository.findByUserIdAndClubTag(userId, clubTag)
                .orElseThrow(() -> new RuntimeException("User-club relationship not found"));

        userClubRepository.delete(userClub);
    }

    /**
     * Get all clubs for a specific user
     */
    @Transactional(readOnly = true)
    public List<UserClub> getClubsForUser(Long userId) {
        return userClubRepository.findAllByUserId(userId);
    }

    /**
     * Get a specific user-club relationship
     */
    @Transactional(readOnly = true)
    public Optional<UserClub> getUserClubRelationship(Long userId, String clubTag) {
        return userClubRepository.findByUserIdAndClubTag(userId, clubTag);
    }

    // ===== UserClubMembership Management Methods =====

    /**
     * Get all memberships for a user in a specific club
     */
    @Transactional(readOnly = true)
    public List<UserClubMembership> getUserMembershipsInClub(Long userId, String clubTag) {
        UserClub userClub = userClubRepository.findByUserIdAndClubTag(userId, clubTag)
                .orElseThrow(() -> new RuntimeException("User is not a member of club: " + clubTag));

        return userClub.getUserClubMemberships();
    }

    /**
     * Add a new membership to an existing user-club relationship
     */
    @Transactional
    public UserClubMembership addMembershipToUser(
            Long userId,
            String clubTag,
            Long membershipId,
            String status,
            LocalDateTime anchorDate,
            LocalDateTime endDate,
            String stripeSubscriptionId) {

        // Get the user-club relationship
        UserClub userClub = userClubRepository.findByUserIdAndClubTag(userId, clubTag)
                .orElseThrow(() -> new RuntimeException("User is not a member of club: " + clubTag));

        // Get the membership plan
        Membership membership = membershipRepository.findById(membershipId)
                .orElseThrow(() -> new RuntimeException("Membership not found with id: " + membershipId));

        // Check if user already has this membership
        boolean alreadyHasMembership = userClub.getUserClubMemberships().stream()
                .anyMatch(ucm -> ucm.getMembership().getId().equals(membershipId)
                        && "ACTIVE".equals(ucm.getStatus()));

        if (alreadyHasMembership) {
            throw new RuntimeException("User already has an active membership with id: " + membershipId);
        }

        // Create the new UserClubMembership
        UserClubMembership userClubMembership = new UserClubMembership(
                userClub,
                membership,
                status,
                anchorDate
        );
        userClubMembership.setEndDate(endDate);
        userClubMembership.setStripeSubscriptionId(stripeSubscriptionId);

        // Add to the UserClub (this will cascade save)
        userClub.addMembership(userClubMembership);

        // Save and return
        userClubRepository.save(userClub);
        return userClubMembership;
    }

    /**
     * Update an existing UserClubMembership
     */
    @Transactional
    public UserClubMembership updateUserClubMembership(
            Long userId,
            String clubTag,
            Long userClubMembershipId,
            String status,
            LocalDateTime anchorDate,
            LocalDateTime endDate,
            String stripeSubscriptionId) {

        // Get the user-club relationship
        UserClub userClub = userClubRepository.findByUserIdAndClubTag(userId, clubTag)
                .orElseThrow(() -> new RuntimeException("User is not a member of club: " + clubTag));

        // Find the specific UserClubMembership
        UserClubMembership membership = userClub.getUserClubMemberships().stream()
                .filter(ucm -> ucm.getId().equals(userClubMembershipId))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("Membership not found with id: " + userClubMembershipId));

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
        userClubRepository.save(userClub);
        return membership;
    }
    private static final Logger log = LoggerFactory.getLogger(MembershipService.class);
    /**
     * Remove a specific membership from a user in a club
     */
    @Transactional
    public void removeMembershipFromUser(Long userId, String clubTag, Long userClubMembershipId) {
        // 1. Fetch UserClub
        UserClub userClub = userClubRepository.findByUserIdAndClubTag(userId, clubTag)
                .orElseThrow(() -> new RuntimeException("User is not a member of club: " + clubTag));

        // 2. Find the specific membership
        UserClubMembership membership = userClub.getUserClubMemberships().stream()
                .filter(ucm -> ucm.getId().equals(userClubMembershipId))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("Membership not found with id: " + userClubMembershipId));

        // 3. Get Stripe Subscription ID (assuming it's stored in membership)
        String stripeSubscriptionId = membership.getStripeSubscriptionId();
        if (stripeSubscriptionId == null || stripeSubscriptionId.isBlank()) {
            throw new RuntimeException("No Stripe subscription linked to this membership.");
        }

        // 4. Delete in Stripe
        try {
            // Get the connected account ID from the club
            String stripeAccountId = userClub.getClub().getClient() != null ?
                    userClub.getClub().getClient().getStripeAccountId() : null;

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
        userClub.removeMembership(membership);

        // 6. Save → cascade delete + remove relationship
        userClubRepository.save(userClub);
    }

    /**
     * Add a membership to a user using the UserClub ID directly
     */
    @Transactional
    public UserClubMembership addMembershipByUserClubId(Long userClubId, Long membershipId, String status, LocalDateTime anchorDate) {
        // Get the user-club relationship by ID
        UserClub userClub = userClubRepository.findById(userClubId)
                .orElseThrow(() -> new RuntimeException("UserClub relationship not found with id: " + userClubId));

        // Check if user has a payment method
        String stripeCustomerId = userClub.getStripeId();
        if (stripeCustomerId == null || stripeCustomerId.isEmpty()) {
            throw new RuntimeException("User must have a payment method before adding a membership. Please add payment method first.");
        }

        // Verify payment method exists in Stripe
        String stripeAccountId = userClub.getClub().getClient() != null ?
                userClub.getClub().getClient().getStripeAccountId() : null;

        try {
            boolean hasPaymentMethod = stripeService.hasDefaultPaymentMethod(stripeCustomerId, stripeAccountId);
            if (!hasPaymentMethod) {
                throw new RuntimeException("User must have a valid payment method before adding a membership. Please add payment method first.");
            }
        } catch (StripeException e) {
            System.err.println("Error checking payment method: " + e.getMessage());
            throw new RuntimeException("Failed to verify payment method: " + e.getMessage());
        }

        // Get the membership plan
        Membership membership = membershipRepository.findById(membershipId)
                .orElseThrow(() -> new RuntimeException("Membership not found with id: " + membershipId));

        // Check if user already has this membership
        boolean alreadyHasMembership = userClub.getUserClubMemberships().stream()
                .anyMatch(ucm -> ucm.getMembership().getId().equals(membershipId)
                        && "ACTIVE".equalsIgnoreCase(ucm.getStatus()));

        if (alreadyHasMembership) {
            throw new RuntimeException("User already has an active membership with id: " + membershipId);
        }

        // Create Stripe subscription
        String stripePriceId = membership.getStripePriceId();
        if (stripePriceId == null || stripePriceId.isEmpty()) {
            throw new RuntimeException("Membership does not have a valid Stripe price ID configured");
        }

        String stripeSubscriptionId;
        try {
            com.stripe.model.Subscription subscription = stripeService.createSubscription(
                    stripeCustomerId,
                    stripePriceId,
                    stripeAccountId,
                    anchorDate != null ? anchorDate : LocalDateTime.now()
            );
            stripeSubscriptionId = subscription.getId();
            System.out.println("Created Stripe subscription: " + stripeSubscriptionId + " for membership " + membershipId);
        } catch (StripeException e) {
            System.err.println("Failed to create Stripe subscription: " + e.getMessage());
            throw new RuntimeException("Failed to create Stripe subscription: " + e.getMessage());
        }

        // Create the new UserClubMembership with Stripe subscription ID
        UserClubMembership userClubMembership = new UserClubMembership(
                userClub,
                membership,
                status != null ? status.toUpperCase() : "ACTIVE",
                anchorDate != null ? anchorDate : LocalDateTime.now()
        );
        userClubMembership.setStripeSubscriptionId(stripeSubscriptionId);

        // Add to the UserClub (this will cascade save)
        userClub.addMembership(userClubMembership);

        // Save and return
        userClubRepository.save(userClub);
        return userClubMembership;
    }

    /**
     * Update a UserClubMembership by its ID directly
     */
    @Transactional
    public UserClubMembership updateUserClubMembershipById(
            Long userClubMembershipId,
            String status,
            LocalDateTime anchorDate,
            LocalDateTime endDate,
            String stripeSubscriptionId) {

        // Find the UserClubMembership by ID
        UserClubMembership membership = userClubRepository.findAll().stream()
                .flatMap(uc -> uc.getUserClubMemberships().stream())
                .filter(ucm -> ucm.getId().equals(userClubMembershipId))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("Membership not found with id: " + userClubMembershipId));

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

        // Save and return (find the parent UserClub to save)
        UserClub userClub = membership.getUserClub();
        userClubRepository.save(userClub);
        return membership;
    }

    /**
     * Get a UserClubMembership by its ID
     */
    @Transactional(readOnly = true)
    public UserClubMembership getUserClubMembershipById(Long id) {
        return userClubRepository.findAll().stream()
                .flatMap(uc -> uc.getUserClubMemberships().stream())
                .filter(ucm -> ucm.getId().equals(id))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("Membership not found with id: " + id));
    }

    /**
     * Remove a UserClubMembership by its ID
     * @param userClubMembershipId The ID of the membership to remove
     * @param cancelAtPeriodEnd If true, cancel at end of billing period; if false, cancel immediately
     * @return The membership with updated cancellation info
     */
    @Transactional
    public UserClubMembership removeMembershipById(Long userClubMembershipId, boolean cancelAtPeriodEnd) {
        // Find the UserClubMembership by ID
        UserClubMembership membership = userClubRepository.findAll().stream()
                .flatMap(uc -> uc.getUserClubMemberships().stream())
                .filter(ucm -> ucm.getId().equals(userClubMembershipId))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("Membership not found with id: " + userClubMembershipId));

        // Cancel the Stripe subscription if one exists
        String stripeSubscriptionId = membership.getStripeSubscriptionId();
        if (stripeSubscriptionId != null && !stripeSubscriptionId.isEmpty()) {
            try {
                // Get the connected account ID from the club
                UserClub userClub = membership.getUserClub();
                String stripeAccountId = userClub.getClub().getClient() != null ?
                        userClub.getClub().getClient().getStripeAccountId() : null;

                if (cancelAtPeriodEnd) {
                    // Cancel at period end - get current period end date
                    LocalDateTime periodEnd = stripeService.cancelSubscriptionAtPeriodEnd(stripeSubscriptionId, stripeAccountId);
                    membership.setStatus("CANCELLING");
                    membership.setEndDate(periodEnd);
                    System.out.println("Scheduled Stripe subscription cancellation: " + stripeSubscriptionId + " at period end: " + periodEnd);

                    // Save and return - DON'T delete from database yet
                    userClubRepository.save(userClub);
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

        // Get the parent UserClub
        UserClub userClub = membership.getUserClub();

        // Remove the membership immediately (cascade will handle deletion)
        userClub.removeMembership(membership);

        // Save
        userClubRepository.save(userClub);

        return membership;
    }

    /**
     * Pause a membership for a specified duration in weeks
     * The membership will be scheduled to pause at the next billing cycle
     */
    @Transactional
    public UserClubMembership pauseMembership(Long userClubMembershipId, Integer pauseDurationWeeks) {
        // Find the UserClubMembership by ID
        UserClubMembership membership = userClubRepository.findAll().stream()
                .flatMap(uc -> uc.getUserClubMemberships().stream())
                .filter(ucm -> ucm.getId().equals(userClubMembershipId))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("Membership not found with id: " + userClubMembershipId));

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
                // Get the connected account ID from the club
                UserClub userClub = membership.getUserClub();
                String stripeAccountId = userClub.getClub().getClient() != null ?
                        userClub.getClub().getClient().getStripeAccountId() : null;

                // Pass the pause end date to Stripe - it will pause at next billing cycle
                stripeService.pauseSubscription(stripeSubscriptionId, pauseEnd, stripeAccountId);
                System.out.println("Scheduled Stripe subscription pause: " + stripeSubscriptionId + " from " + pauseStart + " until " + pauseEnd + " on account: " + stripeAccountId);
            } catch (StripeException e) {
                System.err.println("Failed to pause Stripe subscription: " + e.getMessage());
                throw new RuntimeException("Failed to pause Stripe subscription: " + e.getMessage());
            }
        }

        // Save
        UserClub userClub = membership.getUserClub();
        userClubRepository.save(userClub);

        return membership;
    }

    /**
     * Resume a paused membership
     */
    @Transactional
    public UserClubMembership resumeMembership(Long userClubMembershipId) {
        // Find the UserClubMembership by ID
        UserClubMembership membership = userClubRepository.findAll().stream()
                .flatMap(uc -> uc.getUserClubMemberships().stream())
                .filter(ucm -> ucm.getId().equals(userClubMembershipId))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("Membership not found with id: " + userClubMembershipId));

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
                // Get the connected account ID from the club
                UserClub userClub = membership.getUserClub();
                String stripeAccountId = userClub.getClub().getClient() != null ?
                        userClub.getClub().getClient().getStripeAccountId() : null;

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
        UserClub userClub = membership.getUserClub();
        userClubRepository.save(userClub);

        return membership;
    }
}
