package com.BossLiftingClub.BossLifting.User.SignInLog;

import com.BossLiftingClub.BossLifting.User.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface SignInLogRepository extends JpaRepository<SignInLog, Long>, JpaSpecificationExecutor<SignInLog> {
    List<SignInLog> findByUserId(Long userId);

    @Query("SELECT u FROM User u LEFT JOIN FETCH u.signInLogs WHERE u.id = :id")
    Optional<User> findByIdWithSignInLogs(@Param("id") Long id);

    @Query(value = "SELECT sil.id, sil.sign_in_time AS scan_time, sil.user_id, " +
            "(u.first_name || ' ' || u.last_name) AS username " +
            "FROM sign_in_logs sil " +
            "JOIN users u ON u.id = sil.user_id " +
            "WHERE sil.sign_in_time >= COALESCE(:start, '1970-01-01 00:00:00'::timestamp) " +
            "AND sil.sign_in_time <= COALESCE(:end, '9999-12-31 23:59:59'::timestamp)",
            nativeQuery = true)
    List<SignInLogProjection> findAllWithFilters(@Param("start") LocalDateTime start,
                                                 @Param("end") LocalDateTime end);
}