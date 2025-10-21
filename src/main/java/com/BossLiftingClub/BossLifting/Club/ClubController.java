package com.BossLiftingClub.BossLifting.Club;

import com.BossLiftingClub.BossLifting.User.ClubUser.UserClub;
import com.BossLiftingClub.BossLifting.User.ClubUser.UserClubService;
import com.BossLiftingClub.BossLifting.User.UserDTO;
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

            // Map to DTOs with user information
            List<Map<String, Object>> members = userClubs.stream()
                    .map(uc -> {
                        Map<String, Object> member = new java.util.HashMap<>();
                        member.put("user", new UserDTO(uc.getUser()));
                        member.put("status", uc.getStatus());
                        member.put("stripeId", uc.getStripeId() != null ? uc.getStripeId() : "");
                        member.put("membershipId", uc.getMembership() != null ? uc.getMembership().getId() : null);
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
}