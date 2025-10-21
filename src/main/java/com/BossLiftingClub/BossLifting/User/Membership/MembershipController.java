package com.BossLiftingClub.BossLifting.User.Membership;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/memberships")
public class MembershipController {
    @Autowired
    private MembershipService membershipService;

    @GetMapping("/club/{clubTag}")
    @Transactional(readOnly = true)
    public ResponseEntity<?> getMembershipsByClubTag(@PathVariable String clubTag) {
        try {
            List<MembershipDTO> memberships = membershipService.getMembershipsByClubTag(clubTag);
            return ResponseEntity.ok(memberships);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to retrieve memberships: " + e.getMessage()));
        }
    }

    // ✅ Add a new membership
    @PostMapping
    public ResponseEntity<?> addMembership(@RequestBody Membership membership) {
        try {
            Membership saved = membershipService.createMembershipWithStripe(membership);
            return ResponseEntity.status(HttpStatus.CREATED).body(saved);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to create membership: " + e.getMessage()));
        }
    }

    // ✅ Archive (soft delete) a membership
    @PutMapping("/{id}/archive")
    public ResponseEntity<?> archiveMembership(@PathVariable Long id) {
        try {
            Membership archived = membershipService.archiveMembership(id);
            return ResponseEntity.ok(archived);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "Failed to archive membership: " + e.getMessage()));
        }
    }
}