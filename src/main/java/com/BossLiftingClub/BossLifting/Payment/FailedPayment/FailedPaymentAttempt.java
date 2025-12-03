package com.BossLiftingClub.BossLifting.Payment.FailedPayment;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "failed_payment_attempts")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class FailedPaymentAttempt {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "invoice_id", nullable = false)
    private String invoiceId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "user_business_id")
    private Long userBusinessId;

    @Column(name = "business_id", nullable = false)
    private Long businessId;

    @Column(name = "amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal amount;

    @Column(name = "failure_reason", columnDefinition = "TEXT")
    private String failureReason;

    @Column(name = "status", nullable = false, length = 50)
    private String status; // PENDING_RETRY, RETRYING, SUCCEEDED, EXHAUSTED, MANUAL_RETRY

    @Column(name = "retry_attempt_count", nullable = false)
    private Integer retryAttemptCount = 0;

    @Column(name = "max_retry_attempts", nullable = false)
    private Integer maxRetryAttempts = 4;

    @Column(name = "next_retry_date")
    private LocalDateTime nextRetryDate;

    @Column(name = "last_retry_date")
    private LocalDateTime lastRetryDate;

    @Column(name = "succeeded_at")
    private LocalDateTime succeededAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "stripe_customer_id")
    private String stripeCustomerId;

    @Column(name = "stripe_subscription_id")
    private String stripeSubscriptionId;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}


