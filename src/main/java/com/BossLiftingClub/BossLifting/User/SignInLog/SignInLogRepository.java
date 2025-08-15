package com.BossLiftingClub.BossLifting.User.SignInLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SignInLogRepository extends JpaRepository<SignInLog, Long> {
    List<SignInLog> findByUserId(Long userId);
}