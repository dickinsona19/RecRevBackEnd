package com.BossLiftingClub.BossLifting.User.SignInLog;

import com.BossLiftingClub.BossLifting.User.SignInLog.SignInLogRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/scan-ins")
@CrossOrigin(origins = "*") // Adjust for production
public class ScanInController {

    @Autowired
    private SignInLogRepository signInLogRepository;

    @GetMapping
    public List<ScanInDto> getScanIns(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate) {
        List<SignInLog> logs = signInLogRepository.findAllWithFilters(startDate, endDate);
        return logs.stream()
                .map(ScanInDto::new)
                .collect(Collectors.toList());
    }
}