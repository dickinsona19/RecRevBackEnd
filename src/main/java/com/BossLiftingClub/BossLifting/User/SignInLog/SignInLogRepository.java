package com.BossLiftingClub.BossLifting.User.SignInLog;
import com.BossLiftingClub.BossLifting.User.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface SignInLogRepository extends JpaRepository<SignInLog, Long> {
    List<SignInLog> findByUserId(Long userId);

    @Query("SELECT u FROM User u LEFT JOIN FETCH u.signInLogs WHERE u.id = :id")
    Optional<User> findByIdWithSignInLogs(@Param("id") Long id);

    @Query(value = "SELECT sil.* FROM sign_in_logs sil JOIN users u ON u.id = sil.user_id " +
            "WHERE (:start IS NULL OR sil.sign_in_time >= CAST(:start AS timestamp)) " +
            "AND (:end IS NULL OR sil.sign_in_time <= CAST(:end AS timestamp))",
            nativeQuery = true)
    List<SignInLog> findAllWithFilters(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);
}