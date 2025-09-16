package com.BossLiftingClub.BossLifting.User.SignInLog;

import java.time.LocalDateTime;

public interface SignInLogProjection {
    Long getId();
    LocalDateTime getScanTime(); // Maps to scan_time
    Long getUserId(); // Maps to user_id
    String getUsername(); // Maps to first_name || ' ' || last_name
}