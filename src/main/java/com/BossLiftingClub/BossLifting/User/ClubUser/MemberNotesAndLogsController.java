package com.BossLiftingClub.BossLifting.User.ClubUser;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Controller for managing member notes and logs
 */
@RestController
@RequestMapping("/api/user-clubs")
public class MemberNotesAndLogsController {

    @Autowired
    private UserClubService userClubService;

    @Autowired
    private UserClubRepository userClubRepository;

    // ===== Notes Endpoints =====

    /**
     * Update notes for a member
     * PUT /api/user-clubs/{userClubId}/notes
     *
     * Request body:
     * {
     *   "notes": "Text content of the notes"
     * }
     */
    @PutMapping("/{userClubId}/notes")
    public ResponseEntity<?> updateMemberNotes(
            @PathVariable Long userClubId,
            @RequestBody Map<String, String> request) {
        try {
            String notes = request.get("notes");
            UserClub updated = userClubService.updateMemberNotes(userClubId, notes);

            return ResponseEntity.ok(Map.of(
                    "message", "Notes updated successfully",
                    "userClubId", userClubId,
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
     * GET /api/user-clubs/{userClubId}/notes
     */
    @GetMapping("/{userClubId}/notes")
    public ResponseEntity<?> getMemberNotes(@PathVariable Long userClubId) {
        try {
            UserClub userClub = userClubRepository.findById(userClubId)
                    .orElseThrow(() -> new RuntimeException("UserClub not found with id: " + userClubId));

            return ResponseEntity.ok(Map.of(
                    "userClubId", userClubId,
                    "notes", userClub.getNotes() != null ? userClub.getNotes() : ""
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
     * Get all logs for a member
     * GET /api/user-clubs/{userClubId}/logs
     */
    @GetMapping("/{userClubId}/logs")
    public ResponseEntity<?> getMemberLogs(@PathVariable Long userClubId) {
        try {
            List<MemberLog> logs = userClubService.getMemberLogs(userClubId);
            return ResponseEntity.ok(logs);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to retrieve logs: " + e.getMessage()));
        }
    }

    /**
     * Add a new log for a member
     * POST /api/user-clubs/{userClubId}/logs
     *
     * Request body:
     * {
     *   "logText": "Text content of the log entry",
     *   "createdBy": "admin@example.com"
     * }
     */
    @PostMapping("/{userClubId}/logs")
    public ResponseEntity<?> addMemberLog(
            @PathVariable Long userClubId,
            @RequestBody LogRequest request) {
        try {
            if (request.getLogText() == null || request.getLogText().trim().isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "logText is required"));
            }

            MemberLog log = userClubService.addMemberLog(
                    userClubId,
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
     * PUT /api/user-clubs/logs/{logId}
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

            MemberLog log = userClubService.updateMemberLog(
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
     * DELETE /api/user-clubs/logs/{logId}
     */
    @DeleteMapping("/logs/{logId}")
    public ResponseEntity<?> deleteMemberLog(@PathVariable Long logId) {
        try {
            userClubService.deleteMemberLog(logId);

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
