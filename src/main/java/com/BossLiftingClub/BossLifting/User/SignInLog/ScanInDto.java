package com.BossLiftingClub.BossLifting.User.SignInLog;

import com.BossLiftingClub.BossLifting.User.SignInLog.SignInLog;
import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.LocalDateTime;

public class ScanInDto {

    private String id;
    private String userId;
    private String userName;
    private LocalDateTime scanTime;

    public ScanInDto() {}

    public ScanInDto(SignInLog log) {
        this.id = log.getId().toString();
        this.userId = log.getUser().getId().toString();
        this.userName = log.getUser().getFirstName(); // Assumes User has getName()
        this.scanTime = log.getSignInTime();
    }

    // Getters and Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getUserName() { return userName; }
    public void setUserName(String userName) { this.userName = userName; }

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'")
    public LocalDateTime getScanTime() { return scanTime; }
    public void setScanTime(LocalDateTime scanTime) { this.scanTime = scanTime; }
}