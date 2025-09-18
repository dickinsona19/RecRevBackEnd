package com.BossLiftingClub.BossLifting.User.SignInLog;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/scan-ins")
public class ScanInController {
    private final SignInLogRepository signInLogRepository;

    public ScanInController(SignInLogRepository signInLogRepository) {
        this.signInLogRepository = signInLogRepository;
    }

    @GetMapping
    public List<SignInLogProjection> getScanIns(
            @RequestParam(name = "startDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime start,
            @RequestParam(name = "endDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime end) {
        return signInLogRepository.findAllWithFilters(start, end);
    }
}