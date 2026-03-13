package com.BossLiftingClub.BossLifting.Sync;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Endpoint for Boss-Lifting-Club-API to sync new sign-ups into RecRev.
 * Creates a member with the same entryQrcodeToken.
 */
@RestController
@RequestMapping("/api/sync/boss-signup")
public class BossSyncController {

    private static final Logger logger = LoggerFactory.getLogger(BossSyncController.class);

    private final BossSyncService bossSyncService;

    public BossSyncController(BossSyncService bossSyncService) {
        this.bossSyncService = bossSyncService;
    }

    @PostMapping
    public ResponseEntity<?> syncMemberFromBoss(@RequestBody BossSignupSyncRequest request) {
        try {
            bossSyncService.syncMemberFromBoss(request);
            return ResponseEntity.ok(Map.of("success", true));
        } catch (IllegalArgumentException e) {
            logger.warn("Boss sync validation error: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            logger.error("Boss sync failed", e);
            return ResponseEntity.status(500).body(Map.of("error", "Sync failed: " + e.getMessage()));
        }
    }
}
