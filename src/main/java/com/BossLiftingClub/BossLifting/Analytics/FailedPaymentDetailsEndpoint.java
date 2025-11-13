package com.BossLiftingClub.BossLifting.Analytics;

import com.BossLiftingClub.BossLifting.Club.Club;
import com.BossLiftingClub.BossLifting.Club.ClubRepository;
import com.BossLiftingClub.BossLifting.User.ClubUser.UserClub;
import com.BossLiftingClub.BossLifting.User.ClubUser.UserClubRepository;
import com.BossLiftingClub.BossLifting.User.User;
import com.BossLiftingClub.BossLifting.User.UserRepository;
import com.stripe.model.Invoice;
import com.stripe.model.InvoiceLineItem;
import com.stripe.model.Customer;
import com.stripe.param.InvoiceListParams;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;

@RestController
@RequestMapping("/api/analytics")
public class FailedPaymentDetailsEndpoint {

    private static final Logger logger = LoggerFactory.getLogger(FailedPaymentDetailsEndpoint.class);

    @Autowired
    private final ClubRepository clubRepository;

    @Autowired
    private final UserClubRepository userClubRepository;

    @Autowired
    private final UserRepository userRepository;

    public FailedPaymentDetailsEndpoint(ClubRepository clubRepository, UserClubRepository userClubRepository, UserRepository userRepository) {
        this.clubRepository = clubRepository;
        this.userClubRepository = userClubRepository;
        this.userRepository = userRepository;
    }

    /**
     * Get detailed failed payment information with member details
     * GET /api/analytics/failed-payments-details?clubTag={tag}&startDate={start}&endDate={end}
     */
    @GetMapping("/failed-payments-details")
    @Transactional
    public List<FailedPaymentDetail> getFailedPaymentsDetails(
            @RequestParam String clubTag,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {
        try {
            logger.info("Fetching failed payment details for clubTag={}, startDate={}, endDate={}", clubTag, startDate, endDate);

            Club club = clubRepository.findByClubTag(clubTag)
                    .orElseThrow(() -> new RuntimeException("Club not found with tag: " + clubTag));

            String stripeAccountId = club.getStripeAccountId();

            if (stripeAccountId == null || stripeAccountId.isEmpty()) {
                throw new RuntimeException("Club does not have Stripe configured");
            }

            // Parse date range
            LocalDateTime start = null;
            LocalDateTime end = LocalDateTime.now();

            try {
                if (startDate != null && !startDate.isEmpty()) {
                    start = java.time.ZonedDateTime.parse(startDate).toLocalDateTime();
                }
                if (endDate != null && !endDate.isEmpty()) {
                    end = java.time.ZonedDateTime.parse(endDate).toLocalDateTime();
                }
            } catch (Exception e) {
                logger.error("Error parsing dates: {}", e.getMessage());
                return new ArrayList<>();
            }

            long startEpoch = start != null ? start.atZone(ZoneId.systemDefault()).toEpochSecond() : 0;
            long endEpoch = end.atZone(ZoneId.systemDefault()).toEpochSecond();

            // Fetch failed invoices from Stripe
            com.stripe.net.RequestOptions requestOptions = com.stripe.net.RequestOptions.builder()
                    .setStripeAccount(stripeAccountId)
                    .build();

            List<FailedPaymentDetail> failedPayments = new ArrayList<>();

            // Fetch OPEN invoices (failed/unpaid)
            InvoiceListParams openParams = InvoiceListParams.builder()
                    .setStatus(InvoiceListParams.Status.OPEN)
                    .setLimit(100L)
                    .build();

            for (Invoice invoice : Invoice.list(openParams, requestOptions).autoPagingIterable()) {
                if (start != null && invoice.getCreated() < startEpoch) continue;
                if (invoice.getCreated() > endEpoch) continue;

                FailedPaymentDetail detail = createFailedPaymentDetail(invoice, club.getId());
                if (detail != null) {
                    failedPayments.add(detail);
                }
            }

            // Fetch UNCOLLECTIBLE invoices
            InvoiceListParams uncollectibleParams = InvoiceListParams.builder()
                    .setStatus(InvoiceListParams.Status.UNCOLLECTIBLE)
                    .setLimit(100L)
                    .build();

            for (Invoice invoice : Invoice.list(uncollectibleParams, requestOptions).autoPagingIterable()) {
                if (start != null && invoice.getCreated() < startEpoch) continue;
                if (invoice.getCreated() > endEpoch) continue;

                FailedPaymentDetail detail = createFailedPaymentDetail(invoice, club.getId());
                if (detail != null) {
                    failedPayments.add(detail);
                }
            }

            logger.info("Found {} failed payments for clubTag={}", failedPayments.size(), clubTag);
            return failedPayments;

        } catch (Exception e) {
            logger.error("Error fetching failed payment details: {}", e.getMessage(), e);
            return new ArrayList<>();
        }
    }

    private FailedPaymentDetail createFailedPaymentDetail(Invoice invoice, Long clubId) {
        try {
            String customerId = invoice.getCustomer();
            if (customerId == null) return null;

            // Find the user by Stripe customer ID
            User user = userRepository.findByUserStripeMemberId(customerId);
            if (user == null) {
                logger.warn("User not found for Stripe customer ID: {}", customerId);
                return null;
            }

            // Find the UserClub record
            Optional<UserClub> userClubOpt = userClubRepository.findByUserIdAndClubId(user.getId(), clubId);
            if (!userClubOpt.isPresent()) {
                logger.warn("UserClub not found for userId={}, clubId={}", user.getId(), clubId);
                return null;
            }

            UserClub userClub = userClubOpt.get();

            // Get customer details from Stripe
            Customer customer = null;
            try {
                customer = Customer.retrieve(customerId);
            } catch (Exception e) {
                logger.warn("Could not retrieve customer details for: {}", customerId);
            }

            // Calculate total amount from invoice line items
            double totalAmount = 0.0;
            if (invoice.getLines() != null && invoice.getLines().getData() != null) {
                for (InvoiceLineItem line : invoice.getLines().getData()) {
                    totalAmount += line.getAmount() / 100.0;
                }
            }

            // Create the detail object
            FailedPaymentDetail detail = new FailedPaymentDetail();
            detail.setInvoiceId(invoice.getId());
            detail.setUserClubId(userClub.getId());
            detail.setUserId(user.getId());
            detail.setUserName(user.getFirstName() + " " + user.getLastName());
            detail.setUserEmail(user.getEmail());
            detail.setAmount(totalAmount);
            detail.setStatus(invoice.getStatus());
            detail.setCreatedAt(LocalDateTime.ofEpochSecond(invoice.getCreated(), 0, ZoneId.systemDefault().getRules().getOffset(LocalDateTime.now())));
            detail.setFailureReason(getFailureReason(invoice, customer));
            detail.setDueDate(invoice.getDueDate() != null ?
                LocalDateTime.ofEpochSecond(invoice.getDueDate(), 0, ZoneId.systemDefault().getRules().getOffset(LocalDateTime.now())) :
                null);

            return detail;

        } catch (Exception e) {
            logger.error("Error creating failed payment detail: {}", e.getMessage(), e);
            return null;
        }
    }

    private String getFailureReason(Invoice invoice, Customer customer) {
        // Try to get failure reason from invoice
        if (invoice.getLastFinalizationError() != null && invoice.getLastFinalizationError().getMessage() != null) {
            return invoice.getLastFinalizationError().getMessage();
        }

        // Check if payment was attempted
        if (invoice.getAttempted() != null && invoice.getAttempted()) {
            if (invoice.getCharge() != null) {
                try {
                    com.stripe.model.Charge charge = com.stripe.model.Charge.retrieve(invoice.getCharge());
                    if (charge.getFailureMessage() != null) {
                        return charge.getFailureMessage();
                    }
                } catch (Exception e) {
                    logger.debug("Could not retrieve charge details: {}", e.getMessage());
                }
            }
            return "Payment failed - please update payment method";
        }

        // Check customer default payment method status
        if (customer != null && customer.getInvoiceSettings() != null &&
            customer.getInvoiceSettings().getDefaultPaymentMethod() == null) {
            return "No payment method on file";
        }

        return "Payment pending";
    }

    public static class FailedPaymentDetail {
        private String invoiceId;
        private Long userClubId;
        private Long userId;
        private String userName;
        private String userEmail;
        private Double amount;
        private String status;
        private LocalDateTime createdAt;
        private LocalDateTime dueDate;
        private String failureReason;

        public String getInvoiceId() { return invoiceId; }
        public void setInvoiceId(String invoiceId) { this.invoiceId = invoiceId; }

        public Long getUserClubId() { return userClubId; }
        public void setUserClubId(Long userClubId) { this.userClubId = userClubId; }

        public Long getUserId() { return userId; }
        public void setUserId(Long userId) { this.userId = userId; }

        public String getUserName() { return userName; }
        public void setUserName(String userName) { this.userName = userName; }

        public String getUserEmail() { return userEmail; }
        public void setUserEmail(String userEmail) { this.userEmail = userEmail; }

        public Double getAmount() { return amount; }
        public void setAmount(Double amount) { this.amount = amount; }

        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }

        public LocalDateTime getCreatedAt() { return createdAt; }
        public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

        public LocalDateTime getDueDate() { return dueDate; }
        public void setDueDate(LocalDateTime dueDate) { this.dueDate = dueDate; }

        public String getFailureReason() { return failureReason; }
        public void setFailureReason(String failureReason) { this.failureReason = failureReason; }
    }
}
