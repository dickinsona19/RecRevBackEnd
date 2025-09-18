package com.BossLiftingClub.BossLifting.User.SignInLog;

import com.BossLiftingClub.BossLifting.User.SignInLog.SignInLog;
import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.LocalDateTime;

public class ScanInDto {

    private String id;
    private LocalDateTime scanTime;

    public ScanInDto() {}

    public ScanInDto(SignInLog log) {
        this.id = log.getId().toString();
        this.scanTime = log.getSignInTime();
    }

    // Getters and Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'")
    public LocalDateTime getScanTime() { return scanTime; }
    public void setScanTime(LocalDateTime scanTime) { this.scanTime = scanTime; }
}