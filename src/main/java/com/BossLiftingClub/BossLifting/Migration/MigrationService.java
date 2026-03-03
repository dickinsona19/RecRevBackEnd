package com.BossLiftingClub.BossLifting.Migration;

import com.BossLiftingClub.BossLifting.Business.Business;
import com.BossLiftingClub.BossLifting.Business.BusinessRepository;
import com.BossLiftingClub.BossLifting.User.BusinessUser.StripeSyncService;
import com.BossLiftingClub.BossLifting.User.BusinessUser.UserBusiness;
import com.BossLiftingClub.BossLifting.User.BusinessUser.UserBusinessMembership;
import com.BossLiftingClub.BossLifting.User.BusinessUser.UserBusinessRepository;
import com.BossLiftingClub.BossLifting.User.BusinessUser.UserBusinessService;
import com.BossLiftingClub.BossLifting.User.Membership.Membership;
import com.BossLiftingClub.BossLifting.User.Membership.MembershipRepository;
import com.BossLiftingClub.BossLifting.User.User;
import com.BossLiftingClub.BossLifting.User.UserRepository;
import com.BossLiftingClub.BossLifting.User.UserTitles.UserTitles;
import com.BossLiftingClub.BossLifting.User.UserTitles.UserTitlesRepository;
import com.BossLiftingClub.BossLifting.User.Waiver.WaiverSigningService;
import com.BossLiftingClub.BossLifting.User.Waiver.WaiverStatus;
import com.BossLiftingClub.BossLifting.User.Waiver.WaiverTemplateRepository;
import com.stripe.exception.StripeException;
import com.stripe.model.Customer;
import com.stripe.model.Product;
import com.stripe.model.Subscription;
import com.stripe.param.SubscriptionListParams;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class MigrationService {

    private static final Logger logger = LoggerFactory.getLogger(MigrationService.class);
    private static final String CHARACTERS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    private static final DateTimeFormatter[] DATE_PARSERS = {
            DateTimeFormatter.ISO_LOCAL_DATE_TIME,
            DateTimeFormatter.ISO_DATE_TIME,
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSSSS")
    };

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private BusinessRepository businessRepository;

    @Autowired
    private MembershipRepository membershipRepository;

    @Autowired
    private UserBusinessRepository userBusinessRepository;

    @Autowired
    private UserBusinessService userBusinessService;

    @Autowired
    private UserTitlesRepository userTitlesRepository;

    @Autowired
    private StripeSyncService stripeSyncService;

    @Autowired
    private WaiverSigningService waiverSigningService;

    @Autowired
    private WaiverTemplateRepository waiverTemplateRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private final Random random = new Random();

    /**
     * Flatten nested member structure (parents with childrenDto) into a single list.
     * Sets parentId on children for later linking.
     */
    public List<MigrationMemberInput> flattenMembers(Object input) {
        List<MigrationMemberInput> flat = new ArrayList<>();
        Set<Long> seenIds = new HashSet<>();

        if (input instanceof List) {
            for (Object item : (List<?>) input) {
                flattenMemberRecursive((MigrationMemberInput) item, null, flat, seenIds);
            }
        } else if (input instanceof MigrationMemberInput) {
            flattenMemberRecursive((MigrationMemberInput) input, null, flat, seenIds);
        }

        return flat;
    }

    private void flattenMemberRecursive(MigrationMemberInput member, Long parentId, List<MigrationMemberInput> flat, Set<Long> seenIds) {
        if (member == null || (member.getId() != null && seenIds.contains(member.getId()))) return;
        if (member.getId() != null) seenIds.add(member.getId());

        // Add referrer first (so referrer is imported before referee)
        if (member.getReferredBy() != null) {
            member.setReferredById(member.getReferredBy().getId());
            flattenMemberRecursive(member.getReferredBy(), null, flat, seenIds);
        }

        member.setParentId(parentId);
        flat.add(member);

        if (member.getChildrenDto() != null) {
            for (MigrationMemberInput child : member.getChildrenDto()) {
                flattenMemberRecursive(child, member.getId(), flat, seenIds);
            }
        }
    }

    /**
     * Dry run - preview what would happen without persisting.
     */
    @Transactional(readOnly = true)
    public MigrationResult dryRun(List<MigrationMemberInput> members, String businessTag) {
        return executeMigration(members, businessTag, true);
    }

    /**
     * Execute migration - persist to database.
     */
    @Transactional
    public MigrationResult executeMigration(List<MigrationMemberInput> members, String businessTag, boolean dryRun) {
        MigrationResult result = new MigrationResult();
        result.setDryRun(dryRun);

        Business business = businessRepository.findByBusinessTag(businessTag)
                .orElseThrow(() -> new IllegalArgumentException("Business not found with tag: " + businessTag));

        // Sort: referrers first, then parents first (so we can link referredBy and parent)
        List<MigrationMemberInput> sorted = members.stream()
                .sorted(Comparator
                        .comparing((MigrationMemberInput m) -> m.getReferredById() == null ? 0 : 1)
                        .thenComparing(m -> m.getParentId() == null ? 0 : 1))
                .collect(Collectors.toList());

        Map<Long, User> sourceIdToUser = new HashMap<>();

        for (MigrationMemberInput input : sorted) {
            result.setTotalProcessed(result.getTotalProcessed() + 1);

            try {
                // Check duplicate
                String email = resolveEmail(input);
                if (input.getUserStripeMemberId() != null && !input.getUserStripeMemberId().isEmpty()) {
                    Optional<User> existingByStripe = userRepository.findByUserStripeMemberId(input.getUserStripeMemberId());
                    if (existingByStripe.isPresent()) {
                        result.setSkipped(result.getSkipped() + 1);
                        result.getSkipReasons().add(input.getFirstName() + " " + input.getLastName() + " (Stripe ID exists)");
                        continue;
                    }
                }
                if (email != null && !email.contains("@placeholder")) {
                    Optional<User> existingByEmail = userRepository.findByEmail(email);
                    if (existingByEmail.isPresent()) {
                        result.setSkipped(result.getSkipped() + 1);
                        result.getSkipReasons().add(input.getFirstName() + " " + input.getLastName() + " (email exists)");
                        continue;
                    }
                }
                if (input.getPhoneNumber() != null && !input.getPhoneNumber().isEmpty()) {
                    Optional<User> existingByPhone = userRepository.findByPhoneNumber(input.getPhoneNumber());
                    if (existingByPhone.isPresent()) {
                        result.setSkipped(result.getSkipped() + 1);
                        result.getSkipReasons().add(input.getFirstName() + " " + input.getLastName() + " (phone exists)");
                        continue;
                    }
                }

                if (dryRun) {
                    result.setImported(result.getImported() + 1);
                    result.getImportedMembers().add("Would import: " + input.getFirstName() + " " + input.getLastName() + " (" + email + ")");
                    continue;
                }

                // Create User
                User user = createUser(input, email);
                user = userRepository.save(user);
                if (input.getId() != null) sourceIdToUser.put(input.getId(), user);

                // Link parent if child
                if (input.getParentId() != null) {
                    User parent = sourceIdToUser.get(input.getParentId());
                    if (parent != null) {
                        user.setParent(parent);
                        parent.getChildren().add(user);
                        userRepository.save(parent);
                        userRepository.save(user);
                    }
                }

                // Link referrer if referred
                if (input.getReferredById() != null) {
                    User referrer = sourceIdToUser.get(input.getReferredById());
                    if (referrer != null) {
                        user.setReferredBy(referrer);
                        referrer.getReferredMembers().add(user);
                        userRepository.save(referrer);
                        userRepository.save(user);
                    }
                }

                // Create UserBusiness
                UserBusiness userBusiness = createUserBusiness(user, business, input);
                userBusiness = userBusinessRepository.save(userBusiness);

                // Sync subscriptions from Stripe (source of truth) or create from JSON membership
                syncMembershipsFromStripe(userBusiness, business.getBusinessTag(), input);
                if (userBusiness.getUserBusinessMemberships().isEmpty() && input.getMembership() != null) {
                    createMembershipFromJson(userBusiness, business.getBusinessTag(), input);
                }

                userBusinessRepository.save(userBusiness);
                userBusinessService.calculateAndUpdateStatus(userBusiness);

                // Attach signature to waiver PDF when user has signed waiver (only if business has active waiver template)
                if (input.getSignatureData() != null && !input.getSignatureData().isBlank() &&
                        input.getSignatureData().startsWith("http")) {
                    // Use waiverSignedDate from source if present, otherwise createdAt (when they joined)
                    LocalDateTime signedAt = resolveWaiverSignedDate(input);
                    boolean hasTemplate = waiverTemplateRepository
                            .findFirstByBusinessIdAndActiveTrueOrderByVersionDesc(business.getId())
                            .isPresent();
                    if (hasTemplate) {
                        waiverSigningService.signWaiverFromSignatureUrlForMigration(
                                user.getId(), business.getId(), input.getSignatureData(), signedAt);
                    } else {
                        logger.debug("No active waiver template for business {} - storing signature URL only for {} {}",
                                business.getBusinessTag(), input.getFirstName(), input.getLastName());
                        user.setSignatureData(input.getSignatureData());
                        user.setWaiverSignedDate(signedAt);
                        user.setWaiverStatus(WaiverStatus.SIGNED);
                        userRepository.save(user);
                    }
                    // Recalculate UserBusiness status so "Waiver Required" is cleared (waiver is now signed)
                    userBusinessService.calculateAndUpdateStatus(userBusiness);
                }

                result.setImported(result.getImported() + 1);
                result.getImportedMembers().add(user.getFirstName() + " " + user.getLastName() + " (ID: " + user.getId() + ")");

            } catch (Exception e) {
                logger.error("Migration error for {} {}: {}", input.getFirstName(), input.getLastName(), e.getMessage());
                result.setErrors(result.getErrors() + 1);
                result.getErrorMessages().add(input.getFirstName() + " " + input.getLastName() + ": " + e.getMessage());
            }
        }

        return result;
    }

    private String resolveEmail(MigrationMemberInput input) {
        if (input.getUserStripeMemberId() != null && !input.getUserStripeMemberId().isEmpty()) {
            try {
                Customer customer = Customer.retrieve(input.getUserStripeMemberId());
                String email = customer.getEmail();
                if (email != null && !email.isEmpty()) return email;
            } catch (StripeException e) {
                logger.warn("Could not fetch email from Stripe for {}: {}", input.getUserStripeMemberId(), e.getMessage());
            }
        }
        return "migrated-" + (input.getId() != null ? input.getId() : UUID.randomUUID().toString().substring(0, 8)) + "@placeholder.local";
    }

    private User createUser(MigrationMemberInput input, String email) {
        User user = new User();
        user.setFirstName(input.getFirstName() != null ? input.getFirstName() : "Unknown");
        user.setLastName(input.getLastName() != null ? input.getLastName() : "User");
        // Password: use existing bcrypt hash as-is from source (do NOT re-encode). Fallback for children without hash.
        if (input.getPassword() != null && input.getPassword().startsWith("$2")) {
            user.setPassword(input.getPassword());
        } else {
            user.setPassword(passwordEncoder.encode("ChangeMe!" + (input.getId() != null ? input.getId() : System.currentTimeMillis())));
        }
        user.setEmail(email);
        user.setPhoneNumber(input.getPhoneNumber());
        user.setIsInGoodStanding(input.getIsInGoodStanding() != null ? input.getIsInGoodStanding() : false);
        user.setUserStripeMemberId(input.getUserStripeMemberId());
        user.setLockedInRate(input.getLockedInRate());
        user.setSignatureData(input.getSignatureData());
        user.setProfilePictureUrl(input.getProfilePictureUrl());
        user.setOver18(input.getOver18() != null ? input.getOver18() : false);

        // Parse createdAt
        if (input.getCreatedAt() != null) {
            try {
                for (DateTimeFormatter fmt : DATE_PARSERS) {
                    try {
                        user.setCreatedAt(LocalDateTime.parse(input.getCreatedAt().replace("Z", ""), fmt));
                        break;
                    } catch (Exception ignored) {}
                }
            } catch (Exception ignored) {
                user.setCreatedAt(LocalDateTime.now());
            }
        } else {
            user.setCreatedAt(LocalDateTime.now());
        }

        // Entry token and referral code - always generate new to ensure uniqueness in target system
        user.setEntryQrcodeToken(generateUniqueToken(10));
        user.setReferralCode(generateUniqueReferralCode());

        // User title
        if (input.getUserTitles() != null && input.getUserTitles().getTitle() != null) {
            UserTitles title = userTitlesRepository.findByTitle(input.getUserTitles().getTitle())
                    .orElseGet(() -> {
                        UserTitles t = new UserTitles(input.getUserTitles().getTitle());
                        return userTitlesRepository.save(t);
                    });
            user.setUserTitles(title);
        }

        return user;
    }

    private UserBusiness createUserBusiness(User user, Business business, MigrationMemberInput input) {
        UserBusiness ub = new UserBusiness(user, business, input.getUserStripeMemberId(), "ACTIVE");
        ub.setCreatedAt(LocalDateTime.now());
        ub.setHasEverHadMembership(input.getMembership() != null);
        // isDelinquent will be set by Stripe sync or calculateAndUpdateStatus
        return ub;
    }

    private void syncMembershipsFromStripe(UserBusiness userBusiness, String businessTag, MigrationMemberInput input) {
        String stripeId = input.getUserStripeMemberId();
        if (stripeId == null || stripeId.isEmpty()) return;

        try {
            SubscriptionListParams params = SubscriptionListParams.builder()
                    .setCustomer(stripeId)
                    .setStatus(SubscriptionListParams.Status.ALL)
                    .setLimit(100L)
                    .build();
            com.stripe.model.SubscriptionCollection subs = Subscription.list(params);

            for (Subscription sub : subs.getData()) {
                String subId = sub.getId();
                String stripeStatus = sub.getStatus();
                String dbStatus = mapStripeStatus(stripeStatus);
                boolean hasPastDue = "past_due".equalsIgnoreCase(stripeStatus) || "unpaid".equalsIgnoreCase(stripeStatus);
                if (hasPastDue) userBusiness.setIsDelinquent(true);

                if (sub.getItems() == null || sub.getItems().getData().isEmpty()) continue;

                String productName = null;
                BigDecimal totalPrice = BigDecimal.ZERO;
                String chargeInterval = "MONTHLY";

                for (com.stripe.model.SubscriptionItem item : sub.getItems().getData()) {
                    if (item.getPrice() != null) {
                        if (item.getPrice().getUnitAmount() != null) {
                            totalPrice = totalPrice.add(BigDecimal.valueOf(item.getPrice().getUnitAmount()).divide(BigDecimal.valueOf(100)));
                        }
                        if (item.getPrice().getProduct() != null) {
                            try {
                                Product p = Product.retrieve(item.getPrice().getProduct());
                                if (productName == null) productName = p.getName();
                            } catch (Exception ignored) {}
                        }
                        if (item.getPrice().getRecurring() != null && item.getPrice().getRecurring().getInterval() != null) {
                            chargeInterval = item.getPrice().getRecurring().getInterval().toUpperCase();
                        }
                    }
                }

                if (productName == null) productName = "Subscription";

                Membership membership = findOrCreateMembership(businessTag, productName, totalPrice.toString(), chargeInterval);
                UserBusinessMembership ubm = new UserBusinessMembership();
                ubm.setUserBusiness(userBusiness);
                ubm.setMembership(membership);
                ubm.setStatus(dbStatus);
                ubm.setStripeSubscriptionId(subId);
                ubm.setActualPrice(totalPrice);
                ubm.setAnchorDate(LocalDateTime.now());
                userBusiness.addMembership(ubm);
                userBusiness.setHasEverHadMembership(true);
            }
        } catch (StripeException e) {
            logger.warn("Stripe sync failed for {}: {}", stripeId, e.getMessage());
        }
    }

    private void createMembershipFromJson(UserBusiness userBusiness, String businessTag, MigrationMemberInput input) {
        MigrationMemberInput.MembershipInput mi = input.getMembership();
        if (mi == null || mi.getName() == null) return;

        Membership membership = findOrCreateMembership(businessTag, mi.getName(), 
                mi.getPrice() != null ? mi.getPrice() : "0", 
                mi.getChargeInterval() != null ? mi.getChargeInterval() : "MONTHLY");

        boolean isDelinquent = !input.getIsInGoodStanding();
        if (isDelinquent) userBusiness.setIsDelinquent(true);

        UserBusinessMembership ubm = new UserBusinessMembership();
        ubm.setUserBusiness(userBusiness);
        ubm.setMembership(membership);
        ubm.setStatus(input.getIsInGoodStanding() != null && input.getIsInGoodStanding() ? "ACTIVE" : "PAST_DUE");
        ubm.setActualPrice(mi.getPrice() != null ? new BigDecimal(mi.getPrice()) : BigDecimal.ZERO);
        ubm.setAnchorDate(LocalDateTime.now());
        userBusiness.addMembership(ubm);
        userBusiness.setHasEverHadMembership(true);
    }

    private Membership findOrCreateMembership(String businessTag, String name, String price, String chargeInterval) {
        List<Membership> existing = membershipRepository.findByBusinessTag(businessTag);
        Membership match = existing.stream()
                .filter(m -> name.equalsIgnoreCase(m.getTitle()))
                .findFirst()
                .orElse(null);

        if (match != null) return match;

        Membership m = new Membership();
        m.setTitle(name);
        m.setPrice(price);
        m.setChargeInterval(chargeInterval);
        m.setBusinessTag(businessTag);
        return membershipRepository.save(m);
    }

    /**
     * Resolve the waiver signed date from migration input: use waiverSignedDate if present,
     * otherwise createdAt (join date from source data). Falls back to now if neither parses.
     */
    private LocalDateTime resolveWaiverSignedDate(MigrationMemberInput input) {
        String dateStr = (input.getWaiverSignedDate() != null && !input.getWaiverSignedDate().isBlank())
                ? input.getWaiverSignedDate()
                : input.getCreatedAt();
        return parseWaiverSignedDate(dateStr);
    }

    private LocalDateTime parseWaiverSignedDate(String dateStr) {
        if (dateStr == null || dateStr.isBlank()) return LocalDateTime.now();
        try {
            for (DateTimeFormatter fmt : DATE_PARSERS) {
                try {
                    return LocalDateTime.parse(dateStr.replace("Z", ""), fmt);
                } catch (Exception ignored) {}
            }
        } catch (Exception ignored) {}
        return LocalDateTime.now();
    }

    private String mapStripeStatus(String s) {
        if (s == null) return "INACTIVE";
        switch (s.toLowerCase()) {
            case "active": return "ACTIVE";
            case "past_due":
            case "unpaid": return "PAST_DUE";
            case "canceled":
            case "cancelled": return "CANCELLED";
            case "paused": return "PAUSED";
            default: return "INACTIVE";
        }
    }

    private String generateUniqueToken(int len) {
        StringBuilder sb = new StringBuilder(len);
        for (int i = 0; i < len; i++) sb.append(CHARACTERS.charAt(random.nextInt(CHARACTERS.length())));
        String token = sb.toString();
        while (userRepository.findByEntryQrcodeToken(token).isPresent()) {
            sb = new StringBuilder(len);
            for (int i = 0; i < len; i++) sb.append(CHARACTERS.charAt(random.nextInt(CHARACTERS.length())));
            token = sb.toString();
        }
        return token;
    }

    private String generateUniqueReferralCode() {
        String code;
        do {
            code = generateUniqueToken(10);
        } while (userRepository.findByReferralCode(code) != null);
        return code;
    }
}
