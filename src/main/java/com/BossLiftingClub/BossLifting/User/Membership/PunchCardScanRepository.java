package com.BossLiftingClub.BossLifting.User.Membership;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.LocalDateTime;
import java.util.List;

public interface PunchCardScanRepository extends JpaRepository<PunchCardScan, Long> {
    @Query("SELECT p FROM PunchCardScan p WHERE p.userBusinessMembership.id = :membershipId " +
           "AND p.scannedAt >= :since")
    List<PunchCardScan> findRecentScans(@Param("membershipId") Long membershipId, 
                                        @Param("since") LocalDateTime since);
}
