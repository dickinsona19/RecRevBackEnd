package com.BossLiftingClub.BossLifting.Analytics;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface RecentActivityRepository extends JpaRepository<RecentActivity, Long> {

    /**
     * Find recent activities for a specific business, ordered by most recent first
     */
    List<RecentActivity> findByBusinessIdOrderByCreatedAtDesc(Long businessId);

    /**
     * Find recent activities for a specific business within a time range
     */
    @Query("SELECT ra FROM RecentActivity ra WHERE ra.businessId = :businessId AND ra.createdAt >= :since ORDER BY ra.createdAt DESC")
    List<RecentActivity> findRecentByBusinessId(@Param("businessId") Long businessId, @Param("since") LocalDateTime since);

    /**
     * Check if an event has already been processed (to prevent duplicates)
     */
    Optional<RecentActivity> findByStripeEventId(String stripeEventId);

    /**
     * Delete activities older than a specific date
     */
    @Modifying
    @Query("DELETE FROM RecentActivity ra WHERE ra.createdAt < :cutoffDate")
    int deleteOlderThan(@Param("cutoffDate") LocalDateTime cutoffDate);

    /**
     * Count activities for a business
     */
    long countByBusinessId(Long businessId);
}
