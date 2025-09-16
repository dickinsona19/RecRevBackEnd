package com.BossLiftingClub.BossLifting.User.SignInLog;

import java.time.LocalDateTime;

public interface SignInLogProjection {
    Long getId();
    LocalDateTime getSignInTime();
    Long getUserId();
    String getUsername();
}