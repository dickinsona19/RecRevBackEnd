package com.BossLiftingClub.BossLifting.Payment.FailedPayment;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface FailedPaymentAttemptRepository extends JpaRepository<FailedPaymentAttempt, Long> {
    
    Optional<FailedPaymentAttempt> findByInvoiceId(String invoiceId);
    
    List<FailedPaymentAttempt> findByUserId(Long userId);
    
    List<FailedPaymentAttempt> findByBusinessId(Long businessId);
    
    List<FailedPaymentAttempt> findByStatus(String status);
 
    // Note: Removed findPendingRetries query since Stripe handles automatic retries
    // We only track retry attempts, not schedule them
    
    @Query("SELECT f FROM FailedPaymentAttempt f WHERE f.businessId = :businessId AND f.status = :status")
    List<FailedPaymentAttempt> findByBusinessIdAndStatus(@Param("businessId") Long businessId, @Param("status") String status);
    
    @Query("SELECT COUNT(f) FROM FailedPaymentAttempt f WHERE f.businessId = :businessId AND f.status = :status")
    Long countByBusinessIdAndStatus(@Param("businessId") Long businessId, @Param("status") String status);

    @Query("SELECT f.failureReason, COUNT(f) FROM FailedPaymentAttempt f WHERE f.businessId = :businessId GROUP BY f.failureReason")
    List<Object[]> countByFailureReason(@Param("businessId") Long businessId);
}
