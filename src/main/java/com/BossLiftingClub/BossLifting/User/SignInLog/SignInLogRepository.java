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

    @Query("SELECT s FROM SignInLog s JOIN FETCH s.user u WHERE (:startDate IS NULL OR s.signInTime >= :startDate) AND (:endDate IS NULL OR s.signInTime <= :endDate)")
    List<SignInLog> findAllWithFilters(@Param("startDate") LocalDateTime startDate, @Param("endDate") LocalDateTime endDate);
}