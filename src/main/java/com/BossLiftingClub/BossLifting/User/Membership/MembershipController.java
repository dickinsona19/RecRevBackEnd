package com.BossLiftingClub.BossLifting.User.Membership;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/memberships")
public class MembershipController {
    @Autowired
    private MembershipService membershipService;

    @PostMapping
    public ResponseEntity<MembershipDTO> createMembership(@RequestBody MembershipDTO membershipDTO) {
        return ResponseEntity.ok(membershipService.createMembership(membershipDTO));
    }

    @GetMapping("/{id}")
    public ResponseEntity<MembershipDTO> getMembershipById(@PathVariable Long id) {
        return ResponseEntity.ok(membershipService.getMembershipById(id));
    }

    @GetMapping
    public ResponseEntity<List<MembershipDTO>> getAllMemberships() {
        return ResponseEntity.ok(membershipService.getAllMemberships());
    }

    @PutMapping("/{id}")
    public ResponseEntity<MembershipDTO> updateMembership(@PathVariable Long id, @RequestBody MembershipDTO membershipDTO) {
        return ResponseEntity.ok(membershipService.updateMembership(id, membershipDTO));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteMembership(@PathVariable Long id) {
        membershipService.deleteMembership(id);
        return ResponseEntity.ok().build();
    }
}