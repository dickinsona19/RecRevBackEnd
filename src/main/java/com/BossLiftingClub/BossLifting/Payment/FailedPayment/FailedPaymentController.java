package com.BossLiftingClub.BossLifting.Payment.FailedPayment;

import com.BossLiftingClub.BossLifting.Security.SecurityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/payment/failed")
public class FailedPaymentController {
    
    private static final Logger logger = LoggerFactory.getLogger(FailedPaymentController.class);
    
    @Autowired
    private FailedPaymentAttemptRepository attemptRepository;
    
    @Autowired
    private FailedPaymentRetryService retryService;
    
    /**
     * Get all failed payment attempts for a business
     * GET /api/payment/failed?businessId={id}&status={status}
     */
    @GetMapping
    public ResponseEntity<?> getFailedPayments(
            @RequestParam Long businessId,
            @RequestParam(required = false) String status) {
        
        // Check permission
        if (!SecurityUtils.hasPermission("payment:view_history")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "You don't have permission to view failed payments"));
        }
        
        try {
            List<FailedPaymentAttempt> attempts;
            if (status != null && !status.isEmpty()) {
                attempts = attemptRepository.findByBusinessIdAndStatus(businessId, status);
            } else {
                attempts = attemptRepository.findByBusinessId(businessId);
            }
            return ResponseEntity.ok(attempts);
        } catch (Exception e) {
            logger.error("Error fetching failed payments: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to fetch failed payments: " + e.getMessage()));
        }
    }
    
    /**
     * Get a specific failed payment attempt
     * GET /api/payment/failed/{id}
     */
    @GetMapping("/{id}")
    public ResponseEntity<?> getFailedPayment(@PathVariable Long id) {
        // Check permission
        if (!SecurityUtils.hasPermission("payment:view_history")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "You don't have permission to view failed payments"));
        }
        
        try {
            FailedPaymentAttempt attempt = attemptRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("Failed payment attempt not found: " + id));
            return ResponseEntity.ok(attempt);
        } catch (Exception e) {
            logger.error("Error fetching failed payment: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to fetch failed payment: " + e.getMessage()));
        }
    }
    
    /**
     * Manually retry a failed payment
     * POST /api/payment/failed/{id}/retry
     */
    @PostMapping("/{id}/retry")
    public ResponseEntity<?> retryPayment(@PathVariable Long id) {
        // Check permission
        if (!SecurityUtils.hasPermission("payment:process")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "You don't have permission to retry payments"));
        }
        
        try {
            FailedPaymentAttempt attempt = attemptRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("Failed payment attempt not found: " + id));
            
            // Check if already succeeded or exhausted
            if ("SUCCEEDED".equals(attempt.getStatus())) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(Map.of("error", "Payment has already been successfully processed"));
            }
            
            if ("EXHAUSTED".equals(attempt.getStatus())) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(Map.of("error", "All retry attempts have been exhausted. Please contact the member directly."));
            }
            
            // Mark as manual retry
            attempt.setStatus("MANUAL_RETRY");
            attemptRepository.save(attempt);
            
            // Attempt manual retry (staff-initiated, bypasses Stripe's automatic schedule)
            FailedPaymentRetryService.RetryResult result = retryService.retryPayment(attempt);
            
            if (result.isSuccess()) {
                return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", result.getMessage(),
                    "attempt", attemptRepository.findById(id).orElse(attempt)
                ));
            } else {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(Map.of(
                            "success", false,
                            "message", result.getMessage(),
                            "error", result.getError(),
                            "attempt", attemptRepository.findById(id).orElse(attempt)
                        ));
            }
        } catch (Exception e) {
            logger.error("Error retrying payment: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to retry payment: " + e.getMessage()));
        }
    }
    
    /**
     * Get failed payment statistics for a business
     * GET /api/payment/failed/stats?businessId={id}
     */
    @GetMapping("/stats")
    public ResponseEntity<?> getFailedPaymentStats(@RequestParam Long businessId) {
        // Check permission
        if (!SecurityUtils.hasPermission("analytics:view_simple")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "You don't have permission to view analytics"));
        }
        
        try {
            long totalFailed = attemptRepository.findByBusinessId(businessId).size();
            long pendingRetry = attemptRepository.countByBusinessIdAndStatus(businessId, "PENDING_RETRY");
            long succeeded = attemptRepository.countByBusinessIdAndStatus(businessId, "SUCCEEDED");
            long exhausted = attemptRepository.countByBusinessIdAndStatus(businessId, "EXHAUSTED");
            
            // Get breakdown by reason
            List<Object[]> reasons = attemptRepository.countByFailureReason(businessId);
            Map<String, Long> reasonsMap = new java.util.HashMap<>();
            for (Object[] row : reasons) {
                if (row[0] != null) {
                    reasonsMap.put((String) row[0], (Long) row[1]);
                } else {
                    reasonsMap.put("Unknown", (Long) row[1]);
                }
            }
            
            return ResponseEntity.ok(Map.of(
                "totalFailed", totalFailed,
                "pendingRetry", pendingRetry,
                "succeeded", succeeded,
                "exhausted", exhausted,
                "successRate", totalFailed > 0 ? (double) succeeded / totalFailed * 100 : 0.0,
                "reasons", reasonsMap
            ));
        } catch (Exception e) {
            logger.error("Error fetching failed payment stats: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to fetch stats: " + e.getMessage()));
        }
    }
}

