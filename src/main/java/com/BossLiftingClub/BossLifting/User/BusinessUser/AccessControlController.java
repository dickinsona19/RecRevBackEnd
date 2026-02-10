package com.BossLiftingClub.BossLifting.User.BusinessUser;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Controller for access control endpoints.
 * Implements the "Dictatorship Model" where Dependents inherit the Primary Owner's subscription status.
 */
@RestController
@RequestMapping("/api/access")
public class AccessControlController {

    @Autowired
    private AccessControlService accessControlService;

    /**
     * Check if a user can enter the gym.
     * 
     * GET /api/access/check?userId={id}&businessTag={tag}
     * 
     * @param userId The user ID to check access for
     * @param businessTag Optional: specific business to check. If null, checks all businesses.
     * @return Map with "access" (boolean) and "reason" (string) if denied
     */
    @GetMapping("/check")
    public ResponseEntity<Map<String, Object>> checkAccess(
            @RequestParam Long userId,
            @RequestParam(required = false) String businessTag) {
        try {
            Map<String, Object> result = accessControlService.checkAccess(userId, businessTag);
            boolean hasAccess = (Boolean) result.get("access");
            
            if (hasAccess) {
                return ResponseEntity.ok(result);
            } else {
                return ResponseEntity.status(403).body(result);
            }
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of(
                "access", false,
                "reason", "Error checking access: " + e.getMessage()
            ));
        }
    }

}
