package com.BossLiftingClub.BossLifting.User.Membership;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/punch-card")
public class PunchCardScanController {
    @Autowired
    private PunchCardScanService punchCardScanService;

    @PostMapping("/scan/{membershipId}")
    public ResponseEntity<?> processScan(@PathVariable Long membershipId) {
        try {
            boolean success = punchCardScanService.processPunchCardScan(membershipId);
            if (success) {
                return ResponseEntity.ok(Map.of("success", true, "message", "Punch card scanned successfully"));
            } else {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(Map.of("success", false, "message", "Duplicate scan detected within 1 hour"));
            }
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("success", false, "error", e.getMessage()));
        }
    }
}
