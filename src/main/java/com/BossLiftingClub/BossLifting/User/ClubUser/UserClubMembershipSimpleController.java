package com.BossLiftingClub.BossLifting.User.ClubUser;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Simplified controller for managing UserClubMembership with direct ID access
 */
@RestController
@RequestMapping("/api/user-club-memberships")
public class UserClubMembershipSimpleController {

    @Autowired
    private UserClubService userClubService;

    @Autowired
    private UserClubRepository userClubRepository;

    /**
     * Add a new membership to a user's club relationship
     * POST /api/user-club-memberships
     *
     * Request body:
     * {
     *   "userClubId": 1,
     *   "membershipId": 2,
     *   "status": "ACTIVE"
     * }
     */
    @PostMapping
    public ResponseEntity<?> addMembership(@Valid @RequestBody MembershipAddRequest request) {
        try {
            // Validate required fields
            if (request.getUserClubId() == null) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "userClubId is required"));
            }
            if (request.getMembershipId() == null) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "membershipId is required"));
            }

            // Parse anchor date from string (YYYY-MM-DD) to LocalDateTime
            LocalDateTime anchorDateTime = null;
            if (request.getAnchorDate() != null && !request.getAnchorDate().isEmpty()) {
                anchorDateTime = java.time.LocalDate.parse(request.getAnchorDate()).atStartOfDay();
            } else {
                anchorDateTime = LocalDateTime.now();
            }

            UserClubMembership membership = userClubService.addMembershipByUserClubId(
                    request.getUserClubId(),
                    request.getMembershipId(),
                    request.getStatus() != null ? request.getStatus() : "ACTIVE",
                    anchorDateTime
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
     * PUT /api/user-club-memberships/{id}
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

            UserClubMembership updated = userClubService.updateUserClubMembershipById(
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
     * DELETE /api/user-club-memberships/{id}
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteMembership(@PathVariable Long id) {
        try {
            userClubService.removeMembershipById(id, false); // false = cancel immediately

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
     * POST /api/user-club-memberships/{id}/cancel-at-period-end
     */
    @PostMapping("/{id}/cancel-at-period-end")
    public ResponseEntity<?> cancelMembershipAtPeriodEnd(@PathVariable Long id) {
        try {
            UserClubMembership membership = userClubService.removeMembershipById(id, true); // true = cancel at period end

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
     * GET /api/user-club-memberships/{id}
     */
    @GetMapping("/{id}")
    public ResponseEntity<?> getMembership(@PathVariable Long id) {
        try {
            UserClubMembership membership = userClubService.getUserClubMembershipById(id);
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
     * POST /api/user-club-memberships/{id}/pause
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

            UserClubMembership paused = userClubService.pauseMembership(id, request.getPauseDurationWeeks());
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
     * POST /api/user-club-memberships/{id}/resume
     */
    @PostMapping("/{id}/resume")
    public ResponseEntity<?> resumeMembership(@PathVariable Long id) {
        try {
            UserClubMembership resumed = userClubService.resumeMembership(id);
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
        @NotNull(message = "userClubId is required")
        private Long userClubId;
        
        @NotNull(message = "membershipId is required")
        private Long membershipId;
        
        private String status;
        private String anchorDate;

        // Getters and Setters
        public Long getUserClubId() {
            return userClubId;
        }

        public void setUserClubId(Long userClubId) {
            this.userClubId = userClubId;
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
    }

    public static class MembershipUpdateRequest {
        private String status;
        private String anchorDate; // Changed to String to accept YYYY-MM-DD format
        private LocalDateTime endDate;
        private String stripeSubscriptionId;

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
