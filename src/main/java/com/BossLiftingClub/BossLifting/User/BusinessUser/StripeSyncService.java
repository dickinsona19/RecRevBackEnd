package com.BossLiftingClub.BossLifting.User.BusinessUser;

import com.BossLiftingClub.BossLifting.Business.Business;
import com.BossLiftingClub.BossLifting.Business.BusinessRepository;
import com.BossLiftingClub.BossLifting.User.Membership.Membership;
import com.BossLiftingClub.BossLifting.User.Membership.MembershipRepository;
import com.stripe.Stripe;
import com.stripe.exception.StripeException;
import com.stripe.model.Product;
import com.stripe.model.Subscription;
import com.stripe.param.SubscriptionListParams;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.annotation.PostConstruct;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
public class StripeSyncService {

    private static final Logger logger = LoggerFactory.getLogger(StripeSyncService.class);

    @Value("${stripe.secret.key:}")
    private String stripeApiKey;

    @Autowired
    private BusinessRepository businessRepository;

    @Autowired
    private UserBusinessRepository userBusinessRepository;

    @Autowired
    private UserBusinessService userBusinessService;

    @Autowired
    private MembershipRepository membershipRepository;

    @PostConstruct
    public void initStripe() {
        Stripe.apiKey = stripeApiKey;
    }

    /**
     * Sync subscription statuses from Stripe for a specific business
     * @param businessTag The business tag to sync
     * @return Map with sync results
     */
    @Transactional
    public Map<String, Object> syncBusinessFromStripe(String businessTag) {
        Map<String, Object> result = new java.util.HashMap<>();
        int synced = 0;
        int updated = 0;
        int errors = 0;
        
        try {
            Business business = businessRepository.findByBusinessTag(businessTag)
                    .orElseThrow(() -> new RuntimeException("Business not found with tag: " + businessTag));
            
            String stripeAccountId = business.getStripeAccountId();
            
            if (stripeAccountId == null || stripeAccountId.isEmpty()) {
                result.put("success", false);
                result.put("message", "Business does not have Stripe account configured");
                return result;
            }
            
            List<UserBusiness> userBusinesses = userBusinessRepository.findByBusinessId(business.getId());
            logger.info("Syncing {} user-business relationships for business {}", userBusinesses.size(), businessTag);
            
            for (UserBusiness userBusiness : userBusinesses) {
                try {
                    String stripeCustomerId = userBusiness.getStripeId();
                    
                    if (stripeCustomerId == null || stripeCustomerId.isEmpty()) {
                        continue;
                    }
                    
                    com.stripe.net.RequestOptions requestOptions = com.stripe.net.RequestOptions.builder()
                            .setStripeAccount(stripeAccountId)
                            .build();
                    
                    SubscriptionListParams params = SubscriptionListParams.builder()
                            .setCustomer(stripeCustomerId)
                            .setStatus(SubscriptionListParams.Status.ALL)
                            .setLimit(100L)
                            .build();
                    
                    com.stripe.model.SubscriptionCollection subscriptions = Subscription.list(params, requestOptions);
                    
                    boolean needsRecalculation = false;
                    boolean hasPastDueSubscription = false;
                    
                    // Compare each Stripe subscription with database memberships
                    for (Subscription stripeSub : subscriptions.getData()) {
                        String stripeSubscriptionId = stripeSub.getId();
                        String stripeStatus = stripeSub.getStatus();
                        
                        // Get product name and price from Stripe subscription
                        final String[] productNameRef = {null};
                        final BigDecimal[] stripePriceRef = {null};
                        final String[] chargeIntervalRef = {"MONTHLY"};
                        
                        if (stripeSub.getItems() != null && !stripeSub.getItems().getData().isEmpty()) {
                            com.stripe.model.SubscriptionItem item = stripeSub.getItems().getData().get(0);
                            if (item.getPrice() != null) {
                                // Get product name
                                String productId = item.getPrice().getProduct();
                                if (productId != null) {
                                    try {
                                        Product product = Product.retrieve(productId, requestOptions);
                                        productNameRef[0] = product.getName();
                                    } catch (Exception e) {
                                        logger.warn("Could not retrieve product {}: {}", productId, e.getMessage());
                                    }
                                }
                                
                                // Get price
                                if (item.getPrice().getUnitAmount() != null) {
                                    stripePriceRef[0] = BigDecimal.valueOf(item.getPrice().getUnitAmount()).divide(BigDecimal.valueOf(100));
                                }
                                
                                // Get interval
                                if (item.getPrice().getRecurring() != null) {
                                    String interval = item.getPrice().getRecurring().getInterval();
                                    if (interval != null) {
                                        chargeIntervalRef[0] = interval.toUpperCase();
                                    }
                                }
                            }
                        }
                        
                        final String productName = productNameRef[0];
                        final BigDecimal stripePrice = stripePriceRef[0];
                        final String chargeInterval = chargeIntervalRef[0];
                        
                        // First try to find by subscription ID
                        UserBusinessMembership membership = userBusiness.getUserBusinessMemberships().stream()
                                .filter(m -> stripeSubscriptionId.equals(m.getStripeSubscriptionId()))
                                .findFirst()
                                .orElse(null);
                        
                        // Check if subscription is past_due
                        if ("past_due".equalsIgnoreCase(stripeStatus) || "unpaid".equalsIgnoreCase(stripeStatus)) {
                            hasPastDueSubscription = true;
                        }
                        
                        if (membership != null) {
                            // Found by subscription ID - update status and price
                            String dbStatus = mapStripeStatusToDbStatus(stripeStatus);
                            
                            if (!dbStatus.equalsIgnoreCase(membership.getStatus())) {
                                logger.info("Updating membership {} status from {} to {} (Stripe: {})", 
                                        membership.getId(), membership.getStatus(), dbStatus, stripeStatus);
                                
                                userBusinessService.updateUserBusinessMembershipById(
                                        membership.getId(), dbStatus, null, null, null);
                                needsRecalculation = true;
                                updated++;
                            }
                            
                            // Update price if it changed (both actualPrice and membership price if names match)
                            if (stripePrice != null && membership.getMembership() != null) {
                                BigDecimal currentPrice = membership.getActualPrice() != null 
                                    ? membership.getActualPrice() 
                                    : new BigDecimal(membership.getMembership().getPrice());
                                
                                if (stripePrice.compareTo(currentPrice) != 0) {
                                    logger.info("Updating membership {} actualPrice from {} to {} (Stripe)", 
                                            membership.getId(), currentPrice, stripePrice);
                                    membership.setActualPrice(stripePrice);
                                    
                                    // Also update the membership template price if product name matches
                                    if (productName != null && productName.equalsIgnoreCase(membership.getMembership().getTitle())) {
                                        BigDecimal membershipPrice = new BigDecimal(membership.getMembership().getPrice());
                                        if (stripePrice.compareTo(membershipPrice) != 0) {
                                            logger.info("Updating membership template '{}' price from {} to {} (Stripe)", 
                                                    membership.getMembership().getTitle(), membershipPrice, stripePrice);
                                            membership.getMembership().setPrice(stripePrice.toString());
                                            membershipRepository.save(membership.getMembership());
                                        }
                                    }
                                    
                                    userBusinessRepository.save(userBusiness);
                                    needsRecalculation = true;
                                    updated++;
                                }
                            }
                            
                            boolean isPausedInStripe = "paused".equalsIgnoreCase(stripeStatus) || 
                                                       stripeSub.getPauseCollection() != null;
                            boolean isPausedInDb = userBusiness.getIsPaused() != null && userBusiness.getIsPaused();
                            
                            if (isPausedInStripe != isPausedInDb) {
                                userBusiness.setIsPaused(isPausedInStripe);
                                userBusinessRepository.save(userBusiness);
                                needsRecalculation = true;
                            }
                        } else if (productName != null) {
                            // Not found by subscription ID - try to find by product name
                            List<Membership> matchingMemberships = membershipRepository.findByBusinessTag(businessTag);
                            Membership matchingMembership = matchingMemberships.stream()
                                    .filter(m -> productName.equalsIgnoreCase(m.getTitle()))
                                    .findFirst()
                                    .orElse(null);
                            
                            if (matchingMembership != null) {
                                // Found matching membership by name - create UserBusinessMembership directly
                                logger.info("Found matching membership '{}' by name, creating UserBusinessMembership for subscription {}", 
                                        productName, stripeSubscriptionId);
                                
                                String dbStatus = mapStripeStatusToDbStatus(stripeStatus);
                                BigDecimal actualPrice = stripePrice != null ? stripePrice : new BigDecimal(matchingMembership.getPrice());
                                
                                // Create UserBusinessMembership directly (subscription already exists in Stripe)
                                com.BossLiftingClub.BossLifting.User.BusinessUser.UserBusinessMembership newMembership = 
                                        new com.BossLiftingClub.BossLifting.User.BusinessUser.UserBusinessMembership();
                                newMembership.setUserBusiness(userBusiness);
                                newMembership.setMembership(matchingMembership);
                                newMembership.setStatus(dbStatus);
                                newMembership.setStripeSubscriptionId(stripeSubscriptionId);
                                newMembership.setActualPrice(actualPrice);
                                newMembership.setAnchorDate(LocalDateTime.now());
                                newMembership.setCreatedAt(LocalDateTime.now());
                                newMembership.setUpdatedAt(LocalDateTime.now());
                                
                                userBusiness.addMembership(newMembership);
                                userBusiness.setHasEverHadMembership(true);
                                userBusinessRepository.save(userBusiness);
                                
                                needsRecalculation = true;
                                updated++;
                            } else {
                                // No matching membership found - create a new membership
                                logger.info("No matching membership found for product '{}', creating new membership", productName);
                                
                                Membership newMembership = new Membership();
                                newMembership.setTitle(productName);
                                newMembership.setPrice(stripePrice != null ? stripePrice.toString() : "0.00");
                                newMembership.setChargeInterval(chargeInterval);
                                newMembership.setBusinessTag(businessTag);
                                newMembership.setArchived(false);
                                
                                Membership savedMembership = membershipRepository.save(newMembership);
                                
                                // Create UserBusinessMembership directly (subscription already exists in Stripe)
                                String dbStatus = mapStripeStatusToDbStatus(stripeStatus);
                                BigDecimal actualPrice = stripePrice != null ? stripePrice : BigDecimal.ZERO;
                                
                                com.BossLiftingClub.BossLifting.User.BusinessUser.UserBusinessMembership newUserMembership = 
                                        new com.BossLiftingClub.BossLifting.User.BusinessUser.UserBusinessMembership();
                                newUserMembership.setUserBusiness(userBusiness);
                                newUserMembership.setMembership(savedMembership);
                                newUserMembership.setStatus(dbStatus);
                                newUserMembership.setStripeSubscriptionId(stripeSubscriptionId);
                                newUserMembership.setActualPrice(actualPrice);
                                newUserMembership.setAnchorDate(LocalDateTime.now());
                                newUserMembership.setCreatedAt(LocalDateTime.now());
                                newUserMembership.setUpdatedAt(LocalDateTime.now());
                                
                                userBusiness.addMembership(newUserMembership);
                                userBusiness.setHasEverHadMembership(true);
                                userBusinessRepository.save(userBusiness);
                                
                                needsRecalculation = true;
                                updated++;
                            }
                        }
                    }
                    
                    // Check for subscriptions that were deleted in Stripe but still exist in database
                    List<String> stripeSubscriptionIds = subscriptions.getData().stream()
                            .map(Subscription::getId)
                            .collect(java.util.stream.Collectors.toList());
                    
                    List<UserBusinessMembership> membershipsToRemove = userBusiness.getUserBusinessMemberships().stream()
                            .filter(m -> m.getStripeSubscriptionId() != null && 
                                       !stripeSubscriptionIds.contains(m.getStripeSubscriptionId()) &&
                                       !"CANCELLED".equalsIgnoreCase(m.getStatus()) &&
                                       !"CANCELED".equalsIgnoreCase(m.getStatus()))
                            .collect(java.util.stream.Collectors.toList());
                    
                    for (UserBusinessMembership membership : membershipsToRemove) {
                        logger.info("Removing membership {} - subscription {} not found in Stripe", 
                                membership.getId(), membership.getStripeSubscriptionId());
                        userBusiness.removeMembership(membership);
                        needsRecalculation = true;
                        updated++;
                    }
                    
                    // Update isDelinquent flag based on past_due subscriptions
                    if (hasPastDueSubscription) {
                        if (userBusiness.getIsDelinquent() == null || !userBusiness.getIsDelinquent()) {
                            logger.info("Setting isDelinquent=true for UserBusiness {} - has past_due subscription", 
                                    userBusiness.getId());
                            userBusiness.setIsDelinquent(true);
                            needsRecalculation = true;
                        }
                    } else {
                        // All subscriptions are up to date - clear delinquent flag
                        // This will allow the member to have "Active" status again
                        if (userBusiness.getIsDelinquent() != null && userBusiness.getIsDelinquent()) {
                            logger.info("Clearing isDelinquent for UserBusiness {} - all subscriptions are up to date", 
                                    userBusiness.getId());
                            userBusiness.setIsDelinquent(false);
                            needsRecalculation = true;
                        }
                    }
                    
                    // Recalculate status - if all payments are up to date and memberships are active,
                    // the member will have "Active" status
                    if (needsRecalculation) {
                        userBusinessRepository.save(userBusiness);
                        userBusinessService.calculateAndUpdateStatus(userBusiness);
                    }
                    
                    synced++;
                    
                } catch (com.stripe.exception.StripeException e) {
                    logger.error("Error syncing subscriptions for UserBusiness {}: {}", 
                            userBusiness.getId(), e.getMessage());
                    errors++;
                } catch (Exception e) {
                    logger.error("Unexpected error syncing UserBusiness {}: {}", 
                            userBusiness.getId(), e.getMessage(), e);
                    errors++;
                }
            }
            
            result.put("success", true);
            result.put("synced", synced);
            result.put("updated", updated);
            result.put("errors", errors);
            result.put("message", String.format("Synced %d members, updated %d, %d errors", synced, updated, errors));
            
        } catch (Exception e) {
            logger.error("Error syncing business {} from Stripe: {}", businessTag, e.getMessage(), e);
            result.put("success", false);
            result.put("error", e.getMessage());
        }
        
        return result;
    }

    /**
     * Daily scheduled job to sync subscription statuses from Stripe
     * Runs at 3 AM every day
     */
    @Scheduled(cron = "0 0 3 * * ?")
    @Transactional
    public void syncSubscriptionStatusesFromStripe() {
        logger.info("Starting daily Stripe subscription status sync...");
        
        try {
            // Get all businesses
            List<Business> businesses = businessRepository.findAll();
            logger.info("Found {} businesses to sync", businesses.size());
            
            int totalSynced = 0;
            int totalUpdated = 0;
            int totalErrors = 0;
            
            for (Business business : businesses) {
                String stripeAccountId = business.getStripeAccountId();
                
                // Skip businesses without Stripe account
                if (stripeAccountId == null || stripeAccountId.isEmpty()) {
                    logger.debug("Skipping business {} - no Stripe account ID", business.getId());
                    continue;
                }
                
                // Get all UserBusiness relationships for this business
                List<UserBusiness> userBusinesses = userBusinessRepository.findByBusinessId(business.getId());
                logger.debug("Found {} user-business relationships for business {}", userBusinesses.size(), business.getId());
                
                for (UserBusiness userBusiness : userBusinesses) {
                    try {
                        String stripeCustomerId = userBusiness.getStripeId();
                        
                        // Skip if no Stripe customer ID
                        if (stripeCustomerId == null || stripeCustomerId.isEmpty()) {
                            continue;
                        }
                        
                        // Fetch subscriptions from Stripe for this customer
                        com.stripe.net.RequestOptions requestOptions = com.stripe.net.RequestOptions.builder()
                                .setStripeAccount(stripeAccountId)
                                .build();
                        
                        SubscriptionListParams params = SubscriptionListParams.builder()
                                .setCustomer(stripeCustomerId)
                                .setStatus(SubscriptionListParams.Status.ALL)
                                .setLimit(100L)
                                .build();
                        
                        com.stripe.model.SubscriptionCollection subscriptions = Subscription.list(params, requestOptions);
                        
                        // Track if we need to recalculate status
                        boolean needsRecalculation = false;
                        boolean hasPastDueSubscription = false;
                        
                        // Compare each Stripe subscription with database memberships
                        for (Subscription stripeSub : subscriptions.getData()) {
                            String stripeSubscriptionId = stripeSub.getId();
                            String stripeStatus = stripeSub.getStatus();
                            
                            // Check if subscription is past_due
                            if ("past_due".equalsIgnoreCase(stripeStatus) || "unpaid".equalsIgnoreCase(stripeStatus)) {
                                hasPastDueSubscription = true;
                            }
                            
                            // Find matching membership in database
                            UserBusinessMembership membership = userBusiness.getUserBusinessMemberships().stream()
                                    .filter(m -> stripeSubscriptionId.equals(m.getStripeSubscriptionId()))
                                    .findFirst()
                                    .orElse(null);
                            
                            if (membership != null) {
                                // Map Stripe status to our status
                                String dbStatus = mapStripeStatusToDbStatus(stripeStatus);
                                
                                // Check if status needs updating
                                if (!dbStatus.equalsIgnoreCase(membership.getStatus())) {
                                    logger.info("Updating membership {} status from {} to {} (Stripe: {})", 
                                            membership.getId(), membership.getStatus(), dbStatus, stripeStatus);
                                    
                                    userBusinessService.updateUserBusinessMembershipById(
                                            membership.getId(), dbStatus, null, null, null);
                                    needsRecalculation = true;
                                    totalUpdated++;
                                }
                                
                                // Update isPaused flag based on Stripe subscription pause status
                                boolean isPausedInStripe = "paused".equalsIgnoreCase(stripeStatus) || 
                                                           stripeSub.getPauseCollection() != null;
                                boolean isPausedInDb = userBusiness.getIsPaused() != null && userBusiness.getIsPaused();
                                
                                if (isPausedInStripe != isPausedInDb) {
                                    userBusiness.setIsPaused(isPausedInStripe);
                                    userBusinessRepository.save(userBusiness);
                                    needsRecalculation = true;
                                    logger.info("Updated isPaused flag for UserBusiness {} to {}", 
                                            userBusiness.getId(), isPausedInStripe);
                                }
                            }
                        }
                        
                        // Update isDelinquent flag based on past_due subscriptions
                        if (hasPastDueSubscription) {
                            if (userBusiness.getIsDelinquent() == null || !userBusiness.getIsDelinquent()) {
                                logger.info("Setting isDelinquent=true for UserBusiness {} - has past_due subscription", 
                                        userBusiness.getId());
                                userBusiness.setIsDelinquent(true);
                                needsRecalculation = true;
                            }
                        } else {
                            // All subscriptions are up to date - clear delinquent flag
                            // This will allow the member to have "Active" status again
                            if (userBusiness.getIsDelinquent() != null && userBusiness.getIsDelinquent()) {
                                logger.info("Clearing isDelinquent for UserBusiness {} - all subscriptions are up to date", 
                                        userBusiness.getId());
                                userBusiness.setIsDelinquent(false);
                                needsRecalculation = true;
                            }
                        }
                        
                        // Recalculate status - if all payments are up to date and memberships are active,
                        // the member will have "Active" status
                        if (needsRecalculation) {
                            userBusinessRepository.save(userBusiness);
                            userBusinessService.calculateAndUpdateStatus(userBusiness);
                        }
                        
                        totalSynced++;
                        
                    } catch (StripeException e) {
                        logger.error("Error syncing subscriptions for UserBusiness {}: {}", 
                                userBusiness.getId(), e.getMessage());
                        totalErrors++;
                    } catch (Exception e) {
                        logger.error("Unexpected error syncing UserBusiness {}: {}", 
                                userBusiness.getId(), e.getMessage(), e);
                        totalErrors++;
                    }
                }
            }
            
            logger.info("Completed daily Stripe sync: {} synced, {} updated, {} errors", 
                    totalSynced, totalUpdated, totalErrors);
            
        } catch (Exception e) {
            logger.error("Error in daily Stripe sync job: {}", e.getMessage(), e);
        }
    }
    
    /**
     * Map Stripe subscription status to our database status
     */
    private String mapStripeStatusToDbStatus(String stripeStatus) {
        if (stripeStatus == null) {
            return "INACTIVE";
        }
        
        switch (stripeStatus.toLowerCase()) {
            case "active":
                return "ACTIVE";
            case "past_due":
            case "unpaid":
                return "PAST_DUE"; // Set status to PAST_DUE to mirror Stripe
            case "canceled":
            case "cancelled":
                return "CANCELLED";
            case "paused":
                return "PAUSED";
            case "incomplete":
            case "incomplete_expired":
                return "PENDING";
            case "trialing":
                return "ACTIVE";
            default:
                logger.warn("Unknown Stripe status: {}, defaulting to INACTIVE", stripeStatus);
                return "INACTIVE";
        }
    }
}

