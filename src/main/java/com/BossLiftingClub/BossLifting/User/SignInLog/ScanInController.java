package com.BossLiftingClub.BossLifting.User.SignInLog;

import com.BossLiftingClub.BossLifting.User.SignInLog.SignInLogRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/scan-ins")
public class ScanInController {
    private final SignInLogRepository signInLogRepository;

    public ScanInController(SignInLogRepository signInLogRepository) {
        this.signInLogRepository = signInLogRepository;
    }

    @GetMapping
    public List<SignInLogProjection> getScanIns(
            @RequestParam(required = false) LocalDateTime start,
            @RequestParam(required = false) LocalDateTime end) {
        return signInLogRepository.findAllWithFilters(start, end);
    }
}