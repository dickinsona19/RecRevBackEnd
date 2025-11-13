package com.BossLiftingClub.BossLifting.Club;

import com.BossLiftingClub.BossLifting.User.ClubUser.UserClub;
import com.BossLiftingClub.BossLifting.User.ClubUser.UserClubService;
import com.BossLiftingClub.BossLifting.User.UserDTO;
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
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/clubs")
@Validated
public class ClubController {
    @Autowired
    private ClubService clubService;

    @Autowired
    private UserClubService userClubService;

    @PostMapping
    public ResponseEntity<?> createClub(@Valid @RequestBody ClubDTO clubDTO) {
        try {
            ClubDTO createdClub = clubService.createClub(clubDTO);
            return ResponseEntity.ok(createdClub);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", e.getMessage()));
        } catch (EntityNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", e.getMessage()));
        } catch (DataIntegrityViolationException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", "Club tag '" + clubDTO.getClubTag() + "' already exists"));
        }
    }

//    @GetMapping("/{id}")
//    public ResponseEntity<?> getClubById(@PathVariable Integer id) {
//        try {
//            ClubDTO clubDTO = clubService.getClubById(id);
//            return ResponseEntity.ok(clubDTO);
//        } catch (EntityNotFoundException e) {
//            return ResponseEntity.status(HttpStatus.NOT_FOUND)
//                    .body(Map.of("error", e.getMessage()));
//        }
//    }
//
//    @GetMapping
//    public ResponseEntity<List<ClubDTO>> getAllClubs() {
//        return ResponseEntity.ok(clubService.getAllClubs());
//    }

//    @PutMapping("/{id}")
//    public ResponseEntity<?> updateClub(@PathVariable long id, @Valid @RequestBody ClubDTO clubDTO) {
//        try {
//            ClubDTO updatedClub = clubService.updateClub(id, clubDTO);
//            return ResponseEntity.ok(updatedClub);
//        } catch (IllegalArgumentException e) {
//            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
//                    .body(Map.of("error", e.getMessage()));
//        } catch (EntityNotFoundException e) {
//            return ResponseEntity.status(HttpStatus.NOT_FOUND)
//                    .body(Map.of("error", e.getMessage()));
//        } catch (DataIntegrityViolationException e) {
//            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
//                    .body(Map.of("error", "Club tag '" + clubDTO.getClubTag() + "' already exists"));
//        }
//    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteClub(@PathVariable Integer id) {
        try {
            clubService.deleteClub(id);
            return ResponseEntity.ok().build();
        } catch (EntityNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Get all members (users) for a specific club by clubTag
     */
    @GetMapping("/{clubTag}/members")
    public ResponseEntity<?> getMembersByClubTag(@PathVariable String clubTag) {
        try {
            List<UserClub> userClubs = userClubService.getUsersByClubTag(clubTag);

            // Map to DTOs with user information and multiple memberships
            List<Map<String, Object>> members = userClubs.stream()
                    .map(uc -> {
                        Map<String, Object> member = new java.util.HashMap<>();
                        UserDTO userDTO = new UserDTO(uc.getUser());

                        // Map memberships from UserClubMembership junction table
                        List<Map<String, Object>> memberships = uc.getUserClubMemberships().stream()
                                .map(ucm -> {
                                    Map<String, Object> membershipData = new java.util.HashMap<>();
                                    membershipData.put("id", ucm.getId());
                                    membershipData.put("membershipId", ucm.getMembership().getId());
                                    membershipData.put("title", ucm.getMembership().getTitle());
                                    membershipData.put("price", ucm.getMembership().getPrice());
                                    membershipData.put("chargeInterval", ucm.getMembership().getChargeInterval());
                                    membershipData.put("status", ucm.getStatus());
                                    membershipData.put("anchorDate", ucm.getAnchorDate());
                                    membershipData.put("endDate", ucm.getEndDate());
                                    membershipData.put("stripeSubscriptionId", ucm.getStripeSubscriptionId());
                                    membershipData.put("pauseStartDate", ucm.getPauseStartDate());
                                    membershipData.put("pauseEndDate", ucm.getPauseEndDate());
                                    return membershipData;
                                })
                                .collect(Collectors.toList());

                        userDTO.setMemberships(memberships);

                        // Calculate user status based on memberships
                        String calculatedStatus;
                        if (memberships.isEmpty()) {
                            calculatedStatus = "INACTIVE";
                        } else {
                            // Check if any membership is INACTIVE
                            boolean hasInactiveMembership = memberships.stream()
                                    .anyMatch(m -> "INACTIVE".equalsIgnoreCase((String) m.get("status")));

                            // Check if all memberships are ACTIVE
                            boolean allActive = memberships.stream()
                                    .allMatch(m -> "ACTIVE".equalsIgnoreCase((String) m.get("status")));

                            if (hasInactiveMembership || !allActive) {
                                calculatedStatus = "INACTIVE";
                            } else {
                                calculatedStatus = "ACTIVE";
                            }
                        }

                        member.put("user", userDTO);
                        member.put("userClubId", uc.getId());
                        member.put("status", calculatedStatus);
                        member.put("stripeId", uc.getStripeId() != null ? uc.getStripeId() : "");
                        member.put("createdAt", uc.getCreatedAt());
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
     * Update the status of a user-club relationship
     */
    @PutMapping("/{clubTag}/members/{userId}/status")
    public ResponseEntity<?> updateMemberStatus(
            @PathVariable String clubTag,
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

            UserClub updatedRelationship = userClubService.updateUserClubStatus(userId, clubTag, newStatus.toUpperCase());

            return ResponseEntity.ok(Map.of(
                    "message", "Member status updated successfully",
                    "userId", userId,
                    "clubTag", clubTag,
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
     * Remove a user from a club
     */
    @DeleteMapping("/{clubTag}/members/{userId}")
    public ResponseEntity<?> removeMemberFromClub(
            @PathVariable String clubTag,
            @PathVariable Long userId) {
        try {
            userClubService.removeUserFromClub(userId, clubTag);

            return ResponseEntity.ok(Map.of(
                    "message", "Member removed from club successfully",
                    "userId", userId,
                    "clubTag", clubTag
            ));
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to remove member from club: " + e.getMessage()));
        }
    }

    /**
     * Create Stripe onboarding link for a club
     * This initiates or continues the Stripe Express account setup process
     */
    @PostMapping("/{clubTag}/stripe-onboarding")
    public ResponseEntity<?> createStripeOnboardingLink(
            @PathVariable String clubTag,
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

            String onboardingUrl = clubService.createStripeOnboardingLink(clubTag, returnUrl, refreshUrl);

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
     * Get Stripe Express Dashboard link for a club
     * Allows club owners to access their Stripe dashboard to view payments, payouts, etc.
     */
    @GetMapping("/{clubTag}/stripe-dashboard-link")
    public ResponseEntity<?> getStripeDashboardLink(@PathVariable String clubTag) {
        try {
            String dashboardUrl = clubService.createStripeDashboardLink(clubTag);

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
}