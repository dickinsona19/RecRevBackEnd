package com.BossLiftingClub.BossLifting.Business;

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
@RequestMapping("/api/businesses")
@Validated
public class BusinessController {
    @Autowired
    private BusinessService businessService;

    @Autowired
    private UserClubService userClubService;

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
     * Get all members (users) for a specific business by businessTag
     */
    @GetMapping("/{businessTag}/members")
    public ResponseEntity<?> getMembersByBusinessTag(@PathVariable String businessTag) {
        try {
            List<UserClub> userClubs = userClubService.getUsersByBusinessTag(businessTag);

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

            UserClub updatedRelationship = userClubService.updateUserClubStatus(userId, businessTag, newStatus.toUpperCase());

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
            userClubService.removeUserFromBusiness(userId, businessTag);

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
    
    // Backward compatibility endpoints - map clubTag to businessTag
    @GetMapping("/club/{clubTag}/members")
    public ResponseEntity<?> getMembersByClubTagLegacy(@PathVariable String clubTag) {
        return getMembersByBusinessTag(clubTag);
    }
    
    @PutMapping("/club/{clubTag}/members/{userId}/status")
    public ResponseEntity<?> updateMemberStatusLegacy(
            @PathVariable String clubTag,
            @PathVariable Long userId,
            @RequestBody Map<String, String> request) {
        return updateMemberStatus(clubTag, userId, request);
    }
    
    @DeleteMapping("/club/{clubTag}/members/{userId}")
    public ResponseEntity<?> removeMemberFromClubLegacy(
            @PathVariable String clubTag,
            @PathVariable Long userId) {
        return removeMemberFromBusiness(clubTag, userId);
    }
    
    @PostMapping("/club/{clubTag}/stripe-onboarding")
    public ResponseEntity<?> createStripeOnboardingLinkLegacy(
            @PathVariable String clubTag,
            @RequestBody Map<String, String> request) {
        return createStripeOnboardingLink(clubTag, request);
    }
    
    @GetMapping("/club/{clubTag}/stripe-dashboard-link")
    public ResponseEntity<?> getStripeDashboardLinkLegacy(@PathVariable String clubTag) {
        return getStripeDashboardLink(clubTag);
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
}





