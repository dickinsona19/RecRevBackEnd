package com.BossLiftingClub.BossLifting.Business;

import com.BossLiftingClub.BossLifting.Email.EmailService;
import com.BossLiftingClub.BossLifting.User.BusinessUser.UserBusiness;
import com.BossLiftingClub.BossLifting.User.BusinessUser.UserBusinessRepository;
import com.BossLiftingClub.BossLifting.User.BusinessUser.UserBusinessService;
import com.BossLiftingClub.BossLifting.User.User;
import com.BossLiftingClub.BossLifting.User.UserDTO;
import com.BossLiftingClub.BossLifting.User.UserRepository;
import com.stripe.exception.StripeException;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

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
     * Get all members (users) for a specific business by businessTag
     */
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
                        
                        // If not calculated yet, calculate it now (for existing records or if calculation was missed)
                        // This ensures all records have calculated values, even old ones
                        if (calculatedStatus == null || calculatedUserType == null) {
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
     * Create Stripe onboarding link for a business
     * This initiates or continues the Stripe Express account setup process
     */
    @PostMapping("/{businessTag}/stripe-onboarding")
    public ResponseEntity<?> createStripeOnboardingLink(
            @PathVariable String businessTag,
            @RequestBody Map<String, String> request) {
        try {
            String returnUrl = request.get("returnUrl");
            String refreshUrl = request.get("refreshUrl");

            if (returnUrl == null || returnUrl.trim().isEmpty()) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(Map.of("error", "returnUrl is required"));
            }

            if (refreshUrl == null || refreshUrl.trim().isEmpty()) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(Map.of("error", "refreshUrl is required"));
            }

            String onboardingUrl = businessService.createStripeOnboardingLink(businessTag, returnUrl, refreshUrl);

            return ResponseEntity.ok(Map.of(
                    "url", onboardingUrl,
                    "message", "Stripe onboarding link created successfully"
            ));
        } catch (EntityNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", e.getMessage()));
        } catch (StripeException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Stripe error: " + e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to create onboarding link: " + e.getMessage()));
        }
    }

    /**
     * Get Stripe Express Dashboard link for a business
     * Allows business owners to access their Stripe dashboard to view payments, payouts, etc.
     */
    @GetMapping("/{businessTag}/stripe-dashboard-link")
    public ResponseEntity<?> getStripeDashboardLink(@PathVariable String businessTag) {
        try {
            String dashboardUrl = businessService.createStripeDashboardLink(businessTag);

            return ResponseEntity.ok(Map.of(
                    "url", dashboardUrl,
                    "message", "Stripe dashboard link created successfully"
            ));
        } catch (EntityNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", e.getMessage()));
        } catch (StripeException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Stripe error: " + e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to create dashboard link: " + e.getMessage()));
        }
    }

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
