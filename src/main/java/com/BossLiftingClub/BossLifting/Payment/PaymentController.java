package com.BossLiftingClub.BossLifting.Payment;

import com.BossLiftingClub.BossLifting.Club.Club;
import com.BossLiftingClub.BossLifting.Club.ClubRepository;
import com.BossLiftingClub.BossLifting.Stripe.StripeService;
import com.BossLiftingClub.BossLifting.User.ClubUser.UserClub;
import com.BossLiftingClub.BossLifting.User.ClubUser.UserClubRepository;
import com.stripe.exception.StripeException;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/payment")
public class PaymentController {

    @Autowired
    private StripeService stripeService;

    @Autowired
    private UserClubRepository userClubRepository;

    @Autowired
    private ClubRepository clubRepository;

    @Autowired
    private JavaMailSender mailSender;

    @Autowired
    private ApplicationContext applicationContext;

    /**
     * Check if a user has a default payment method
     */
    @GetMapping("/check-payment-method")
    public ResponseEntity<?> checkPaymentMethod(
            @RequestParam Long userClubId
    ) {
        try {
            UserClub userClub = userClubRepository.findById(userClubId)
                    .orElseThrow(() -> new RuntimeException("UserClub not found"));

            String stripeCustomerId = userClub.getStripeId();
            if (stripeCustomerId == null || stripeCustomerId.isEmpty()) {
                return ResponseEntity.ok(Map.of("hasPaymentMethod", false));
            }

            Club club = userClub.getClub();
            if (!"COMPLETED".equals(club.getOnboardingStatus())) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(Map.of("error", "Stripe integration not complete. Please complete Stripe onboarding first."));
            }

            String stripeAccountId = club.getStripeAccountId();

            boolean hasPaymentMethod = stripeService.hasDefaultPaymentMethod(stripeCustomerId, stripeAccountId);

            return ResponseEntity.ok(Map.of("hasPaymentMethod", hasPaymentMethod));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to check payment method: " + e.getMessage()));
        }
    }

    /**
     * Get payment method details (brand and last 4 digits)
     */
    @GetMapping("/payment-method-details")
    public ResponseEntity<?> getPaymentMethodDetails(
            @RequestParam Long userClubId
    ) {
        try {
            UserClub userClub = userClubRepository.findById(userClubId)
                    .orElseThrow(() -> new RuntimeException("UserClub not found"));

            String stripeCustomerId = userClub.getStripeId();
            if (stripeCustomerId == null || stripeCustomerId.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("error", "No Stripe customer ID found"));
            }

            Club club = userClub.getClub();
            if (!"COMPLETED".equals(club.getOnboardingStatus())) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(Map.of("error", "Stripe integration not complete. Please complete Stripe onboarding first."));
            }

            String stripeAccountId = club.getStripeAccountId();

            Map<String, String> paymentMethodDetails = stripeService.getPaymentMethodDetails(stripeCustomerId, stripeAccountId);

            return ResponseEntity.ok(paymentMethodDetails);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to get payment method details: " + e.getMessage()));
        }
    }

    /**
     * Update payment method for a user
     */
    @PostMapping("/update-payment-method")
    public ResponseEntity<?> updatePaymentMethod(@RequestBody Map<String, Object> request) {
        try {
            Long userClubId = Long.parseLong(request.get("userClubId").toString());
            String paymentMethodId = (String) request.get("paymentMethodId");

            if (paymentMethodId == null || paymentMethodId.isEmpty()) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(Map.of("error", "Payment method ID is required"));
            }

            UserClub userClub = userClubRepository.findById(userClubId)
                    .orElseThrow(() -> new RuntimeException("UserClub not found"));

            String stripeCustomerId = userClub.getStripeId();
            if (stripeCustomerId == null || stripeCustomerId.isEmpty()) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(Map.of("error", "User does not have a Stripe customer ID"));
            }

            Club club = userClub.getClub();
            if (!"COMPLETED".equals(club.getOnboardingStatus())) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(Map.of("error", "Stripe integration not complete. Please complete Stripe onboarding first."));
            }

            if (club.getStripeAccountId() == null) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(Map.of("error", "Club does not have Stripe configured"));
            }

            // Attach and set as default payment method
            stripeService.attachPaymentMethodOnConnectedAccount(
                    stripeCustomerId,
                    paymentMethodId,
                    club.getStripeAccountId()
            );

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Payment method updated successfully"
            ));
        } catch (StripeException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Stripe error: " + e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to update payment method: " + e.getMessage()));
        }
    }

    /**
     * Create a Stripe Checkout Session for payment method collection
     */
    @PostMapping("/create-checkout-session")
    public ResponseEntity<?> createCheckoutSession(@RequestBody Map<String, Object> request) {
        try {
            Long userClubId = Long.parseLong(request.get("userClubId").toString());
            String successUrl = (String) request.get("successUrl");
            String cancelUrl = (String) request.get("cancelUrl");

            UserClub userClub = userClubRepository.findById(userClubId)
                    .orElseThrow(() -> new RuntimeException("UserClub not found"));

            String stripeCustomerId = userClub.getStripeId();
            if (stripeCustomerId == null || stripeCustomerId.isEmpty()) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(Map.of("error", "User does not have a Stripe customer ID"));
            }

            Club club = userClub.getClub();
            if (!"COMPLETED".equals(club.getOnboardingStatus())) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(Map.of("error", "Stripe integration not complete. Please complete Stripe onboarding first."));
            }

            if (club.getStripeAccountId() == null) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(Map.of("error", "Club does not have Stripe configured"));
            }

            String checkoutUrl = stripeService.createPaymentMethodCheckoutSession(
                    stripeCustomerId,
                    club.getStripeAccountId(),
                    successUrl,
                    cancelUrl
            );

            return ResponseEntity.ok(Map.of("url", checkoutUrl));
        } catch (StripeException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Stripe error: " + e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to create checkout session: " + e.getMessage()));
        }
    }

    /**
     * Send email with Stripe payment link
     */
    @PostMapping("/send-payment-link")
    public ResponseEntity<?> sendPaymentLink(@RequestBody Map<String, Object> request) {
        try {
            Long userClubId = Long.parseLong(request.get("userClubId").toString());
            String successUrl = (String) request.get("successUrl");
            String cancelUrl = (String) request.get("cancelUrl");

            UserClub userClub = userClubRepository.findById(userClubId)
                    .orElseThrow(() -> new RuntimeException("UserClub not found"));

            String stripeCustomerId = userClub.getStripeId();
            if (stripeCustomerId == null || stripeCustomerId.isEmpty()) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(Map.of("error", "User does not have a Stripe customer ID"));
            }

            Club club = userClub.getClub();
            if (!"COMPLETED".equals(club.getOnboardingStatus())) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(Map.of("error", "Stripe integration not complete. Please complete Stripe onboarding first."));
            }

            if (club.getStripeAccountId() == null) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(Map.of("error", "Club does not have Stripe configured"));
            }

            // Create checkout session
            String checkoutUrl = stripeService.createPaymentMethodCheckoutSession(
                    stripeCustomerId,
                    club.getStripeAccountId(),
                    successUrl,
                    cancelUrl
            );

            // Send email
            String userEmail = userClub.getUser().getEmail();
            String userName = userClub.getUser().getFirstName() + " " + userClub.getUser().getLastName();
            String clubName = club.getTitle();

            sendPaymentMethodEmail(userEmail, userName, clubName, checkoutUrl);

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Payment link sent to " + userEmail
            ));
        } catch (StripeException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Stripe error: " + e.getMessage()));
        } catch (MessagingException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to send email: " + e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to send payment link: " + e.getMessage()));
        }
    }

    /**
     * Get payment history for a user from Stripe
     * GET /api/payment/payment-history?userClubId={id}
     */
    @GetMapping("/payment-history")
    public ResponseEntity<?> getPaymentHistory(@RequestParam Long userClubId) {
        try {
            UserClub userClub = userClubRepository.findById(userClubId)
                    .orElseThrow(() -> new RuntimeException("UserClub not found"));

            String stripeCustomerId = userClub.getStripeId();
            if (stripeCustomerId == null || stripeCustomerId.isEmpty()) {
                return ResponseEntity.ok(List.of()); // Return empty list if no Stripe customer
            }

            Club club = userClub.getClub();
            if (!"COMPLETED".equals(club.getOnboardingStatus())) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(Map.of("error", "Stripe integration not complete. Please complete Stripe onboarding first."));
            }

            String stripeAccountId = club.getStripeAccountId();

            List<Map<String, Object>> paymentHistory = stripeService.getPaymentHistory(stripeCustomerId, stripeAccountId);

            return ResponseEntity.ok(paymentHistory);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to fetch payment history: " + e.getMessage()));
        }
    }

    /**
     * Helper method to send payment method collection email
     */
    /**
     * Create memberships and subscriptions after payment method has been added
     * POST /api/payment/create-memberships-after-payment
     */
    @PostMapping("/create-memberships-after-payment")
    public ResponseEntity<?> createMembershipsAfterPayment(@RequestBody Map<String, Object> request) {
        try {
            Long userClubId = Long.parseLong(request.get("userClubId").toString());
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> memberships = (List<Map<String, Object>>) request.get("memberships");

            if (memberships == null || memberships.isEmpty()) {
                return ResponseEntity.ok(Map.of(
                        "success", true,
                        "message", "No memberships to create"
                ));
            }

            // Create each membership (which will automatically create Stripe subscriptions)
            for (Map<String, Object> membership : memberships) {
                Long membershipId = Long.parseLong(membership.get("membershipId").toString());
                String anchorDateStr = (String) membership.get("anchorDate");
                String status = (String) membership.getOrDefault("status", "ACTIVE");

                // Parse anchor date
                java.time.LocalDateTime anchorDate;
                if (anchorDateStr != null && !anchorDateStr.isEmpty()) {
                    anchorDate = java.time.LocalDate.parse(anchorDateStr).atStartOfDay();
                } else {
                    anchorDate = java.time.LocalDateTime.now();
                }

                // Call the service to add membership (which will create subscription)
                com.BossLiftingClub.BossLifting.User.ClubUser.UserClubService userClubService =
                        applicationContext.getBean(com.BossLiftingClub.BossLifting.User.ClubUser.UserClubService.class);

                userClubService.addMembershipByUserClubId(userClubId, membershipId, status, anchorDate);
            }

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Memberships and subscriptions created successfully"
            ));
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to create memberships: " + e.getMessage()));
        }
    }

    /**
     * Webhook endpoint for connected account Stripe events
     * Handles subscription lifecycle events to keep database in sync with Stripe
     */
    @PostMapping("/webhook-connected")
    public ResponseEntity<String> handleConnectedAccountWebhook(
            @RequestBody String payload,
            @RequestHeader("Stripe-Signature") String sigHeader,
            @RequestHeader(value = "Stripe-Account", required = false) String stripeAccountId
    ) {
        try {
            // Extract Stripe account ID from payload
            com.google.gson.JsonObject jsonObject = com.google.gson.JsonParser.parseString(payload).getAsJsonObject();
            if (jsonObject.has("account")) {
                stripeAccountId = jsonObject.get("account").getAsString();
            }

            System.out.println("📨 Received webhook for account: " + stripeAccountId);

            // Parse the event
            com.stripe.model.Event event = com.stripe.model.Event.GSON.fromJson(payload, com.stripe.model.Event.class);
            String eventType = event.getType();

            System.out.println("🔔 Event type: " + eventType);

            // Get UserClubService
            com.BossLiftingClub.BossLifting.User.ClubUser.UserClubService userClubService =
                    applicationContext.getBean(com.BossLiftingClub.BossLifting.User.ClubUser.UserClubService.class);

            switch (eventType) {
                case "checkout.session.completed":
                    handleCheckoutSessionCompleted(event, stripeAccountId);
                    break;

                case "customer.subscription.updated":
                    handleSubscriptionUpdated(event, userClubService);
                    break;

                case "customer.subscription.deleted":
                    handleSubscriptionDeleted(event, userClubService);
                    break;

                case "customer.subscription.paused":
                    handleSubscriptionPaused(event, userClubService);
                    break;

                case "customer.subscription.resumed":
                    handleSubscriptionResumed(event, userClubService);
                    break;

                case "invoice.payment_failed":
                    handlePaymentFailed(event, userClubService);
                    break;

                case "invoice.payment_succeeded":
                    handlePaymentSucceeded(event, userClubService);
                    break;

                default:
                    System.out.println("ℹ️  Unhandled event type: " + eventType);
            }

            return ResponseEntity.ok("Webhook processed: " + eventType);
        } catch (Exception e) {
            System.err.println("❌ Error processing webhook: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.status(500).body("Webhook processing failed: " + e.getMessage());
        }
    }

    /**
     * Handle checkout.session.completed - Set payment method as default
     */
    private void handleCheckoutSessionCompleted(com.stripe.model.Event event, String stripeAccountId) {
        try {
            com.stripe.model.checkout.Session session = (com.stripe.model.checkout.Session)
                    event.getDataObjectDeserializer().getObject().orElse(null);

            if (session != null && "setup".equals(session.getMode())) {
                String customerId = session.getCustomer();
                String setupIntentId = session.getSetupIntent();

                if (setupIntentId != null && stripeAccountId != null) {
                    com.stripe.net.RequestOptions requestOptions = com.stripe.net.RequestOptions.builder()
                            .setStripeAccount(stripeAccountId)
                            .build();

                    com.stripe.model.SetupIntent setupIntent = com.stripe.model.SetupIntent.retrieve(setupIntentId, requestOptions);
                    String paymentMethodId = setupIntent.getPaymentMethod();

                    if (paymentMethodId != null) {
                        stripeService.attachPaymentMethodOnConnectedAccount(customerId, paymentMethodId, stripeAccountId);
                        System.out.println("✅ Payment method set as default for customer: " + customerId);
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("❌ Error in handleCheckoutSessionCompleted: " + e.getMessage());
        }
    }

    /**
     * Handle customer.subscription.updated - Update membership status based on Stripe state
     */
    private void handleSubscriptionUpdated(com.stripe.model.Event event,
            com.BossLiftingClub.BossLifting.User.ClubUser.UserClubService userClubService) {
        try {
            com.stripe.model.Subscription subscription = (com.stripe.model.Subscription)
                    event.getDataObjectDeserializer().getObject().orElse(null);

            if (subscription == null) return;

            String subscriptionId = subscription.getId();
            String status = subscription.getStatus();

            System.out.println("📝 Subscription updated: " + subscriptionId + " -> " + status);

            // Find the membership by Stripe subscription ID
            com.BossLiftingClub.BossLifting.User.ClubUser.UserClubMembership membership =
                    findMembershipByStripeSubscriptionId(subscriptionId);

            if (membership == null) {
                System.out.println("⚠️  No membership found for subscription: " + subscriptionId);
                return;
            }

            // Check if pause_collection is set (subscription is paused)
            if (subscription.getPauseCollection() != null) {
                // Subscription is paused or scheduled to pause
                if ("paused".equals(subscription.getStatus()) ||
                    subscription.getPauseCollection().getResumesAt() != null) {

                    // Update status to PAUSED if it was PAUSE_SCHEDULED
                    if ("PAUSE_SCHEDULED".equalsIgnoreCase(membership.getStatus())) {
                        membership.setStatus("PAUSED");
                        userClubService.updateUserClubMembershipById(
                                membership.getId(), "PAUSED", null, null, null);
                        System.out.println("✅ Membership " + membership.getId() + " status updated to PAUSED");
                    }
                }
            }

            // Check if cancel_at_period_end is set
            if (Boolean.TRUE.equals(subscription.getCancelAtPeriodEnd())) {
                // Subscription is scheduled for cancellation
                if (!"CANCELLING".equalsIgnoreCase(membership.getStatus())) {
                    long cancelAt = subscription.getCurrentPeriodEnd();
                    java.time.LocalDateTime cancelDate = java.time.LocalDateTime.ofEpochSecond(
                            cancelAt, 0, java.time.ZoneOffset.UTC);

                    userClubService.updateUserClubMembershipById(
                            membership.getId(), "CANCELLING", null, cancelDate, null);
                    System.out.println("✅ Membership " + membership.getId() + " scheduled for cancellation at " + cancelDate);
                }
            }

            // If subscription becomes active after being paused
            if ("active".equals(status) && "PAUSED".equalsIgnoreCase(membership.getStatus())) {
                userClubService.updateUserClubMembershipById(
                        membership.getId(), "ACTIVE", null, null, null);
                System.out.println("✅ Membership " + membership.getId() + " reactivated");
            }

        } catch (Exception e) {
            System.err.println("❌ Error in handleSubscriptionUpdated: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Handle customer.subscription.deleted - Remove membership from database
     */
    private void handleSubscriptionDeleted(com.stripe.model.Event event,
            com.BossLiftingClub.BossLifting.User.ClubUser.UserClubService userClubService) {
        try {
            com.stripe.model.Subscription subscription = (com.stripe.model.Subscription)
                    event.getDataObjectDeserializer().getObject().orElse(null);

            if (subscription == null) return;

            String subscriptionId = subscription.getId();
            System.out.println("🗑️  Subscription deleted: " + subscriptionId);

            // Find and delete the membership
            com.BossLiftingClub.BossLifting.User.ClubUser.UserClubMembership membership =
                    findMembershipByStripeSubscriptionId(subscriptionId);

            if (membership != null) {
                // Delete the membership from database since it's cancelled in Stripe
                com.BossLiftingClub.BossLifting.User.ClubUser.UserClub userClub = membership.getUserClub();
                userClub.removeMembership(membership);
                userClubRepository.save(userClub);
                System.out.println("✅ Membership " + membership.getId() + " deleted from database");
            }

        } catch (Exception e) {
            System.err.println("❌ Error in handleSubscriptionDeleted: " + e.getMessage());
        }
    }

    /**
     * Handle customer.subscription.paused - Update membership to PAUSED status
     */
    private void handleSubscriptionPaused(com.stripe.model.Event event,
            com.BossLiftingClub.BossLifting.User.ClubUser.UserClubService userClubService) {
        try {
            com.stripe.model.Subscription subscription = (com.stripe.model.Subscription)
                    event.getDataObjectDeserializer().getObject().orElse(null);

            if (subscription == null) return;

            String subscriptionId = subscription.getId();
            System.out.println("⏸️  Subscription paused: " + subscriptionId);

            com.BossLiftingClub.BossLifting.User.ClubUser.UserClubMembership membership =
                    findMembershipByStripeSubscriptionId(subscriptionId);

            if (membership != null && !"PAUSED".equalsIgnoreCase(membership.getStatus())) {
                userClubService.updateUserClubMembershipById(
                        membership.getId(), "PAUSED", null, null, null);
                System.out.println("✅ Membership " + membership.getId() + " status updated to PAUSED");
            }

        } catch (Exception e) {
            System.err.println("❌ Error in handleSubscriptionPaused: " + e.getMessage());
        }
    }

    /**
     * Handle customer.subscription.resumed - Update membership to ACTIVE status
     */
    private void handleSubscriptionResumed(com.stripe.model.Event event,
            com.BossLiftingClub.BossLifting.User.ClubUser.UserClubService userClubService) {
        try {
            com.stripe.model.Subscription subscription = (com.stripe.model.Subscription)
                    event.getDataObjectDeserializer().getObject().orElse(null);

            if (subscription == null) return;

            String subscriptionId = subscription.getId();
            System.out.println("▶️  Subscription resumed: " + subscriptionId);

            com.BossLiftingClub.BossLifting.User.ClubUser.UserClubMembership membership =
                    findMembershipByStripeSubscriptionId(subscriptionId);

            if (membership != null && !"ACTIVE".equalsIgnoreCase(membership.getStatus())) {
                // Clear pause dates and set to active
                membership.setPauseStartDate(null);
                membership.setPauseEndDate(null);
                userClubService.updateUserClubMembershipById(
                        membership.getId(), "ACTIVE", null, null, null);
                System.out.println("✅ Membership " + membership.getId() + " resumed and set to ACTIVE");
            }

        } catch (Exception e) {
            System.err.println("❌ Error in handleSubscriptionResumed: " + e.getMessage());
        }
    }

    /**
     * Handle invoice.payment_failed - Mark membership as payment failed
     */
    private void handlePaymentFailed(com.stripe.model.Event event,
            com.BossLiftingClub.BossLifting.User.ClubUser.UserClubService userClubService) {
        try {
            com.stripe.model.Invoice invoice = (com.stripe.model.Invoice)
                    event.getDataObjectDeserializer().getObject().orElse(null);

            if (invoice == null) return;

            String subscriptionId = invoice.getSubscription();
            System.out.println("❌ Payment failed for subscription: " + subscriptionId);

            com.BossLiftingClub.BossLifting.User.ClubUser.UserClubMembership membership =
                    findMembershipByStripeSubscriptionId(subscriptionId);

            if (membership != null) {
                // You could add a payment_failed status or send notification
                System.out.println("⚠️  Payment failed for membership: " + membership.getId());
                // Optionally: Update status or send email notification
            }

        } catch (Exception e) {
            System.err.println("❌ Error in handlePaymentFailed: " + e.getMessage());
        }
    }

    /**
     * Handle invoice.payment_succeeded - Confirm payment success
     */
    private void handlePaymentSucceeded(com.stripe.model.Event event,
            com.BossLiftingClub.BossLifting.User.ClubUser.UserClubService userClubService) {
        try {
            com.stripe.model.Invoice invoice = (com.stripe.model.Invoice)
                    event.getDataObjectDeserializer().getObject().orElse(null);

            if (invoice == null) return;

            String subscriptionId = invoice.getSubscription();
            System.out.println("✅ Payment succeeded for subscription: " + subscriptionId);

            // Ensure membership is active if payment succeeded
            com.BossLiftingClub.BossLifting.User.ClubUser.UserClubMembership membership =
                    findMembershipByStripeSubscriptionId(subscriptionId);

            if (membership != null &&
                !"ACTIVE".equalsIgnoreCase(membership.getStatus()) &&
                !"PAUSE_SCHEDULED".equalsIgnoreCase(membership.getStatus()) &&
                !"CANCELLING".equalsIgnoreCase(membership.getStatus())) {

                userClubService.updateUserClubMembershipById(
                        membership.getId(), "ACTIVE", null, null, null);
                System.out.println("✅ Membership " + membership.getId() + " reactivated after successful payment");
            }

        } catch (Exception e) {
            System.err.println("❌ Error in handlePaymentSucceeded: " + e.getMessage());
        }
    }

    /**
     * Helper method to find a membership by Stripe subscription ID
     */
    private com.BossLiftingClub.BossLifting.User.ClubUser.UserClubMembership findMembershipByStripeSubscriptionId(String subscriptionId) {
        List<com.BossLiftingClub.BossLifting.User.ClubUser.UserClub> allUserClubs = userClubRepository.findAll();

        for (com.BossLiftingClub.BossLifting.User.ClubUser.UserClub userClub : allUserClubs) {
            for (com.BossLiftingClub.BossLifting.User.ClubUser.UserClubMembership membership : userClub.getUserClubMemberships()) {
                if (subscriptionId.equals(membership.getStripeSubscriptionId())) {
                    return membership;
                }
            }
        }

        return null;
    }

    /**
     * Process a refund for a payment
     * POST /api/payment/process-refund
     */
    @PostMapping("/process-refund")
    public ResponseEntity<?> processRefund(@RequestBody Map<String, Object> request) {
        try {
            String chargeId = (String) request.get("chargeId");
            Long userClubId = request.get("userClubId") != null ?
                Long.parseLong(request.get("userClubId").toString()) : null;

            // Amount in dollars from frontend - convert to cents
            Double amountInDollars = request.get("amount") != null ?
                ((Number) request.get("amount")).doubleValue() : null;
            Long amountInCents = amountInDollars != null ?
                Math.round(amountInDollars * 100) : null;

            if (chargeId == null || chargeId.isEmpty()) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(Map.of("error", "Charge ID is required"));
            }

            // Get stripe account ID from user club if provided
            String stripeAccountId = null;
            if (userClubId != null) {
                UserClub userClub = userClubRepository.findById(userClubId)
                        .orElseThrow(() -> new RuntimeException("UserClub not found"));
                Club club = userClub.getClub();
                if (!"COMPLETED".equals(club.getOnboardingStatus())) {
                    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                            .body(Map.of("error", "Stripe integration not complete. Please complete Stripe onboarding first."));
                }
                stripeAccountId = club.getStripeAccountId();
            }

            System.out.println("💰 Processing refund for charge: " + chargeId);
            System.out.println("💵 Amount: " + (amountInCents != null ?
                "$" + (amountInCents / 100.0) + " (" + amountInCents + " cents)" : "Full refund"));

            // Process refund
            Map<String, Object> refundResult = stripeService.createRefund(
                    chargeId,
                    amountInCents,
                    stripeAccountId
            );

            String status = (String) refundResult.get("status");
            System.out.println("✅ Refund status: " + status);

            if ("succeeded".equals(status)) {
                return ResponseEntity.ok(Map.of(
                        "success", true,
                        "message", "Refund processed successfully!",
                        "refundId", refundResult.get("id"),
                        "amount", ((Number) refundResult.get("amount")).doubleValue() / 100.0,
                        "status", status
                ));
            } else {
                return ResponseEntity.ok(Map.of(
                        "success", true,
                        "message", "Refund initiated",
                        "refundId", refundResult.get("id"),
                        "amount", ((Number) refundResult.get("amount")).doubleValue() / 100.0,
                        "status", status
                ));
            }

        } catch (StripeException e) {
            System.err.println("❌ Stripe error: " + e.getMessage());
            String errorMessage = "Refund failed: " + e.getMessage();

            if (e.getCode() != null) {
                switch (e.getCode()) {
                    case "charge_already_refunded":
                        errorMessage = "This charge has already been refunded.";
                        break;
                    case "amount_too_large":
                        errorMessage = "Refund amount exceeds the original charge amount.";
                        break;
                    default:
                        errorMessage = "Refund failed: " + e.getMessage();
                }
            }

            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of(
                            "error", errorMessage,
                            "stripeError", e.getCode() != null ? e.getCode() : "unknown"
                    ));
        } catch (Exception e) {
            System.err.println("❌ Error processing refund: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to process refund: " + e.getMessage()));
        }
    }

    /**
     * Process a one-time payment for product purchases
     * POST /api/payment/process-product-payment
     */
    @PostMapping("/process-product-payment")
    public ResponseEntity<?> processProductPayment(@RequestBody Map<String, Object> request) {
        try {
            Long userClubId = Long.parseLong(request.get("userClubId").toString());
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> items = (List<Map<String, Object>>) request.get("items");

            if (items == null || items.isEmpty()) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(Map.of("error", "No items in cart"));
            }

            // Get user club
            UserClub userClub = userClubRepository.findById(userClubId)
                    .orElseThrow(() -> new RuntimeException("UserClub not found"));

            String stripeCustomerId = userClub.getStripeId();
            if (stripeCustomerId == null || stripeCustomerId.isEmpty()) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(Map.of("error", "User does not have a Stripe customer ID"));
            }

            Club club = userClub.getClub();
            if (!"COMPLETED".equals(club.getOnboardingStatus())) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(Map.of("error", "Stripe integration not complete. Please complete Stripe onboarding first."));
            }

            if (club.getStripeAccountId() == null) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(Map.of("error", "Club does not have Stripe configured"));
            }

            String stripeAccountId = club.getStripeAccountId();

            // Check if user has payment method
            boolean hasPaymentMethod = stripeService.hasDefaultPaymentMethod(stripeCustomerId, stripeAccountId);
            if (!hasPaymentMethod) {
                return ResponseEntity.status(HttpStatus.PAYMENT_REQUIRED)
                        .body(Map.of(
                                "error", "No payment method on file",
                                "requiresPaymentMethod", true
                        ));
            }

            // Calculate total amount and build description
            double subtotal = 0.0;
            StringBuilder description = new StringBuilder("Product purchase: ");
            for (int i = 0; i < items.size(); i++) {
                Map<String, Object> item = items.get(i);
                String productName = (String) item.get("name");
                int quantity = ((Number) item.get("quantity")).intValue();
                double price = ((Number) item.get("price")).doubleValue();
                double itemTotal = price * quantity;
                subtotal += itemTotal;

                description.append(quantity).append("x ").append(productName);
                if (i < items.size() - 1) {
                    description.append(", ");
                }
            }

            // Add tax (8% as per frontend)
            double taxRate = 0.08;
            double tax = subtotal * taxRate;
            double total = subtotal + tax;

            // Convert to cents for Stripe
            long amountInCents = Math.round(total * 100);

            System.out.println("💳 Processing product payment for userClubId: " + userClubId);
            System.out.println("💰 Amount: $" + total + " (" + amountInCents + " cents)");
            System.out.println("📝 Description: " + description);

            // Process payment
            Map<String, Object> paymentResult = stripeService.createOneTimePayment(
                    stripeCustomerId,
                    amountInCents,
                    "usd",
                    description.toString(),
                    stripeAccountId
            );

            String status = (String) paymentResult.get("status");
            System.out.println("✅ Payment status: " + status);

            if ("succeeded".equals(status)) {
                // Payment successful
                return ResponseEntity.ok(Map.of(
                        "success", true,
                        "message", "Payment processed successfully!",
                        "paymentId", paymentResult.get("id"),
                        "amount", total,
                        "receiptUrl", paymentResult.getOrDefault("receiptUrl", "")
                ));
            } else if ("requires_action".equals(status) || "requires_confirmation".equals(status)) {
                // Payment requires additional action (e.g., 3D Secure)
                return ResponseEntity.status(HttpStatus.PAYMENT_REQUIRED)
                        .body(Map.of(
                                "error", "Payment requires additional authentication",
                                "requiresAction", true,
                                "paymentId", paymentResult.get("id")
                        ));
            } else {
                // Payment failed
                return ResponseEntity.status(HttpStatus.PAYMENT_REQUIRED)
                        .body(Map.of(
                                "error", "Payment failed with status: " + status,
                                "status", status
                        ));
            }

        } catch (StripeException e) {
            System.err.println("❌ Stripe error: " + e.getMessage());
            String errorMessage = e.getMessage();

            // Handle specific Stripe error types
            if (e.getCode() != null) {
                switch (e.getCode()) {
                    case "card_declined":
                        errorMessage = "Your card was declined. Please try a different payment method.";
                        break;
                    case "insufficient_funds":
                        errorMessage = "Insufficient funds on your card.";
                        break;
                    case "expired_card":
                        errorMessage = "Your card has expired. Please update your payment method.";
                        break;
                    default:
                        errorMessage = "Payment failed: " + e.getMessage();
                }
            }

            return ResponseEntity.status(HttpStatus.PAYMENT_REQUIRED)
                    .body(Map.of(
                            "error", errorMessage,
                            "stripeError", e.getCode() != null ? e.getCode() : "unknown"
                    ));
        } catch (Exception e) {
            System.err.println("❌ Error processing product payment: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to process payment: " + e.getMessage()));
        }
    }

    private void sendPaymentMethodEmail(String toEmail, String userName, String clubName, String paymentLink)
            throws MessagingException {
        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

        helper.setTo(toEmail);
        helper.setSubject("Add Payment Method - " + clubName);

        String htmlContent = String.format("""
                <!DOCTYPE html>
                <html>
                <head>
                    <style>
                        body { font-family: Arial, sans-serif; line-height: 1.6; color: #333; }
                        .container { max-width: 600px; margin: 0 auto; padding: 20px; }
                        .header { background: linear-gradient(135deg, #10b981, #3b82f6); color: white; padding: 30px; text-align: center; border-radius: 10px 10px 0 0; }
                        .content { background: #f9fafb; padding: 30px; border-radius: 0 0 10px 10px; }
                        .button { display: inline-block; background: linear-gradient(135deg, #10b981, #059669); color: white; padding: 15px 30px; text-decoration: none; border-radius: 8px; font-weight: bold; margin: 20px 0; }
                        .footer { text-align: center; margin-top: 20px; color: #6b7280; font-size: 12px; }
                    </style>
                </head>
                <body>
                    <div class="container">
                        <div class="header">
                            <h1>Add Your Payment Method</h1>
                        </div>
                        <div class="content">
                            <p>Hi %s,</p>
                            <p>Welcome to %s! To complete your membership setup, please add a payment method for your subscription.</p>
                            <p>Click the button below to securely add your payment information:</p>
                            <div style="text-align: center;">
                                <a href="%s" class="button">Add Payment Method</a>
                            </div>
                            <p>This link will expire in 24 hours. If you have any questions, please contact us.</p>
                            <p>Best regards,<br>%s Team</p>
                        </div>
                        <div class="footer">
                            <p>This email was sent by %s. If you did not request this, please ignore this email.</p>
                        </div>
                    </div>
                </body>
                </html>
                """, userName, clubName, paymentLink, clubName, clubName);

        helper.setText(htmlContent, true);
        mailSender.send(message);
    }
}
