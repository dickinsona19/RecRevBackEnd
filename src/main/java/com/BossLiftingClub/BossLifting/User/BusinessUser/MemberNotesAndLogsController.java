package com.BossLiftingClub.BossLifting.User.BusinessUser;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.BossLiftingClub.BossLifting.User.SignInLog.SignInLog;
import com.BossLiftingClub.BossLifting.User.SignInLog.SignInLogRepository;
import com.BossLiftingClub.BossLifting.User.User;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Controller for managing member notes and logs
 */
@RestController
@RequestMapping("/api/user-businesses")
public class MemberNotesAndLogsController {

    @Autowired
    private UserBusinessService userBusinessService;

    @Autowired
    private UserBusinessRepository userBusinessRepository;

    @Autowired
    private SignInLogRepository signInLogRepository;

    /**
     * Get business ID for a user
     * GET /api/user-businesses/user/{userId}
     */
    @GetMapping("/user/{userId}")
    public ResponseEntity<?> getUserBusinesses(@PathVariable Long userId) {
        try {
            List<UserBusiness> userBusinesses = userBusinessRepository.findAllByUserId(userId);
            if (userBusinesses.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("error", "User not found or not associated with any business"));
            }

            // Return the first business (or all if needed)
            List<Map<String, Object>> businesses = userBusinesses.stream()
                    .map(ub -> {
                        Map<String, Object> businessMap = new java.util.HashMap<>();
                        businessMap.put("id", ub.getId());
                        businessMap.put("businessId", ub.getBusiness().getId());
                        businessMap.put("businessTag", ub.getBusiness().getBusinessTag() != null ? ub.getBusiness().getBusinessTag() : "");
                        businessMap.put("businessTitle", ub.getBusiness().getTitle() != null ? ub.getBusiness().getTitle() : "");
                        return businessMap;
                    })
                    .collect(Collectors.toList());

            return ResponseEntity.ok(businesses);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to retrieve user businesses: " + e.getMessage()));
        }
    }

    // ===== Notes Endpoints =====

    /**
     * Update notes for a member
     * PUT /api/user-businesses/{userBusinessId}/notes
     *
     * Request body:
     * {
     *   "notes": "Text content of the notes"
     * }
     */
    @PutMapping("/{userBusinessId}/notes")
    public ResponseEntity<?> updateMemberNotes(
            @PathVariable Long userBusinessId,
            @RequestBody Map<String, String> request) {
        try {
            String notes = request.get("notes");
            UserBusiness updated = userBusinessService.updateMemberNotes(userBusinessId, notes);

            return ResponseEntity.ok(Map.of(
                    "message", "Notes updated successfully",
                    "userBusinessId", userBusinessId,
                    "notes", updated.getNotes() != null ? updated.getNotes() : ""
            ));
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to update notes: " + e.getMessage()));
        }
    }

    /**
     * Get notes for a member
     * GET /api/user-businesses/{userBusinessId}/notes
     */
    @GetMapping("/{userBusinessId}/notes")
    public ResponseEntity<?> getMemberNotes(@PathVariable Long userBusinessId) {
        try {
            UserBusiness userBusiness = userBusinessRepository.findById(userBusinessId)
                    .orElseThrow(() -> new RuntimeException("UserBusiness not found with id: " + userBusinessId));

            return ResponseEntity.ok(Map.of(
                    "userBusinessId", userBusinessId,
                    "notes", userBusiness.getNotes() != null ? userBusiness.getNotes() : ""
            ));
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to retrieve notes: " + e.getMessage()));
        }
    }

    // ===== Logs Endpoints =====

    /**
     * Get all logs for a member (including manual logs and sign-ins)
     * GET /api/user-businesses/{userBusinessId}/logs
     */
    @GetMapping("/{userBusinessId}/logs")
    public ResponseEntity<?> getMemberLogs(@PathVariable Long userBusinessId) {
        try {
            // 1. Get Manual Logs
            List<MemberLog> logs = userBusinessService.getMemberLogs(userBusinessId);

            // 2. Get Sign-In Logs
            // We need to get the user ID from the userBusinessId first
            UserBusiness userBusiness = userBusinessRepository.findById(userBusinessId)
                    .orElseThrow(() -> new RuntimeException("UserBusiness not found with id: " + userBusinessId));
            
            Long userId = userBusiness.getUser().getId();
            List<SignInLog> signInLogs = signInLogRepository.findByUserId(userId);

            // 3. Combine into Unified DTOs
            List<UnifiedLogDTO> unifiedLogs = new ArrayList<>();

            // Add manual logs
            for (MemberLog log : logs) {
                unifiedLogs.add(new UnifiedLogDTO(
                        log.getId(),
                        "LOG",
                        log.getLogText(),
                        log.getCreatedAt(),
                        log.getCreatedBy()
                ));
            }

            // Add sign-in logs
            for (SignInLog signIn : signInLogs) {
                unifiedLogs.add(new UnifiedLogDTO(
                        signIn.getId(),
                        "SIGN_IN",
                        "Member Checked In",
                        signIn.getSignInTime(),
                        "System"
                ));
            }

            // 4. Sort by date descending (newest first)
            unifiedLogs.sort(Comparator.comparing(UnifiedLogDTO::getCreatedAt).reversed());

            return ResponseEntity.ok(unifiedLogs);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to retrieve logs: " + e.getMessage()));
        }
    }

    /**
     * Add a new log for a member
     * POST /api/user-businesses/{userBusinessId}/logs
     *
     * Request body:
     * {
     *   "logText": "Text content of the log entry",
     *   "createdBy": "admin@example.com"
     * }
     */
    @PostMapping("/{userBusinessId}/logs")
    public ResponseEntity<?> addMemberLog(
            @PathVariable Long userBusinessId,
            @RequestBody LogRequest request) {
        try {
            if (request.getLogText() == null || request.getLogText().trim().isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "logText is required"));
            }

            MemberLog log = userBusinessService.addMemberLog(
                    userBusinessId,
                    request.getLogText(),
                    request.getCreatedBy()
            );

            return ResponseEntity.status(HttpStatus.CREATED).body(log);
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to add log: " + e.getMessage()));
        }
    }

    /**
     * Update an existing log
     * PUT /api/user-businesses/logs/{logId}
     *
     * Request body:
     * {
     *   "logText": "Updated text content",
     *   "updatedBy": "admin@example.com"
     * }
     */
    @PutMapping("/logs/{logId}")
    public ResponseEntity<?> updateMemberLog(
            @PathVariable Long logId,
            @RequestBody LogRequest request) {
        try {
            if (request.getLogText() == null || request.getLogText().trim().isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "logText is required"));
            }

            MemberLog log = userBusinessService.updateMemberLog(
                    logId,
                    request.getLogText(),
                    request.getUpdatedBy()
            );

            return ResponseEntity.ok(log);
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to update log: " + e.getMessage()));
        }
    }

    /**
     * Delete a log
     * DELETE /api/user-businesses/logs/{logId}
     */
    @DeleteMapping("/logs/{logId}")
    public ResponseEntity<?> deleteMemberLog(@PathVariable Long logId) {
        try {
            userBusinessService.deleteMemberLog(logId);

            return ResponseEntity.ok(Map.of(
                    "message", "Log deleted successfully",
                    "logId", logId
            ));
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to delete log: " + e.getMessage()));
        }
    }

    // ===== DTOs =====

    public static class UnifiedLogDTO {
        private Long id;
        private String type; // LOG, SIGN_IN
        private String text;
        private LocalDateTime createdAt;
        private String createdBy;

        public UnifiedLogDTO(Long id, String type, String text, LocalDateTime createdAt, String createdBy) {
            this.id = id;
            this.type = type;
            this.text = text;
            this.createdAt = createdAt;
            this.createdBy = createdBy;
        }

        public Long getId() { return id; }
        public String getType() { return type; }
        public String getText() { return text; }
        public LocalDateTime getCreatedAt() { return createdAt; }
        public String getCreatedBy() { return createdBy; }
    }

    public static class LogRequest {
        private String logText;
        private String createdBy;
        private String updatedBy;

        public String getLogText() {
            return logText;
        }

        public void setLogText(String logText) {
            this.logText = logText;
        }

        public String getCreatedBy() {
            return createdBy;
        }

        public void setCreatedBy(String createdBy) {
            this.createdBy = createdBy;
        }

        public String getUpdatedBy() {
            return updatedBy;
        }

        public void setUpdatedBy(String updatedBy) {
            this.updatedBy = updatedBy;
        }
    }
}
