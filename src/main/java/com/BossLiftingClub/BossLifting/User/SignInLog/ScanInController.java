package com.BossLiftingClub.BossLifting.User.SignInLog;

import com.BossLiftingClub.BossLifting.User.User;
import com.BossLiftingClub.BossLifting.User.UserRepository;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/scan-ins")
public class ScanInController {
    private final SignInLogRepository signInLogRepository;
    private final UserRepository userRepository;

    public ScanInController(SignInLogRepository signInLogRepository, UserRepository userRepository) {
        this.signInLogRepository = signInLogRepository;
        this.userRepository = userRepository;
    }

    @GetMapping
    public List<SignInLogProjection> getScanIns(
            @RequestParam(name = "startDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime start,
            @RequestParam(name = "endDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime end) {
        return signInLogRepository.findAllWithFilters(start, end);
    }

    /**
     * Simulate a scan-in for a user
     */
    @PostMapping("/{userId}")
    public ResponseEntity<?> createScanIn(@PathVariable Long userId) {
        try {
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new RuntimeException("User not found"));

            SignInLog log = new SignInLog();
            log.setUser(user);
            log.setSignInTime(LocalDateTime.now());
            signInLogRepository.save(log);

            return ResponseEntity.ok(Map.of(
                    "message", "Scan-in recorded successfully",
                    "userId", userId,
                    "time", log.getSignInTime()
            ));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }
}