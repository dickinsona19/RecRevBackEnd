package com.BossLiftingClub.BossLifting.User.BusinessUser;

import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Controller for managing UserBusinessMembership relationships
 * (memberships assigned to users within a business)
 */
@RestController
@RequestMapping("/api/businesses/{businessTag}/members/{userId}/memberships")
public class UserBusinessMembershipController {

    @Autowired
    private UserBusinessService userBusinessService;

    /**
     * Get all memberships for a specific user in a business
     * GET /api/businesses/{businessTag}/members/{userId}/memberships
     */
    @GetMapping
    public ResponseEntity<?> getUserMemberships(
            @PathVariable String businessTag,
            @PathVariable Long userId) {
        try {
            List<UserBusinessMembership> memberships = userBusinessService.getUserMembershipsInBusiness(userId, businessTag);
            return ResponseEntity.ok(memberships);
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to retrieve memberships: " + e.getMessage()));
        }
    }

    /**
     * Add a new membership to an existing user in a business
     * POST /api/businesses/{businessTag}/members/{userId}/memberships
     *
     * Request body:
     * {
     *   "membershipId": 1,
     *   "status": "ACTIVE",
     *   "anchorDate": "2025-11-03T00:00:00",
     *   "endDate": "2026-11-03T00:00:00",  // optional
     *   "stripeSubscriptionId": "sub_123"   // optional
     * }
     */
    @PostMapping
    public ResponseEntity<?> addMembershipToUser(
            @PathVariable String businessTag,
            @PathVariable Long userId,
            @Valid @RequestBody MembershipAssignmentRequest request) {
        try {
            // Validate required fields
            if (request.getMembershipId() == null) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "membershipId is required"));
            }

            BigDecimal overridePrice = request.getCustomPrice() != null ? BigDecimal.valueOf(request.getCustomPrice()) : null;
            if (overridePrice != null && overridePrice.compareTo(BigDecimal.ZERO) <= 0) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "customPrice must be greater than 0"));
            }

            UserBusinessMembership membership = userBusinessService.addMembershipToUser(
                    userId,
                    businessTag,
                    request.getMembershipId(),
                    request.getStatus() != null ? request.getStatus() : "ACTIVE",
                    request.getAnchorDate() != null ? request.getAnchorDate() : LocalDateTime.now(),
                    request.getEndDate(),
                    request.getStripeSubscriptionId(),
                    overridePrice
            );

            return ResponseEntity.status(HttpStatus.CREATED).body(membership);
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to add membership: " + e.getMessage()));
        }
    }

    /**
     * Update an existing membership for a user in a business
     * PUT /api/businesses/{businessTag}/members/{userId}/memberships/{membershipId}
     *
     * Request body:
     * {
     *   "status": "INACTIVE",
     *   "anchorDate": "2025-11-03T00:00:00",
     *   "endDate": "2026-11-03T00:00:00",
     *   "stripeSubscriptionId": "sub_123"
     * }
     */
    @PutMapping("/{userBusinessMembershipId}")
    public ResponseEntity<?> updateUserMembership(
            @PathVariable String businessTag,
            @PathVariable Long userId,
            @PathVariable Long userBusinessMembershipId,
            @Valid @RequestBody MembershipUpdateRequest request) {
        try {
            UserBusinessMembership updated = userBusinessService.updateUserBusinessMembership(
                    userId,
                    businessTag,
                    userBusinessMembershipId,
                    request.getStatus(),
                    request.getAnchorDate(),
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
     * Delete a specific membership from a user in a business
     * DELETE /api/businesses/{businessTag}/members/{userId}/memberships/{membershipId}
     */
    @DeleteMapping("/{userBusinessMembershipId}")
    public ResponseEntity<?> deleteUserMembership(
            @PathVariable String businessTag,
            @PathVariable Long userId,
            @PathVariable Long userBusinessMembershipId) {
        try {
            userBusinessService.removeMembershipFromUser(userId, businessTag, userBusinessMembershipId);

            return ResponseEntity.ok(Map.of(
                    "message", "Membership removed successfully",
                    "userId", userId,
                    "businessTag", businessTag,
                    "userBusinessMembershipId", userBusinessMembershipId
            ));
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to delete membership: " + e.getMessage()));
        }
    }

    // DTOs for request bodies
    public static class MembershipAssignmentRequest {
        private Long membershipId;
        private String status;
        private LocalDateTime anchorDate;
        private LocalDateTime endDate;
        private String stripeSubscriptionId;
        private Double customPrice;

        // Getters and Setters
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

        public LocalDateTime getAnchorDate() {
            return anchorDate;
        }

        public void setAnchorDate(LocalDateTime anchorDate) {
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

        public Double getCustomPrice() {
            return customPrice;
        }

        public void setCustomPrice(Double customPrice) {
            this.customPrice = customPrice;
        }
    }

    public static class MembershipUpdateRequest {
        private String status;
        private LocalDateTime anchorDate;
        private LocalDateTime endDate;
        private String stripeSubscriptionId;

        // Getters and Setters
        public String getStatus() {
            return status;
        }

        public void setStatus(String status) {
            this.status = status;
        }

        public LocalDateTime getAnchorDate() {
            return anchorDate;
        }

        public void setAnchorDate(LocalDateTime anchorDate) {
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
    }
}
