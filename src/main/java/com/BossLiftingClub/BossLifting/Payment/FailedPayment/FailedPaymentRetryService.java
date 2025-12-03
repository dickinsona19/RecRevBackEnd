package com.BossLiftingClub.BossLifting.Payment.FailedPayment;

import com.BossLiftingClub.BossLifting.Business.Business;
import com.BossLiftingClub.BossLifting.Business.BusinessRepository;
import com.BossLiftingClub.BossLifting.Stripe.StripeService;
import com.stripe.exception.StripeException;
import com.stripe.model.Invoice;
import com.stripe.param.InvoicePayParams;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Service for tracking and manually retrying failed payments.
 * Note: Stripe handles automatic payment retries - this service is for:
 * 1. Database tracking of failed payment attempts
 * 2. Manual retry functionality (staff-initiated)
 * 3. Integration with our membership system
 */
@Service
public class FailedPaymentRetryService {
    
    private static final Logger logger = LoggerFactory.getLogger(FailedPaymentRetryService.class);
    
    @Autowired
    private FailedPaymentAttemptRepository attemptRepository;
    
    @Autowired
    private FailedPaymentNotificationService notificationService;
    
    @Autowired
    private BusinessRepository businessRepository;
    
    @Autowired
    private StripeService stripeService;
    
    /**
     * Create or update a failed payment attempt record when Stripe reports a failure.
     * This is called from the webhook handler when Stripe sends invoice.payment_failed.
     * 
     * Note: Stripe will automatically retry the payment. We just track the attempt here.
     */
    @Transactional
    public FailedPaymentAttempt createOrUpdateFailedPaymentAttempt(
            String invoiceId,
            Long userId,
            Long userBusinessId,
            Long businessId,
            BigDecimal amount,
            String failureReason,
            String stripeCustomerId,
            String stripeSubscriptionId) {
        
        // Check if attempt already exists (for retries from Stripe)
        Optional<FailedPaymentAttempt> existingOpt = attemptRepository.findByInvoiceId(invoiceId);
        
        if (existingOpt.isPresent()) {
            // Update existing record (Stripe is retrying)
            FailedPaymentAttempt attempt = existingOpt.get();
            attempt.setRetryAttemptCount(attempt.getRetryAttemptCount() + 1);
            attempt.setLastRetryDate(LocalDateTime.now());
            attempt.setFailureReason(failureReason);
            attempt.setStatus("RETRYING");
            attempt = attemptRepository.save(attempt);
            
            logger.info("Updated failed payment attempt {} for invoice: {}, retry count: {}", 
                    attempt.getId(), invoiceId, attempt.getRetryAttemptCount());
            
            // Send pre-retry notification for manual retries (Stripe handles automatic retries)
            // We only notify on the first attempt
            if (attempt.getRetryAttemptCount() == 1) {
                notificationService.sendInitialFailureNotification(attempt);
            }
            
            return attempt;
        } else {
            // Create new record (first failure)
            FailedPaymentAttempt attempt = new FailedPaymentAttempt();
            attempt.setInvoiceId(invoiceId);
            attempt.setUserId(userId);
            attempt.setUserBusinessId(userBusinessId);
            attempt.setBusinessId(businessId);
            attempt.setAmount(amount);
            attempt.setFailureReason(failureReason);
            attempt.setStatus("PENDING_RETRY"); // Stripe will retry automatically
            attempt.setRetryAttemptCount(0);
            attempt.setMaxRetryAttempts(4); // Stripe's default retry schedule
            attempt.setStripeCustomerId(stripeCustomerId);
            attempt.setStripeSubscriptionId(stripeSubscriptionId);
            
            attempt = attemptRepository.save(attempt);
            
            // Send initial failure notification
            notificationService.sendInitialFailureNotification(attempt);
            
            logger.info("Created failed payment attempt for invoice: {}, user: {}, business: {}", 
                    invoiceId, userId, businessId);
            
            return attempt;
        }
    }
    
    /**
     * Mark a failed payment attempt as succeeded (called from webhook when Stripe retry succeeds).
     */
    @Transactional
    public void markPaymentSucceeded(String invoiceId) {
        Optional<FailedPaymentAttempt> attemptOpt = attemptRepository.findByInvoiceId(invoiceId);
        
        if (attemptOpt.isPresent()) {
            FailedPaymentAttempt attempt = attemptOpt.get();
            if (!"SUCCEEDED".equals(attempt.getStatus())) {
                attempt.setStatus("SUCCEEDED");
                attempt.setSucceededAt(LocalDateTime.now());
                attempt.setNextRetryDate(null);
                attemptRepository.save(attempt);
                
                // Send success notification
                notificationService.sendRetrySuccessNotification(attempt);
                
                logger.info("Marked failed payment attempt {} as succeeded for invoice: {}", 
                        attempt.getId(), invoiceId);
            }
        }
    }
    
    /**
     * Mark a failed payment attempt as exhausted (all Stripe retries failed).
     */
    @Transactional
    public void markPaymentExhausted(String invoiceId) {
        Optional<FailedPaymentAttempt> attemptOpt = attemptRepository.findByInvoiceId(invoiceId);
        
        if (attemptOpt.isPresent()) {
            FailedPaymentAttempt attempt = attemptOpt.get();
            if (!"EXHAUSTED".equals(attempt.getStatus())) {
                attempt.setStatus("EXHAUSTED");
                attempt.setNextRetryDate(null);
                attemptRepository.save(attempt);
                
                // Send exhausted notification
                notificationService.sendRetryExhaustedNotification(attempt);
                
                logger.info("Marked failed payment attempt {} as exhausted for invoice: {}", 
                        attempt.getId(), invoiceId);
            }
        }
    }
    
    /**
     * Manually retry a failed payment (staff-initiated, bypasses Stripe's schedule).
     * This allows staff to immediately attempt a payment retry without waiting for Stripe's automatic retry.
     * 
     * @param attempt The failed payment attempt
     * @return Result of the retry attempt
     */
    @Transactional
    public RetryResult retryPayment(FailedPaymentAttempt attempt) {
        try {
            Business business = businessRepository.findById(attempt.getBusinessId())
                    .orElseThrow(() -> new RuntimeException("Business not found: " + attempt.getBusinessId()));
            
            String stripeAccountId = business.getStripeAccountId();
            if (stripeAccountId == null || stripeAccountId.isEmpty()) {
                throw new RuntimeException("Business does not have Stripe account configured");
            }
            
            // Update status to RETRYING
            attempt.setStatus("RETRYING");
            attempt.setLastRetryDate(LocalDateTime.now());
            attempt.setRetryAttemptCount(attempt.getRetryAttemptCount() + 1);
            attemptRepository.save(attempt);
            
            // Attempt to pay the invoice via Stripe API
            com.stripe.net.RequestOptions requestOptions = com.stripe.net.RequestOptions.builder()
                    .setStripeAccount(stripeAccountId)
                    .build();
            
            Invoice invoice = Invoice.retrieve(attempt.getInvoiceId(), requestOptions);
            
            // If invoice is draft, finalize it first
            if ("draft".equals(invoice.getStatus())) {
                invoice = invoice.finalizeInvoice(requestOptions);
            }
            
            // Pay the invoice (only if it's open)
            Invoice paidInvoice = invoice;
            if ("open".equals(invoice.getStatus())) {
                InvoicePayParams params = InvoicePayParams.builder().build();
                paidInvoice = invoice.pay(params, requestOptions);
            }
            
            // Refresh invoice to get latest status
            paidInvoice = Invoice.retrieve(attempt.getInvoiceId(), requestOptions);
            
            // Check if payment succeeded
            if ("paid".equals(paidInvoice.getStatus())) {
                // Success!
                attempt.setStatus("SUCCEEDED");
                attempt.setSucceededAt(LocalDateTime.now());
                attempt.setNextRetryDate(null);
                attemptRepository.save(attempt);
                
                // Send success notification
                notificationService.sendRetrySuccessNotification(attempt);
                
                logger.info("Manual payment retry succeeded for attempt: {}, invoice: {}", 
                        attempt.getId(), attempt.getInvoiceId());
                return new RetryResult(true, "Payment processed successfully", null);
            } else {
                // Still failed
                attempt.setStatus("PENDING_RETRY"); // Stripe will continue automatic retries
                attempt.setFailureReason("Manual retry failed: Invoice status is " + paidInvoice.getStatus());
                attemptRepository.save(attempt);
                
                logger.warn("Manual payment retry failed for attempt: {}, invoice status: {}", 
                        attempt.getId(), paidInvoice.getStatus());
                return new RetryResult(false, "Payment retry failed: Invoice status is " + paidInvoice.getStatus(), 
                        "Invoice status: " + paidInvoice.getStatus());
            }
            
        } catch (StripeException e) {
            logger.error("Stripe error during manual payment retry for attempt {}: {}", attempt.getId(), e.getMessage());
            
            attempt.setStatus("PENDING_RETRY"); // Stripe will continue automatic retries
            attempt.setFailureReason("Manual retry failed: " + e.getMessage());
            attemptRepository.save(attempt);
            
            return new RetryResult(false, "Stripe error during retry", "Stripe error: " + e.getMessage());
        } catch (Exception e) {
            logger.error("Error during manual payment retry for attempt {}: {}", attempt.getId(), e.getMessage(), e);
            
            attempt.setStatus("PENDING_RETRY");
            attempt.setFailureReason("Manual retry failed: " + e.getMessage());
            attemptRepository.save(attempt);
            
            return new RetryResult(false, "Error during retry", "Error: " + e.getMessage());
        }
    }
    
    /**
     * Result of a manual payment retry attempt
     */
    public static class RetryResult {
        private final boolean success;
        private final String message;
        private final String error;
        
        public RetryResult(boolean success, String message, String error) {
            this.success = success;
            this.message = message;
            this.error = error;
        }
        
        public boolean isSuccess() { return success; }
        public String getMessage() { return message; }
        public String getError() { return error; }
    }
}
