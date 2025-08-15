package com.BossLiftingClub.BossLifting.User.SignInLog;

import java.time.LocalDateTime;

public class SignInLogDto {

    private LocalDateTime signInTime;

    public SignInLogDto() {}

    public SignInLogDto(SignInLog log) {

        this.signInTime = log.getSignInTime();
    }


    public LocalDateTime getSignInTime() {
        return signInTime;
    }
    public void setSignInTime(LocalDateTime signInTime) {
        this.signInTime = signInTime;
    }
}
