package com.BossLiftingClub.BossLifting.User.BusinessUser;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * Simplified controller for managing UserBusinessMembership with direct ID access
 */
@RestController
@RequestMapping("/api/user-business-memberships")
public class UserBusinessMembershipSimpleController {

    @Autowired
    private UserBusinessService userBusinessService;

    @Autowired
    private UserBusinessRepository userBusinessRepository;

    /**
     * Apply a promo code to an existing membership
     * POST /api/user-business-memberships/{id}/promo
     * Request body: { "promoCode": "SAVE50" }
     */
    @PostMapping("/{id}/promo")
    public ResponseEntity<?> applyPromoCode(@PathVariable Long id, @RequestBody Map<String, String> request) {
        try {
            String promoCode = request.get("promoCode");
            if (promoCode == null || promoCode.trim().isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "promoCode is required"));
            }

            UserBusinessMembership updated = userBusinessService.applyPromoCodeToMembership(id, promoCode);
            return ResponseEntity.ok(updated);
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to apply promo code: " + e.getMessage()));
        }
    }

    /**
     * Add a new membership to a user's business relationship
     * POST /api/user-business-memberships
     *
     * Request body:
     * {
     *   "userBusinessId": 1,
     *   "membershipId": 2,
     *   "status": "ACTIVE"
     * }
     */
    @PostMapping
    public ResponseEntity<?> addMembership(@Valid @RequestBody MembershipAddRequest request) {
        try {
            // Validate required fields
            if (request.getUserBusinessId() == null) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "userBusinessId is required"));
            }
            if (request.getMembershipId() == null) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "membershipId is required"));
            }

            // Parse anchor date from string (YYYY-MM-DD) to LocalDateTime
            LocalDateTime anchorDateTime = null;
            if (request.getAnchorDate() != null && !request.getAnchorDate().isEmpty()) {
                LocalDate requestedDate = java.time.LocalDate.parse(request.getAnchorDate());
                if (requestedDate.equals(LocalDate.now())) {
                    anchorDateTime = LocalDateTime.now();
                } else {
                    anchorDateTime = requestedDate.atStartOfDay();
                }
            } else {
                anchorDateTime = LocalDateTime.now();
            }

            BigDecimal overridePrice = request.getCustomPrice() != null ? BigDecimal.valueOf(request.getCustomPrice()) : null;
            if (overridePrice != null && overridePrice.compareTo(BigDecimal.ZERO) <= 0) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "customPrice must be greater than 0"));
            }

            UserBusinessMembership membership = userBusinessService.addMembershipByUserBusinessId(
                    request.getUserBusinessId(),
                    request.getMembershipId(),
                    request.getStatus() != null ? request.getStatus() : "ACTIVE",
                    anchorDateTime,
                    overridePrice,
                    request.getPromoCode(),
                    request.getSignatureDataUrl(),
                    request.getSignerName()
            );

            return ResponseEntity.status(HttpStatus.CREATED).body(membership);
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to add membership: " + e.getMessage()));
        }
    }

    /**
     * Update an existing membership by its ID
     * PUT /api/user-business-memberships/{id}
     */
    @PutMapping("/{id}")
    public ResponseEntity<?> updateMembership(
            @PathVariable Long id,
            @Valid @RequestBody MembershipUpdateRequest request) {
        try {
            // Convert anchorDate from string (YYYY-MM-DD) to LocalDateTime
            LocalDateTime anchorDateTime = null;
            if (request.getAnchorDate() != null && !request.getAnchorDate().isEmpty()) {
                anchorDateTime = java.time.LocalDate.parse(request.getAnchorDate()).atStartOfDay();
            }

            UserBusinessMembership updated = userBusinessService.updateUserBusinessMembershipById(
                    id,
                    request.getStatus(),
                    anchorDateTime,
                    request.getEndDate(),
                    request.getStripeSubscriptionId()
            );

            return ResponseEntity.ok(updated);
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to update membership: " + e.getMessage()));
        }
    }

    /**
     * Delete a membership by its ID (immediate cancellation)
     * DELETE /api/user-business-memberships/{id}
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteMembership(@PathVariable Long id) {
        try {
            userBusinessService.removeMembershipById(id, false); // false = cancel immediately

            return ResponseEntity.ok(Map.of(
                    "message", "Membership cancelled successfully",
                    "id", id
            ));
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to delete membership: " + e.getMessage()));
        }
    }

    /**
     * Cancel a membership at the end of the billing period
     * POST /api/user-business-memberships/{id}/cancel-at-period-end
     */
    @PostMapping("/{id}/cancel-at-period-end")
    public ResponseEntity<?> cancelMembershipAtPeriodEnd(@PathVariable Long id) {
        try {
            UserBusinessMembership membership = userBusinessService.removeMembershipById(id, true); // true = cancel at period end

            return ResponseEntity.ok(Map.of(
                    "message", "Membership will be cancelled at the end of the billing period",
                    "id", id,
                    "cancelAt", membership.getEndDate()
            ));
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to schedule membership cancellation: " + e.getMessage()));
        }
    }

    /**
     * Get a specific membership by its ID
     * GET /api/user-business-memberships/{id}
     */
    @GetMapping("/{id}")
    public ResponseEntity<?> getMembership(@PathVariable Long id) {
        try {
            UserBusinessMembership membership = userBusinessService.getUserBusinessMembershipById(id);
            return ResponseEntity.ok(membership);
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to retrieve membership: " + e.getMessage()));
        }
    }

    /**
     * Pause a membership for a specified duration
     * POST /api/user-business-memberships/{id}/pause
     *
     * Request body:
     * {
     *   "pauseDurationWeeks": 1  // Number of weeks to pause
     * }
     */
    @PostMapping("/{id}/pause")
    public ResponseEntity<?> pauseMembership(
            @PathVariable Long id,
            @Valid @RequestBody MembershipPauseRequest request) {
        try {
            if (request.getPauseDurationWeeks() == null || request.getPauseDurationWeeks() <= 0) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "pauseDurationWeeks must be a positive number"));
            }

            UserBusinessMembership paused = userBusinessService.pauseMembership(id, request.getPauseDurationWeeks());
            return ResponseEntity.ok(paused);
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to pause membership: " + e.getMessage()));
        }
    }

    /**
     * Resume a paused membership
     * POST /api/user-business-memberships/{id}/resume
     */
    @PostMapping("/{id}/resume")
    public ResponseEntity<?> resumeMembership(@PathVariable Long id) {
        try {
            UserBusinessMembership resumed = userBusinessService.resumeMembership(id);
            return ResponseEntity.ok(resumed);
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to resume membership: " + e.getMessage()));
        }
    }

    // DTOs for request bodies
    public static class MembershipAddRequest {
        @NotNull(message = "userBusinessId is required")
        private Long userBusinessId;
        
        @NotNull(message = "membershipId is required")
        private Long membershipId;
        
        private String status;
        private String anchorDate;
        private String promoCode;
        private Double customPrice;
        private String signatureDataUrl; // Base64 signature image
        private String signerName; // Name of person who signed

        // Getters and Setters
        public Long getUserBusinessId() {
            return userBusinessId;
        }

        public void setUserBusinessId(Long userBusinessId) {
            this.userBusinessId = userBusinessId;
        }

        public Long getMembershipId() {
            return membershipId;
        }

        public void setMembershipId(Long membershipId) {
            this.membershipId = membershipId;
        }

        public String getStatus() {
            return status;
        }

        public void setStatus(String status) {
            this.status = status;
        }

        public String getAnchorDate() {
            return anchorDate;
        }

        public void setAnchorDate(String anchorDate) {
            this.anchorDate = anchorDate;
        }

        public String getPromoCode() {
            return promoCode;
        }

        public void setPromoCode(String promoCode) {
            this.promoCode = promoCode;
        }

        public Double getCustomPrice() {
            return customPrice;
        }

        public void setCustomPrice(Double customPrice) {
            this.customPrice = customPrice;
        }

        public String getSignatureDataUrl() {
            return signatureDataUrl;
        }

        public void setSignatureDataUrl(String signatureDataUrl) {
            this.signatureDataUrl = signatureDataUrl;
        }

        public String getSignerName() {
            return signerName;
        }

        public void setSignerName(String signerName) {
            this.signerName = signerName;
        }
    }

    public static class MembershipUpdateRequest {
        private String status;
        private String anchorDate; // Changed to String to accept YYYY-MM-DD format
        private LocalDateTime endDate;
        private String stripeSubscriptionId;
        private String promoCode;

        // Getters and Setters
        public String getStatus() {
            return status;
        }

        public void setStatus(String status) {
            this.status = status;
        }

        public String getAnchorDate() {
            return anchorDate;
        }

        public void setAnchorDate(String anchorDate) {
            this.anchorDate = anchorDate;
        }

        public LocalDateTime getEndDate() {
            return endDate;
        }

        public void setEndDate(LocalDateTime endDate) {
            this.endDate = endDate;
        }

        public String getStripeSubscriptionId() {
            return stripeSubscriptionId;
        }

        public void setStripeSubscriptionId(String stripeSubscriptionId) {
            this.stripeSubscriptionId = stripeSubscriptionId;
        }

        public String getPromoCode() {
            return promoCode;
        }

        public void setPromoCode(String promoCode) {
            this.promoCode = promoCode;
        }
    }

    public static class MembershipPauseRequest {
        @NotNull(message = "pauseDurationWeeks is required")
        @Min(value = 1, message = "pauseDurationWeeks must be at least 1")
        private Integer pauseDurationWeeks;

        // Getters and Setters
        public Integer getPauseDurationWeeks() {
            return pauseDurationWeeks;
        }

        public void setPauseDurationWeeks(Integer pauseDurationWeeks) {
            this.pauseDurationWeeks = pauseDurationWeeks;
        }
    }
}
