package com.BossLiftingClub.BossLifting.Business;

import com.BossLiftingClub.BossLifting.Email.EmailService;
import com.BossLiftingClub.BossLifting.User.BusinessUser.UserBusiness;
import com.BossLiftingClub.BossLifting.Stripe.StripeService;
import com.BossLiftingClub.BossLifting.User.BusinessUser.UserBusinessMembership;
import com.BossLiftingClub.BossLifting.User.BusinessUser.UserBusinessRepository;
import com.BossLiftingClub.BossLifting.User.BusinessUser.UserBusinessService;
import com.BossLiftingClub.BossLifting.User.User;
import com.BossLiftingClub.BossLifting.User.UserDTO;
import com.BossLiftingClub.BossLifting.User.UserRepository;
import com.BossLiftingClub.BossLifting.User.SignInLog.SignInLog;
import com.BossLiftingClub.BossLifting.User.SignInLog.SignInLogRepository;
import com.stripe.exception.StripeException;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import com.BossLiftingClub.BossLifting.User.BusinessUser.MemberListProjection;

@RestController
@RequestMapping("/api/businesses")
@Validated
public class BusinessController {
    @Autowired
    private BusinessService businessService;

    @Autowired
    private UserBusinessService userBusinessService;

    @Autowired
    private EmailService emailService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserBusinessRepository userBusinessRepository;

    @Autowired
    private StripeService stripeService;

    @Autowired
    private SignInLogRepository signInLogRepository;

    @PostMapping
    public ResponseEntity<?> createBusiness(@Valid @RequestBody BusinessDTO businessDTO) {
        try {
            BusinessDTO createdBusiness = businessService.createBusiness(businessDTO);
            return ResponseEntity.ok(createdBusiness);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", e.getMessage()));
        } catch (EntityNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", e.getMessage()));
        } catch (DataIntegrityViolationException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", "Business tag '" + businessDTO.getBusinessTag() + "' already exists"));
        }
    }

    /**
     * Update a business by ID
     */
    @PutMapping("/{id}")
    public ResponseEntity<?> updateBusiness(@PathVariable Long id, @Valid @RequestBody BusinessDTO businessDTO) {
        try {
            BusinessDTO updatedBusiness = businessService.updateBusiness(id, businessDTO);
            return ResponseEntity.ok(updatedBusiness);
        } catch (EntityNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to update business: " + e.getMessage()));
        }
    }

    /**
     * Lightweight member list with pagination. Use this for the members table - avoids loading
     * full UserDTO, referrer, signInLogs, and calculateAndUpdateStatus per member.
     */
    @GetMapping("/{businessTag}/members/list")
    public ResponseEntity<?> getMembersList(@PathVariable String businessTag,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "false") boolean delinquentOnly) {
        try {
            Pageable pageable = PageRequest.of(page, Math.min(size, 100));
            String searchTerm = (search != null && !search.isBlank()) ? search.trim() : "";
            var result = userBusinessRepository.findMemberListByBusinessTag(businessTag, searchTerm, delinquentOnly, pageable);
            List<Map<String, Object>> items = result.getContent().stream()
                    .map(p -> {
                        Map<String, Object> m = new HashMap<>();
                        m.put("userBusinessId", p.getUserBusinessId());
                        m.put("userId", p.getUserId());
                        m.put("firstName", p.getFirstName());
                        m.put("lastName", p.getLastName());
                        m.put("email", p.getEmail());
                        m.put("phoneNumber", p.getPhoneNumber());
                        m.put("profilePictureUrl", p.getProfilePictureUrl());
                        m.put("userType", p.getUserType());
                        m.put("calculatedStatus", p.getCalculatedStatus());
                        m.put("calculatedUserType", p.getCalculatedUserType());
                        m.put("createdAt", p.getCreatedAt());
                        m.put("hasEverHadMembership", p.getHasEverHadMembership() != null && p.getHasEverHadMembership());
                        m.put("isDelinquent", p.getIsDelinquent() != null && p.getIsDelinquent());
                        m.put("stripeId", p.getStripeId() != null ? p.getStripeId() : "");
                        String titles = p.getMembershipTitles();
                        if (titles != null && !titles.isEmpty()) {
                            m.put("membershipTitles", java.util.Arrays.asList(titles.split(",")));
                        } else {
                            m.put("membershipTitles", new ArrayList<String>());
                        }
                        return m;
                    })
                    .collect(Collectors.toList());
            Map<String, Object> response = new HashMap<>();
            response.put("content", items);
            response.put("totalElements", result.getTotalElements());
            response.put("totalPages", result.getTotalPages());
            response.put("size", result.getSize());
            response.put("number", result.getNumber());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to retrieve members list: " + e.getMessage()));
        }
    }

    /**
     * Get member IDs only - for bulk email and similar use cases.
     */
    @GetMapping("/{businessTag}/members/ids")
    public ResponseEntity<?> getMemberIds(@PathVariable String businessTag) {
        try {
            List<Long> ids = userBusinessRepository.findUserBusinessIdsByBusinessTag(businessTag);
            return ResponseEntity.ok(ids);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to retrieve member IDs: " + e.getMessage()));
        }
    }

    /**
     * Get full member details by userBusinessId. Call when a member is selected from the list.
     */
    @GetMapping("/{businessTag}/members/{userBusinessId}")
    @org.springframework.transaction.annotation.Transactional
    public ResponseEntity<?> getMemberById(@PathVariable String businessTag, @PathVariable Long userBusinessId) {
        try {
            Optional<UserBusiness> opt = userBusinessRepository.findByIdAndBusinessTag(userBusinessId, businessTag);
            if (opt.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Member not found"));
            }
            return ResponseEntity.ok(buildFullMemberMap(opt.get()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to retrieve member: " + e.getMessage()));
        }
    }

    /**
     * Get full member by userId (for Analytics failed payment flow).
     */
    @GetMapping("/{businessTag}/members/by-user/{userId}")
    @org.springframework.transaction.annotation.Transactional
    public ResponseEntity<?> getMemberByUserId(@PathVariable String businessTag, @PathVariable Long userId) {
        try {
            Optional<UserBusiness> opt = userBusinessRepository.findByUserIdAndBusinessTag(userId, businessTag);
            if (opt.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Member not found"));
            }
            return ResponseEntity.ok(buildFullMemberMap(opt.get()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to retrieve member: " + e.getMessage()));
        }
    }

    /**
     * Process a QR/barcode scan: look up user by entryQrcodeToken, record scan-in log (5-min cooldown),
     * and return full member for the selected business. Frontend uses this to set the selected user.
     */
    @PostMapping("/{businessTag}/scan-in")
    @org.springframework.transaction.annotation.Transactional
    public ResponseEntity<?> processScanIn(@PathVariable String businessTag, @RequestBody Map<String, Object> body) {
        try {
            Object tokenObj = body != null ? body.get("qrCodeToken") : null;
            if (tokenObj == null || tokenObj.toString().trim().isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "qrCodeToken is required"));
            }
            String rawInput = tokenObj.toString().trim();
            String token = extractTokenFromScan(rawInput);

            Optional<User> userOpt = userRepository.findByEntryQrcodeToken(token);
            if (userOpt.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "User not found for this QR code"));
            }

            Optional<UserBusiness> ubOpt = userBusinessRepository.findByUserIdAndBusinessTag(userOpt.get().getId(), businessTag);
            if (ubOpt.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Member not found in this business"));
            }

            // 5-minute cooldown to prevent double scans
            LocalDateTime fiveMinutesAgo = LocalDateTime.now().minusMinutes(5);
            List<SignInLog> recentLogs = signInLogRepository.findRecentByUserId(userOpt.get().getId(), fiveMinutesAgo);
            if (!recentLogs.isEmpty()) {
                return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
                        "error", "Scan already recorded. Please wait 5 minutes before scanning again.",
                        "code", "COOLDOWN"
                ));
            }

            SignInLog log = new SignInLog();
            log.setUser(userOpt.get());
            log.setSignInTime(LocalDateTime.now());
            signInLogRepository.save(log);

            return ResponseEntity.ok(buildFullMemberMap(ubOpt.get()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Scan failed: " + e.getMessage()));
        }
    }

    /** Extract token from scan result - handles plain token or URL containing token */
    private static String extractTokenFromScan(String raw) {
        if (raw == null || raw.isEmpty()) return raw;
        raw = raw.trim();
        // If it looks like a URL, try to extract token from path (e.g. /entry/TOKEN or ?token=TOKEN)
        if (raw.contains("/") || raw.contains("?") || raw.startsWith("http")) {
            Pattern pathToken = Pattern.compile("/([a-zA-Z0-9_-]{6,})/?$");
            Matcher m = pathToken.matcher(raw);
            if (m.find()) return m.group(1);
            int q = raw.indexOf("token=");
            if (q >= 0) {
                int start = q + 6;
                int end = raw.indexOf("&", start);
                return end > 0 ? raw.substring(start, end).trim() : raw.substring(start).trim();
            }
        }
        return raw;
    }

    private Map<String, Object> buildFullMemberMap(UserBusiness ub) {
        Map<String, Object> member = new HashMap<>();
        UserDTO userDTO = new UserDTO(ub.getUser());
        if (userDTO.getReferredById() != null) {
            User referredBy = ub.getUser().getReferredBy();
            if (referredBy == null) {
                referredBy = userRepository.findById(userDTO.getReferredById()).orElse(null);
            }
            if (referredBy != null) {
                Map<String, Object> referrerInfo = new HashMap<>();
                referrerInfo.put("id", referredBy.getId());
                referrerInfo.put("firstName", referredBy.getFirstName());
                referrerInfo.put("lastName", referredBy.getLastName());
                referrerInfo.put("email", referredBy.getEmail());
                referrerInfo.put("referralCode", referredBy.getReferralCode());
                member.put("referrerInfo", referrerInfo);
            }
        }
        List<Map<String, Object>> memberships = ub.getUserBusinessMemberships().stream()
                .map(ubm -> {
                    Map<String, Object> m = new HashMap<>();
                    m.put("id", ubm.getId());
                    m.put("membershipId", ubm.getMembership().getId());
                    m.put("title", ubm.getMembership().getTitle());
                    m.put("price", ubm.getMembership().getPrice());
                    m.put("chargeInterval", ubm.getMembership().getChargeInterval());
                    m.put("status", ubm.getStatus());
                    Object anchorDate = ubm.getAnchorDate();
                    // Enrich with Stripe current_period_start when available (source of truth for billing)
                    if (ubm.getStripeSubscriptionId() != null && !ubm.getStripeSubscriptionId().isEmpty()) {
                        try {
                            Map<String, Object> stripeDetails = stripeService.retrieveSubscriptionDetails(ubm.getStripeSubscriptionId(), null);
                            Object stripeAnchor = stripeDetails.get("currentPeriodStart");
                            if (stripeAnchor != null) anchorDate = stripeAnchor;
                            Object stripeStatus = stripeDetails.get("status");
                            if (stripeStatus != null) m.put("status", mapStripeStatusToDisplay((String) stripeStatus));
                        } catch (StripeException e) {
                            // Fall back to DB anchor/status if Stripe fetch fails
                        }
                    }
                    m.put("anchorDate", anchorDate);
                    m.put("endDate", ubm.getEndDate());
                    m.put("stripeSubscriptionId", ubm.getStripeSubscriptionId());
                    m.put("pauseStartDate", ubm.getPauseStartDate());
                    m.put("pauseEndDate", ubm.getPauseEndDate());
                    if (ubm.getActualPrice() != null) m.put("actualPrice", ubm.getActualPrice());
                    Double planPrice = parsePriceToDouble(ubm.getMembership().getPrice());
                    if (planPrice != null) m.put("planPrice", planPrice);
                    return m;
                })
                .collect(Collectors.toList());
        userDTO.setMemberships(memberships);
        boolean hasAccount = userBusinessService.hasAccount(ub.getUser());
        String calculatedStatus = ub.getCalculatedStatus();
        String calculatedUserType = ub.getCalculatedUserType();
        boolean waiverSignedButStatusWrong = "Waiver Required".equalsIgnoreCase(calculatedStatus)
                && ub.getUser() != null && ub.getUser().getWaiverSignedDate() != null;
        if (calculatedStatus == null || calculatedUserType == null || waiverSignedButStatusWrong) {
            try {
                userBusinessService.calculateAndUpdateStatus(ub);
                var reloaded = userBusinessRepository.findById(ub.getId());
                if (reloaded.isPresent()) {
                    calculatedStatus = reloaded.get().getCalculatedStatus();
                    calculatedUserType = reloaded.get().getCalculatedUserType();
                }
            } catch (Exception e) {
                System.err.println("Warning: Failed to calculate status for UserBusiness " + ub.getId() + ": " + e.getMessage());
            }
        }
        if (calculatedStatus == null) calculatedStatus = "Inactive";
        if (calculatedUserType == null) calculatedUserType = "Member";
        member.put("user", userDTO);
        member.put("userBusinessId", ub.getId());
        member.put("status", calculatedStatus);
        member.put("calculatedStatus", calculatedStatus);
        member.put("calculatedUserType", calculatedUserType);
        member.put("stripeId", ub.getStripeId() != null ? ub.getStripeId() : "");
        member.put("createdAt", ub.getCreatedAt());
        member.put("hasAccount", hasAccount);
        member.put("hasEverHadMembership", ub.getHasEverHadMembership() != null ? ub.getHasEverHadMembership() : false);
        member.put("isDelinquent", ub.getIsDelinquent() != null ? ub.getIsDelinquent() : false);
        return member;
    }

    private static String mapStripeStatusToDisplay(String stripeStatus) {
        if (stripeStatus == null) return "INACTIVE";
        switch (stripeStatus.toLowerCase()) {
            case "active": return "ACTIVE";
            case "paused": return "PAUSED";
            case "past_due": case "unpaid": return "PAST_DUE";
            case "canceled": case "cancelled": return "CANCELLED";
            case "trialing": return "ACTIVE";
            default: return stripeStatus.toUpperCase();
        }
    }

    /**
     * Get all members (users) for a specific business by businessTag
     * @deprecated Use /members/list for the table and /members/{userBusinessId} for detail.
     */
    @Deprecated
    @GetMapping("/{businessTag}/members")
    @org.springframework.transaction.annotation.Transactional // Allow writes to calculate status if needed
    public ResponseEntity<?> getMembersByBusinessTag(@PathVariable String businessTag) {
        try {
            List<UserBusiness> userBusinesses = userBusinessService.getUsersByBusinessTag(businessTag);

            // Map to DTOs with user information and multiple memberships
            List<Map<String, Object>> members = userBusinesses.stream()
                    .map(ub -> {
                        Map<String, Object> member = new java.util.HashMap<>();
                        UserDTO userDTO = new UserDTO(ub.getUser());
                        
                        // Add referrer information if user was referred
                        if (userDTO.getReferredById() != null) {
                            // Try to get referrer from the loaded relationship first
                            User referredBy = ub.getUser().getReferredBy();
                            
                            // If not loaded, fetch it explicitly
                            if (referredBy == null) {
                                referredBy = userRepository.findById(userDTO.getReferredById()).orElse(null);
                            }
                            
                            if (referredBy != null) {
                                Map<String, Object> referrerInfo = new java.util.HashMap<>();
                                referrerInfo.put("id", referredBy.getId());
                                referrerInfo.put("firstName", referredBy.getFirstName());
                                referrerInfo.put("lastName", referredBy.getLastName());
                                referrerInfo.put("email", referredBy.getEmail());
                                referrerInfo.put("referralCode", referredBy.getReferralCode());
                                // Add referrer info to member map
                                member.put("referrerInfo", referrerInfo);
                            }
                        }

                        // Map memberships from UserBusinessMembership junction table
                        List<Map<String, Object>> memberships = ub.getUserBusinessMemberships().stream()
                                .map(ubm -> {
                                    Map<String, Object> membershipData = new java.util.HashMap<>();
                                    membershipData.put("id", ubm.getId());
                                    membershipData.put("membershipId", ubm.getMembership().getId());
                                    membershipData.put("title", ubm.getMembership().getTitle());
                                    membershipData.put("price", ubm.getMembership().getPrice());
                                    membershipData.put("chargeInterval", ubm.getMembership().getChargeInterval());
                                    membershipData.put("status", ubm.getStatus());
                                    membershipData.put("anchorDate", ubm.getAnchorDate());
                                    membershipData.put("endDate", ubm.getEndDate());
                                    membershipData.put("stripeSubscriptionId", ubm.getStripeSubscriptionId());
                                    membershipData.put("pauseStartDate", ubm.getPauseStartDate());
                                    membershipData.put("pauseEndDate", ubm.getPauseEndDate());
                                    if (ubm.getActualPrice() != null) {
                                        membershipData.put("actualPrice", ubm.getActualPrice());
                                    }
                                    Double planPrice = parsePriceToDouble(ubm.getMembership().getPrice());
                                    if (planPrice != null) {
                                        membershipData.put("planPrice", planPrice);
                                    }
                                    return membershipData;
                                })
                                .collect(Collectors.toList());

                        userDTO.setMemberships(memberships);

                        // Check if user has an account (password is not default "userpass1")
                        boolean hasAccount = userBusinessService.hasAccount(ub.getUser());
                        
                        // Get calculated status and user type from database (calculate if not set)
                        String calculatedStatus = ub.getCalculatedStatus();
                        String calculatedUserType = ub.getCalculatedUserType();
                        
                        // If not calculated yet, or status shows "Waiver Required" but user has signed waiver (stale),
                        // recalculate now. This fixes migrated members whose status wasn't updated after waiver signing.
                        boolean waiverSignedButStatusWrong = "Waiver Required".equalsIgnoreCase(calculatedStatus)
                                && ub.getUser() != null && ub.getUser().getWaiverSignedDate() != null;
                        if (calculatedStatus == null || calculatedUserType == null || waiverSignedButStatusWrong) {
                            try {
                                userBusinessService.calculateAndUpdateStatus(ub);
                                // Reload from database to get updated calculated values
                                Optional<UserBusiness> reloaded = userBusinessRepository.findById(ub.getId());
                                if (reloaded.isPresent()) {
                                    calculatedStatus = reloaded.get().getCalculatedStatus();
                                    calculatedUserType = reloaded.get().getCalculatedUserType();
                                }
                            } catch (Exception e) {
                                // If calculation fails, use defaults (shouldn't happen, but safe fallback)
                                System.err.println("Warning: Failed to calculate status for UserBusiness " + ub.getId() + ": " + e.getMessage());
                            }
                        }
                        
                        // Ensure we have values (fallback if still null)
                        if (calculatedStatus == null) calculatedStatus = "Inactive";
                        if (calculatedUserType == null) calculatedUserType = "Member";
                        
                        member.put("user", userDTO);
                        member.put("userBusinessId", ub.getId());
                        member.put("status", calculatedStatus);
                        member.put("calculatedStatus", calculatedStatus);
                        member.put("calculatedUserType", calculatedUserType);
                        member.put("stripeId", ub.getStripeId() != null ? ub.getStripeId() : "");
                        member.put("createdAt", ub.getCreatedAt());
                        member.put("hasAccount", hasAccount);
                        member.put("hasEverHadMembership", ub.getHasEverHadMembership() != null ? ub.getHasEverHadMembership() : false);
                        member.put("isDelinquent", ub.getIsDelinquent() != null ? ub.getIsDelinquent() : false);
                        return member;
                    })
                    .collect(Collectors.toList());

            return ResponseEntity.ok(members);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to retrieve members: " + e.getMessage()));
        }
    }

    /**
     * Update the status of a user-business relationship
     */
    @Autowired
    private com.BossLiftingClub.BossLifting.User.BusinessUser.StripeSyncService stripeSyncService;

    /**
     * Sync from Stripe and recalculate status for all members of a business
     * This will:
     * 1. Sync subscription statuses from Stripe (update/remove memberships based on Stripe state)
     * 2. Recalculate status for all members
     */
    @PostMapping("/{businessTag}/recalculate-statuses")
    public ResponseEntity<?> recalculateAllMemberStatuses(@PathVariable String businessTag) {
        try {
            // First, sync from Stripe to update memberships based on current Stripe state
            Map<String, Object> syncResult = stripeSyncService.syncBusinessFromStripe(businessTag);
            
            if (!Boolean.TRUE.equals(syncResult.get("success"))) {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body(Map.of("error", syncResult.get("error") != null ? syncResult.get("error") : "Failed to sync from Stripe"));
            }
            
            // Then recalculate statuses for all members
            List<UserBusiness> userBusinesses = userBusinessRepository.findAllByBusinessTag(businessTag);
            
            int recalculated = 0;
            for (UserBusiness userBusiness : userBusinesses) {
                try {
                    userBusinessService.calculateAndUpdateStatus(userBusiness);
                    recalculated++;
                } catch (Exception e) {
                    System.err.println("Error recalculating status for UserBusiness " + userBusiness.getId() + ": " + e.getMessage());
                }
            }
            
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Synced from Stripe and recalculated status for " + recalculated + " members",
                    "synced", syncResult.get("synced"),
                    "updated", syncResult.get("updated"),
                    "recalculated", recalculated,
                    "total", userBusinesses.size()
            ));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to sync and recalculate statuses: " + e.getMessage()));
        }
    }

    @PutMapping("/{businessTag}/members/{userId}/status")
    public ResponseEntity<?> updateMemberStatus(
            @PathVariable String businessTag,
            @PathVariable Long userId,
            @RequestBody Map<String, String> request) {
        try {
            String newStatus = request.get("status");
            if (newStatus == null || newStatus.trim().isEmpty()) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(Map.of("error", "Status is required"));
            }

            // Validate status
            List<String> validStatuses = List.of("ACTIVE", "INACTIVE", "CANCELLED", "PENDING");
            if (!validStatuses.contains(newStatus.toUpperCase())) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(Map.of("error", "Invalid status. Must be one of: ACTIVE, INACTIVE, CANCELLED, PENDING"));
            }

            UserBusiness updatedRelationship = userBusinessService.updateUserBusinessStatus(userId, businessTag, newStatus.toUpperCase());

            return ResponseEntity.ok(Map.of(
                    "message", "Member status updated successfully",
                    "userId", userId,
                    "businessTag", businessTag,
                    "newStatus", updatedRelationship.getStatus()
            ));
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to update member status: " + e.getMessage()));
        }
    }

    /**
     * Remove a user from a business
     */
    @DeleteMapping("/{businessTag}/members/{userId}")
    public ResponseEntity<?> removeMemberFromBusiness(
            @PathVariable String businessTag,
            @PathVariable Long userId) {
        try {
            userBusinessService.removeUserFromBusiness(userId, businessTag);

            return ResponseEntity.ok(Map.of(
                    "message", "Member removed from business successfully",
                    "userId", userId,
                    "businessTag", businessTag
            ));
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to remove member from business: " + e.getMessage()));
        }
    }

    /**
     * Single-tenant: Stripe onboarding and per-business dashboard links have been removed.
     * Stripe is configured at the platform level with one account.
     */

    @GetMapping("/id/{id}")
    public ResponseEntity<?> getBusinessById(@PathVariable Long id) {
        try {
            BusinessDTO business = businessService.getBusinessById(id);
            return ResponseEntity.ok(business);
        } catch (EntityNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/tag/{businessTag}")
    public ResponseEntity<?> getBusinessByTag(@PathVariable String businessTag) {
        try {
            BusinessDTO business = businessService.getBusinessByTag(businessTag);
            return ResponseEntity.ok(business);
        } catch (EntityNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Send a white-labeled email to a specific member.
     */
    @PostMapping("/{businessTag}/email/send-single")
    public ResponseEntity<?> sendSingleEmail(
            @PathVariable String businessTag,
            @RequestBody EmailRequest request) {
        try {
            if (request.getUserBusinessId() == null) {
                return ResponseEntity.badRequest().body(Map.of("error", "userBusinessId is required"));
            }
            if (request.getSubject() == null || request.getSubject().trim().isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "subject is required"));
            }
            if (request.getBody() == null || request.getBody().trim().isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "body is required"));
            }

            // 1. Retrieve UserBusiness to get both User and Business context
            UserBusiness userBusiness = userBusinessService.getUserBusinessById(request.getUserBusinessId())
                    .orElseThrow(() -> new EntityNotFoundException("Member not found with ID: " + request.getUserBusinessId()));

            // 2. Verify business tag matches (security check)
            if (!userBusiness.getBusiness().getBusinessTag().equals(businessTag)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Member does not belong to this business"));
            }

            // 3. Get recipient email
            String recipientEmail = userBusiness.getUser().getEmail();
            if (recipientEmail == null || recipientEmail.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "Member has no email address"));
            }

            // 4. Get Business white-label details
            Business business = userBusiness.getBusiness();
            String businessName = business.getTitle();
            String businessReplyTo = business.getContactEmail();
            String contactEmail = business.getContactEmail();

            // Fallback if contact email is not set
            if (businessReplyTo == null || businessReplyTo.isEmpty()) {
                 // Optional: use a default or fail. For now, let's use a noreply or just proceed with caution
                 // But sendBlastEmail might require it. Let's use system default or throw error.
                 // Better to require it, but for now let's use a placeholder or the business tag constructed email if valid
                 // businessReplyTo = "support@" + businessTag + ".com"; // Risky if not real.
                 // Let's require it for white labeling, or fallback to system default logic in EmailService if we allowed nulls there (we didn't).
                 // Assuming BusinessService ensures it or we check here.
                 // Let's fallback to a generic one if missing to prevent crash, or error out.
                 // Erroring out encourages them to set it up.
                 // But wait, older businesses might not have it.
                 // Let's check if we can use a default.
                 businessReplyTo = "noreply@recrev.com"; // Safe fallback?
            }

            // 5. Send email
            emailService.sendBlastEmail(recipientEmail, request.getSubject(), request.getBody(), businessName, businessReplyTo, contactEmail);

            return ResponseEntity.ok(Map.of("message", "Email sent successfully to " + recipientEmail));

        } catch (EntityNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "Failed to send email: " + e.getMessage()));
        }
    }

    // Request DTO
    public static class EmailRequest {
        private Long userBusinessId;
        private String subject;
        private String body;

        public Long getUserBusinessId() {
            return userBusinessId;
        }

        public void setUserBusinessId(Long userBusinessId) {
            this.userBusinessId = userBusinessId;
        }

        public String getSubject() {
            return subject;
        }

        public void setSubject(String subject) {
            this.subject = subject;
        }

        public String getBody() {
            return body;
        }

        public void setBody(String body) {
            this.body = body;
        }
    }

    /**
     * Send white-labeled emails to multiple members.
     */
    @PostMapping("/{businessTag}/email/send-bulk")
    public ResponseEntity<?> sendBulkEmail(
            @PathVariable String businessTag,
            @RequestBody BulkEmailRequest request) {
        try {
            if (request.getUserBusinessIds() == null || request.getUserBusinessIds().isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "userBusinessIds list is required"));
            }
            if (request.getSubject() == null || request.getSubject().trim().isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "subject is required"));
            }
            if (request.getBody() == null || request.getBody().trim().isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "body is required"));
            }

            int successCount = 0;
            int failCount = 0;
            List<String> failedEmails = new java.util.ArrayList<>();

            // Get business details once
            BusinessDTO businessDTO = businessService.getBusinessByTag(businessTag);
            String businessName = businessDTO.getTitle();
            String businessReplyTo = businessDTO.getContactEmail();

            if (businessReplyTo == null || businessReplyTo.isEmpty()) {
                businessReplyTo = "noreply@recrev.com"; 
            }

            for (Long userBusinessId : request.getUserBusinessIds()) {
                try {
                    UserBusiness userBusiness = userBusinessService.getUserBusinessById(userBusinessId)
                            .orElseThrow(() -> new EntityNotFoundException("Member not found with ID: " + userBusinessId));

                    if (!userBusiness.getBusiness().getBusinessTag().equals(businessTag)) {
                        failCount++;
                        continue;
                    }

                    String recipientEmail = userBusiness.getUser().getEmail();
                    if (recipientEmail == null || recipientEmail.isEmpty()) {
                        failCount++;
                        continue;
                    }

                    emailService.sendBlastEmail(recipientEmail, request.getSubject(), request.getBody(), businessName, businessReplyTo, businessDTO.getContactEmail());
                    successCount++;
                } catch (Exception e) {
                    failCount++;
                    failedEmails.add("ID " + userBusinessId + ": " + e.getMessage());
                }
            }

            return ResponseEntity.ok(Map.of(
                    "message", "Bulk email processing completed",
                    "sent", successCount,
                    "failed", failCount,
                    "failedDetails", failedEmails
            ));

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "Failed to process bulk email: " + e.getMessage()));
        }
    }

    public static class BulkEmailRequest {
        private List<Long> userBusinessIds;
        private String subject;
        private String body;

        public List<Long> getUserBusinessIds() {
            return userBusinessIds;
        }

        public void setUserBusinessIds(List<Long> userBusinessIds) {
            this.userBusinessIds = userBusinessIds;
        }

        public String getSubject() {
            return subject;
        }

        public void setSubject(String subject) {
            this.subject = subject;
        }

        public String getBody() {
            return body;
        }

        public void setBody(String body) {
            this.body = body;
        }
    }

    /**
     * Get business by businessTag (for public join page)
     */
    @GetMapping("/public/{businessTag}")
    public ResponseEntity<?> getPublicBusinessByTag(@PathVariable String businessTag) {
        try {
            BusinessDTO businessDTO = businessService.getBusinessByTag(businessTag);
            // Only return public-facing information
            Map<String, Object> publicInfo = new java.util.HashMap<>();
            publicInfo.put("title", businessDTO.getTitle());
            publicInfo.put("logoUrl", businessDTO.getLogoUrl());
            publicInfo.put("businessTag", businessDTO.getBusinessTag());
            return ResponseEntity.ok(publicInfo);
        } catch (EntityNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "Business not found with tag: " + businessTag));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to retrieve business: " + e.getMessage()));
        }
    }

    private Double parsePriceToDouble(String price) {
        if (price == null || price.trim().isEmpty()) {
            return null;
        }
        try {
            String normalized = price.replaceAll("[^0-9.]", "");
            if (normalized.isEmpty()) {
                return null;
            }
            return Double.parseDouble(normalized);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
