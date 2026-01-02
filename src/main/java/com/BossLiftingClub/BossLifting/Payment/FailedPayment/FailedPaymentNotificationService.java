package com.BossLiftingClub.BossLifting.Payment.FailedPayment;

import com.BossLiftingClub.BossLifting.Business.Business;
import com.BossLiftingClub.BossLifting.Business.BusinessRepository;
import com.BossLiftingClub.BossLifting.Email.EmailService;
import com.BossLiftingClub.BossLifting.User.User;
import com.BossLiftingClub.BossLifting.User.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;

@Service
public class FailedPaymentNotificationService {
    
    private static final Logger logger = LoggerFactory.getLogger(FailedPaymentNotificationService.class);
    
    @Autowired
    private EmailService emailService;
    
    @Autowired
    private UserRepository userRepository;
    
    @Autowired
    private BusinessRepository businessRepository;
    
    /**
     * Send immediate notification when payment fails
     */
    public void sendInitialFailureNotification(FailedPaymentAttempt attempt) {
        try {
            User user = userRepository.findById(attempt.getUserId())
                    .orElseThrow(() -> new RuntimeException("User not found: " + attempt.getUserId()));
            
            Business business = businessRepository.findById(attempt.getBusinessId())
                    .orElseThrow(() -> new RuntimeException("Business not found: " + attempt.getBusinessId()));
            
            String subject = "Payment Failed - Action Required";
            String body = buildInitialFailureEmail(user, business, attempt);
            
            emailService.sendEmail(user.getEmail(), subject, body, business.getContactEmail());
            logger.info("Sent initial failure notification to user: {}", user.getEmail());
        } catch (Exception e) {
            logger.error("Failed to send initial failure notification: {}", e.getMessage(), e);
        }
    }
    
    /**
     * Send notification before retry attempt
     * Note: This is currently not used since Stripe handles automatic retries
     * Could be used if we want to send notifications 24h before Stripe's scheduled retry
     */
    @Deprecated
    public void sendPreRetryNotification(FailedPaymentAttempt attempt) {
        // Not currently used - Stripe handles automatic retries
        // Could be implemented if we want to notify customers before Stripe's scheduled retry
    }
    
    /**
     * Send notification after successful retry
     */
    public void sendRetrySuccessNotification(FailedPaymentAttempt attempt) {
        try {
            User user = userRepository.findById(attempt.getUserId())
                    .orElseThrow(() -> new RuntimeException("User not found: " + attempt.getUserId()));
            
            Business business = businessRepository.findById(attempt.getBusinessId())
                    .orElseThrow(() -> new RuntimeException("Business not found: " + attempt.getBusinessId()));
            
            String subject = "Payment Successful - Thank You!";
            String body = buildRetrySuccessEmail(user, business, attempt);
            
            emailService.sendEmail(user.getEmail(), subject, body, business.getContactEmail());
            logger.info("Sent retry success notification to user: {}", user.getEmail());
        } catch (Exception e) {
            logger.error("Failed to send retry success notification: {}", e.getMessage(), e);
        }
    }
    
    /**
     * Send notification when all retries are exhausted
     */
    public void sendRetryExhaustedNotification(FailedPaymentAttempt attempt) {
        try {
            User user = userRepository.findById(attempt.getUserId())
                    .orElseThrow(() -> new RuntimeException("User not found: " + attempt.getUserId()));
            
            Business business = businessRepository.findById(attempt.getBusinessId())
                    .orElseThrow(() -> new RuntimeException("Business not found: " + attempt.getBusinessId()));
            
            String subject = "Urgent: Payment Failed - Membership at Risk";
            String body = buildRetryExhaustedEmail(user, business, attempt);
            
            emailService.sendEmail(user.getEmail(), subject, body, business.getContactEmail());
            logger.info("Sent retry exhausted notification to user: {}", user.getEmail());
        } catch (Exception e) {
            logger.error("Failed to send retry exhausted notification: {}", e.getMessage(), e);
        }
    }
    
    private String buildInitialFailureEmail(User user, Business business, FailedPaymentAttempt attempt) {
        return String.format(
            "Hello %s,\n\n" +
            "We were unable to process your payment of $%.2f for %s.\n\n" +
            "Failure Reason: %s\n\n" +
            "We will automatically retry this payment. Please ensure your payment method is up to date.\n\n" +
            "Next retry: %s\n\n" +
            "To update your payment method, please log in to your account or contact us.\n\n" +
            "Thank you,\n" +
            "%s Team",
            user.getFirstName(),
            attempt.getAmount().doubleValue(),
            business.getTitle(),
            attempt.getFailureReason() != null ? attempt.getFailureReason() : "Payment method issue",
            attempt.getNextRetryDate() != null ? attempt.getNextRetryDate().format(DateTimeFormatter.ofPattern("MMMM dd, yyyy 'at' h:mm a")) : "Within 24 hours",
            business.getTitle()
        );
    }
    
    private String buildPreRetryEmail(User user, Business business, FailedPaymentAttempt attempt) {
        return String.format(
            "Hello %s,\n\n" +
            "We will attempt to process your payment of $%.2f for %s within the next 24 hours.\n\n" +
            "This is retry attempt #%d of %d.\n\n" +
            "Please ensure your payment method is up to date to avoid any service interruptions.\n\n" +
            "To update your payment method, please log in to your account.\n\n" +
            "Thank you,\n" +
            "%s Team",
            user.getFirstName(),
            attempt.getAmount().doubleValue(),
            business.getTitle(),
            attempt.getRetryAttemptCount() + 1,
            attempt.getMaxRetryAttempts(),
            business.getTitle()
        );
    }
    
    private String buildRetrySuccessEmail(User user, Business business, FailedPaymentAttempt attempt) {
        return String.format(
            "Hello %s,\n\n" +
            "Great news! We successfully processed your payment of $%.2f for %s.\n\n" +
            "Your membership remains active and in good standing.\n\n" +
            "Thank you for your prompt attention to this matter.\n\n" +
            "Best regards,\n" +
            "%s Team",
            user.getFirstName(),
            attempt.getAmount().doubleValue(),
            business.getTitle(),
            business.getTitle()
        );
    }
    
    private String buildRetryExhaustedEmail(User user, Business business, FailedPaymentAttempt attempt) {
        return String.format(
            "Hello %s,\n\n" +
            "URGENT: We were unable to process your payment of $%.2f for %s after %d attempts.\n\n" +
            "Failure Reason: %s\n\n" +
            "Your membership may be at risk of suspension. Please update your payment method immediately to avoid service interruption.\n\n" +
            "To update your payment method:\n" +
            "1. Log in to your account\n" +
            "2. Go to Payment Methods\n" +
            "3. Add or update your payment method\n\n" +
            "If you have any questions, please contact us immediately.\n\n" +
            "Thank you,\n" +
            "%s Team",
            user.getFirstName(),
            attempt.getAmount().doubleValue(),
            business.getTitle(),
            attempt.getRetryAttemptCount(),
            attempt.getFailureReason() != null ? attempt.getFailureReason() : "Payment method issue",
            business.getTitle()
        );
    }
}

