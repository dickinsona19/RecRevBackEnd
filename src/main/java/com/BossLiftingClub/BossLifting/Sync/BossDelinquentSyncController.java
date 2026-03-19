package com.BossLiftingClub.BossLifting.Sync;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Endpoint for Boss-Lifting-Club-API to signal when a member becomes delinquent
 * (e.g. invoice.payment_failed or subscription status past_due).
 */
@RestController
@RequestMapping("/api/sync/boss-delinquent")
public class BossDelinquentSyncController {

    private static final Logger logger = LoggerFactory.getLogger(BossDelinquentSyncController.class);

    private final BossDelinquentSyncService bossDelinquentSyncService;

    public BossDelinquentSyncController(BossDelinquentSyncService bossDelinquentSyncService) {
        this.bossDelinquentSyncService = bossDelinquentSyncService;
    }

    @PostMapping
    public ResponseEntity<?> markMemberDelinquent(@RequestBody Map<String, Object> request) {
        try {
            Object stripeIdObj = request.get("userStripeMemberId");
            if (stripeIdObj == null) {
                stripeIdObj = request.get("stripeCustomerId");
            }
            String userStripeMemberId = stripeIdObj != null ? stripeIdObj.toString().trim() : null;

            if (userStripeMemberId == null || userStripeMemberId.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "userStripeMemberId is required"));
            }

            bossDelinquentSyncService.markDelinquentByStripeId(userStripeMemberId);
            return ResponseEntity.ok(Map.of("success", true));
        } catch (IllegalArgumentException e) {
            logger.warn("Boss delinquent sync validation: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            logger.error("Boss delinquent sync failed", e);
            return ResponseEntity.status(500).body(Map.of("error", "Sync failed: " + e.getMessage()));
        }
    }
}
