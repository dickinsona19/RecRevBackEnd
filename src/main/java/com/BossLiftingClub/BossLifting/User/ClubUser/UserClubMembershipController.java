package com.BossLiftingClub.BossLifting.User.ClubUser;

import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Controller for managing UserClubMembership relationships
 * (memberships assigned to users within a club)
 */
@RestController
@RequestMapping("/api/clubs/{clubTag}/members/{userId}/memberships")
public class UserClubMembershipController {

    @Autowired
    private UserClubService userClubService;

    /**
     * Get all memberships for a specific user in a club
     * GET /api/clubs/{clubTag}/members/{userId}/memberships
     */
    @GetMapping
    public ResponseEntity<?> getUserMemberships(
            @PathVariable String clubTag,
            @PathVariable Long userId) {
        try {
            List<UserClubMembership> memberships = userClubService.getUserMembershipsInClub(userId, clubTag);
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
     * Add a new membership to an existing user in a club
     * POST /api/clubs/{clubTag}/members/{userId}/memberships
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
            @PathVariable String clubTag,
            @PathVariable Long userId,
            @Valid @RequestBody MembershipAssignmentRequest request) {
        try {
            // Validate required fields
            if (request.getMembershipId() == null) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "membershipId is required"));
            }

            UserClubMembership membership = userClubService.addMembershipToUser(
                    userId,
                    clubTag,
                    request.getMembershipId(),
                    request.getStatus() != null ? request.getStatus() : "ACTIVE",
                    request.getAnchorDate() != null ? request.getAnchorDate() : LocalDateTime.now(),
                    request.getEndDate(),
                    request.getStripeSubscriptionId()
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
     * Update an existing membership for a user in a club
     * PUT /api/clubs/{clubTag}/members/{userId}/memberships/{membershipId}
     *
     * Request body:
     * {
     *   "status": "INACTIVE",
     *   "anchorDate": "2025-11-03T00:00:00",
     *   "endDate": "2026-11-03T00:00:00",
     *   "stripeSubscriptionId": "sub_123"
     * }
     */
    @PutMapping("/{userClubMembershipId}")
    public ResponseEntity<?> updateUserMembership(
            @PathVariable String clubTag,
            @PathVariable Long userId,
            @PathVariable Long userClubMembershipId,
            @Valid @RequestBody MembershipUpdateRequest request) {
        try {
            UserClubMembership updated = userClubService.updateUserClubMembership(
                    userId,
                    clubTag,
                    userClubMembershipId,
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
     * Delete a specific membership from a user in a club
     * DELETE /api/clubs/{clubTag}/members/{userId}/memberships/{membershipId}
     */
    @DeleteMapping("/{userClubMembershipId}")
    public ResponseEntity<?> deleteUserMembership(
            @PathVariable String clubTag,
            @PathVariable Long userId,
            @PathVariable Long userClubMembershipId) {
        try {
            userClubService.removeMembershipFromUser(userId, clubTag, userClubMembershipId);

            return ResponseEntity.ok(Map.of(
                    "message", "Membership removed successfully",
                    "userId", userId,
                    "clubTag", clubTag,
                    "userClubMembershipId", userClubMembershipId
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
