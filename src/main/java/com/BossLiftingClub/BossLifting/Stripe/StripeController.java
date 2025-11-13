package com.BossLiftingClub.BossLifting.Stripe;

import com.BossLiftingClub.BossLifting.Analytics.RecentActivity;
import com.BossLiftingClub.BossLifting.Analytics.RecentActivityRepository;
import com.BossLiftingClub.BossLifting.Club.ClubService;
import com.BossLiftingClub.BossLifting.Promo.PromoDTO;
import com.BossLiftingClub.BossLifting.Promo.PromoService;
import com.BossLiftingClub.BossLifting.Stripe.ProcessedEvent.EventService;
import com.BossLiftingClub.BossLifting.Stripe.RequestsAndResponses.*;
import com.BossLiftingClub.BossLifting.Stripe.Transfers.TransferService;
import com.BossLiftingClub.BossLifting.User.*;
import com.BossLiftingClub.BossLifting.User.Membership.Membership;
import com.BossLiftingClub.BossLifting.User.Membership.MembershipRepository;
import com.BossLiftingClub.BossLifting.User.UserTitles.UserTitles;
import com.BossLiftingClub.BossLifting.User.UserTitles.UserTitlesRepository;
import com.stripe.Stripe;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.exception.StripeException;
import com.stripe.model.*;
import com.stripe.model.checkout.Session;
import com.stripe.net.Webhook;
import com.stripe.param.*;
import com.stripe.param.checkout.SessionCreateParams;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@RestController
public class StripeController {
    private final StripeService stripeService;
    private final String webhookSecret;
    private final UserService userService;
    private final UserTitlesRepository userTitlesRepository;
    private final MembershipRepository membershipRepository;
    private final UserRepository userRepository;
    private final String webhookSubscriptionSecret;
    private final EventService eventService;
    private final TransferService transferService;
    private final PromoService promoService;
    private final RecentActivityRepository recentActivityRepository;
    private final ClubService clubService;
    @Autowired
    private JavaMailSender mailSender;
    public StripeController(EventService eventService, TransferService transferService, UserService userService, StripeService stripeService, @Value("${stripe.webhook.secret}") String webhookSecret, @Value("${stripe.webhook.subscriptionSecret}") String webhookSubscriptionSecret, UserTitlesRepository userTitlesRepository, MembershipRepository membershipRepository, UserRepository userRepository, PromoService promoService, RecentActivityRepository recentActivityRepository, ClubService clubService) {
        this.eventService = eventService;
        this.stripeService = stripeService;
        this.webhookSecret = webhookSecret;
        this.userService = userService;
        this.userTitlesRepository = userTitlesRepository;
        this.membershipRepository = membershipRepository;
        this.userRepository = userRepository;
        this.webhookSubscriptionSecret = webhookSubscriptionSecret;
        this.transferService = transferService;
        this.promoService = promoService;
        this.recentActivityRepository = recentActivityRepository;
        this.clubService = clubService;
    }

    public void sendOnboardingEmail(String customerId) throws StripeException, MessagingException {
        // Fetch customer details from Stripe
        Customer customer = Customer.retrieve(customerId);
        String email = customer.getEmail();
        String name = customer.getName() != null ? customer.getName() : "Member"; // Fallback to "Member" if name is null

        // Split name into first and last name (if possible)
        String firstName = name;
        String lastName = "";
        if (name != null && name.contains(" ")) {
            String[] nameParts = name.split(" ", 2);
            firstName = nameParts[0];
            lastName = nameParts.length > 1 ? nameParts[1] : "";
        }

        // Validate email
        if (email == null || email.isEmpty()) {
            System.err.println("No email found for customer ID: " + customerId);
            throw new IllegalArgumentException("No email found for customer ID: " + customerId);
        }

        String subject = "Welcome to CLT Lifting Club!";
        String message = String.format(
                "Hey %s %s,\n\n" +
                        "Welcome to the CLT Lifting Club! 💪\n\n" +
                        "You're all set to start tracking your progress and hitting your goals.\n\n" +
                        "👉 Download the app here: https://apps.apple.com/us/app/clt-lifting-club/id6744620860\n\n" +
                        "Log in using your phone number and password you signed up with.\n\n" +
                        "Let’s get stronger together!\n\n" +
                        "- The CLT Lifting Club Team",
                firstName, lastName
        );

        SimpleMailMessage emailMessage = new SimpleMailMessage();
        emailMessage.setTo(email);
        emailMessage.setSubject(subject);
        emailMessage.setText(message);

        mailSender.send(emailMessage);
        System.out.println("Onboarding email sent to: " + email);
    }

    @PostMapping("/webhook")
    public ResponseEntity<String> handleWebhook(
            @RequestBody String payload,
            @RequestHeader("Stripe-Signature") String sigHeader
    ) throws Exception {
        System.out.println(payload);
        try {
            Event event = Webhook.constructEvent(payload, sigHeader, webhookSecret);

            // Handle checkout.session.completed (successful payment method setup)
            if ("checkout.session.completed".equals(event.getType())) {
                Session session = (Session) event.getDataObjectDeserializer().getObject().orElse(null);
                if (session == null) {
                    return ResponseEntity.status(400).body("Invalid session data");
                }

                String customerId = session.getCustomer();
                String mode = session.getMode();

                if ("setup".equals(mode)) {
                    // Handle setup mode (from /signupWithCard)
                    String setupIntentId = session.getSetupIntent();
                    if (setupIntentId == null) {
                        return ResponseEntity.status(400).body("No setup intent found in session");
                    }

                    SetupIntent setupIntent = SetupIntent.retrieve(setupIntentId);
                    String paymentMethodId = setupIntent.getPaymentMethod();
                    if (paymentMethodId != null) {
                        // Payment method provided, attach it and proceed
                        stripeService.attachPaymentMethod(customerId, paymentMethodId);
                        System.out.println("Payment method " + paymentMethodId + " set as default for customer " + customerId);

                        // Create and save user
                        User user = createUserFromSession(session, customerId);
                        System.out.println("User created with ID: " + user.getId() + " for customer: " + customerId);

                        // Create subscriptions
                        createSubscriptions(customerId, paymentMethodId, user.getLockedInRate(), session.getMetadata().get("promoToken"));
                        sendOnboardingEmail(customerId);
                    } else {
                        // No payment method provided, delete the Stripe customer
                        System.out.println("No payment method attached in setup intent: " + setupIntentId);
                        stripeService.deleteCustomer(customerId);
                        System.out.println("Deleted Stripe customer " + customerId + " due to no payment method.");
                        return ResponseEntity.status(400).body("No payment method found in setup intent");
                    }
                }
            }

            // Handle checkout.session.expired (user canceled or session timed out)
            if ("checkout.session.expired".equals(event.getType())) {
                Session session = (Session) event.getDataObjectDeserializer().getObject().orElse(null);
                if (session == null) {
                    return ResponseEntity.status(400).body("Invalid session data");
                }

                String customerId = session.getCustomer();
                stripeService.deleteCustomer(customerId);
                System.out.println("Checkout session expired, deleted Stripe customer: " + customerId);
            }

            return ResponseEntity.ok("Webhook handled");
        } catch (StripeException e) {
            e.printStackTrace();
            System.out.println("Webhook error: " + e.getMessage());
            return ResponseEntity.status(500).body("Webhook error: " + e.getMessage());
        }
    }

    private User createUserFromSession(Session session, String customerId) throws Exception {
        Map<String, String> metadata = session.getMetadata();
        User user = new User();
        user.setFirstName(metadata.get("firstName"));
        user.setLastName(metadata.get("lastName"));
        user.setPhoneNumber(metadata.get("phoneNumber"));
        user.setPassword(metadata.get("password"));
        user.setLockedInRate(metadata.get("lockedInRate"));

        if (metadata.get("referredUserId") != null) {
            User referrer = userRepository.findById(Long.valueOf(metadata.get("referredUserId")))
                    .orElseThrow(() -> new RuntimeException("Referred User not found in database"));
            user.setReferredBy(referrer);

            // Create and apply discount coupon for referrer
            try {
                String referrerStripeId = referrer.getUserStripeMemberId();
                if (referrerStripeId != null) {
                    SubscriptionListParams subscriptionParams = SubscriptionListParams.builder()
                            .setCustomer(referrerStripeId)
                            .build();
                    SubscriptionCollection subscriptions = Subscription.list(subscriptionParams);

                    if (subscriptions.getData().isEmpty()) {
                        System.err.println("No subscriptions found for referrer with Stripe ID: " + referrerStripeId);
                    } else {
                        Subscription referrerSubscription = subscriptions.getData()
                                .stream()
                                .filter(sub -> sub.getItems().getData().stream()
                                        .anyMatch(item -> {
                                            String priceId = item.getPrice().getId();
                                            return !"price_1RF30SGHcVHSTvgIpegCzQ0m".equals(priceId);
                                        }))
                                .findFirst()
                                .orElse(null);

                        if (referrerSubscription == null) {
                            System.err.println("No matching subscription found for referrer with Stripe ID: " + referrerStripeId);
                        } else {
                            if (referrerSubscription.getDiscount() != null && referrerSubscription.getDiscount().getCoupon() != null) {
                                Coupon existingCoupon = referrerSubscription.getDiscount().getCoupon();
                                Long newDurationInMonths = 1L;
                                if (existingCoupon.getDurationInMonths() != null) {
                                    newDurationInMonths = existingCoupon.getDurationInMonths() + 1;
                                }

                                CouponCreateParams couponParams = CouponCreateParams.builder()
                                        .setPercentOff(BigDecimal.valueOf(100.0))
                                        .setDuration(CouponCreateParams.Duration.REPEATING)
                                        .setDurationInMonths(newDurationInMonths)
                                        .setName("Extended Referral Discount")
                                        .build();
                                Coupon newCoupon = Coupon.create(couponParams);

                                SubscriptionUpdateParams updateParams = SubscriptionUpdateParams.builder()
                                        .setCoupon(newCoupon.getId())
                                        .build();
                                Subscription updatedSubscription = referrerSubscription.update(updateParams);
                                System.out.println("Extended coupon applied to referrer's subscription: " + updatedSubscription.getId());
                            } else {
                                CouponCreateParams couponParams = CouponCreateParams.builder()
                                        .setPercentOff(BigDecimal.valueOf(100.0))
                                        .setDuration(CouponCreateParams.Duration.REPEATING)
                                        .setDurationInMonths(1L)
                                        .setName("Referral Discount")
                                        .build();
                                Coupon coupon = Coupon.create(couponParams);

                                SubscriptionUpdateParams updateParams = SubscriptionUpdateParams.builder()
                                        .setCoupon(coupon.getId())
                                        .build();
                                Subscription updatedSubscription = referrerSubscription.update(updateParams);
                                System.out.println("Coupon applied to referrer's subscription: " + updatedSubscription.getId());
                            }
                        }
                    }
                } else {
                    System.err.println("Referrer has no Stripe ID associated.");
                }
            } catch (StripeException e) {
                System.err.println("Error applying referral coupon: " + e.getMessage());
            }
        }

        user.setIsInGoodStanding(false);
        UserTitles foundingUserTitle = userTitlesRepository.findByTitle(metadata.get("userTitle"))
                .orElseThrow(() -> new RuntimeException("Founding user title not found in database"));
//        Membership membership = membershipRepository.findByTitle(metadata.get("membership"))
//                .orElseThrow(() -> new RuntimeException("Membership not found in database"));
//        user.setMembership(membership);
        user.setUserTitles(foundingUserTitle);
        user.setUserStripeMemberId(customerId);
        System.out.println("userLog: " + user);
        System.out.println("getReferredMembersDto: " + user.getReferredMembersDto());
        userService.save(user);

        String promoToken = metadata.get("promoToken");
        if (promoToken != null) {
            promoService.addUserToPromo(promoToken, user.getId());
        } else {
            System.out.println("No promo token provided in metadata.");
        }

        return user;
    }
    @PostMapping("/signupWithCard")
    public ResponseEntity<Map<String, String>> signupWithCard(@RequestBody UserRequest userRequest) {
        try {
            // Step 1: Check if phone number already exists
            Optional<User> existingUser = userService.getUserByPhoneNumber(userRequest.getPhoneNumber());
            if (existingUser.isPresent()) {
                System.out.println("Existing user found: " + existingUser.get());
                Map<String, String> errorResponse = new HashMap<>();
                errorResponse.put("error", "PhoneNumber already in use");
                return ResponseEntity.badRequest().body(errorResponse); // 400 Bad Request
            }

            // Step 2: Fetch the "founding_user" title and membership from the database
            UserTitles foundingUserTitle = userTitlesRepository.findByTitle("Founding User")
                    .orElseThrow(() -> new RuntimeException("Founding user title not found in database"));
//            Membership membership = membershipRepository.findByTitle(userRequest.getMembershipName())
//                    .orElseThrow(() -> new RuntimeException("Membership not found in database"));

            // Step 3: Create Stripe customer (we'll clean up in webhook if no payment info)
            String customerId = stripeService.createCustomer(
                    null, // Email optional
                    userRequest.getFirstName() + " " + userRequest.getLastName(),
                    null  // No payment method yet
            );

            // Step 4: Store user data in metadata
            Map<String, String> metadata = new HashMap<>();
            metadata.put("firstName", userRequest.getFirstName());
            metadata.put("lastName", userRequest.getLastName());
            metadata.put("phoneNumber", userRequest.getPhoneNumber());
            metadata.put("password", userRequest.getPassword()); // Consider hashing if sensitive
            metadata.put("userTitle", foundingUserTitle.getTitle());
//            metadata.put("membership", membership.getTitle());
            metadata.put("lockedInRate",userRequest.getLockedInRate());
            if (userRequest.getReferralId() != null) {
                String referredId = userRequest.getReferralId().toString();
                metadata.put("referredUserId", referredId);
            }
            System.out.println(userRequest.getPromoToken()+ "ourside");
            if (userRequest.getPromoToken() != null) {
                System.out.println(userRequest.getPromoToken() +"inside");
                metadata.put("promoToken", userRequest.getPromoToken());
            }

            // Step 5: Create Checkout session in setup mode with metadata
            String sessionId = stripeService.createSetupCheckoutSessionWithMetadata(
                    customerId,
                    "https://www.cltliftingclub.com/success",
                    "https://www.cltliftingclub.com/cancel",
                    metadata
            );

            // Step 6: Return session ID to frontend
            Map<String, String> response = new HashMap<>();
            response.put("sessionId", sessionId);
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(500).body(Map.of("error", "Failed: " + e.getMessage()));
        }
    }


    @PostMapping("/StripeSubscriptionHandler")
    public ResponseEntity<String> handleStripeWebhook(HttpServletRequest request,
                                                      @RequestHeader("Stripe-Signature") String sigHeader) {
        try {
            // Read the raw body as bytes
            String payload = new String(request.getInputStream().readAllBytes(), StandardCharsets.UTF_8);

            // Verify webhook signature
            Event event = Webhook.constructEvent(payload, sigHeader, webhookSubscriptionSecret);

            // Deserialize the event data object
            EventDataObjectDeserializer dataObjectDeserializer = event.getDataObjectDeserializer();
            if (!dataObjectDeserializer.getObject().isPresent()) {
                return new ResponseEntity<>("Invalid event data", HttpStatus.BAD_REQUEST);
            }

            String eventId = event.getId();
            String eventType = event.getType();

            // Check if event was already processed
            if (eventService.isEventProcessed(eventId)) {
                System.out.println("Event " + eventId + " already processed, skipping.");
                return new ResponseEntity<>("Event already processed", HttpStatus.OK);
            }

            // Handle invoice.paid (transfer 4% fee to Connected Account)
//            if ("invoice.paid".equals(eventType)) {
//                Invoice invoice = (Invoice) dataObjectDeserializer.getObject().orElse(null);
//                if (invoice != null && invoice.getSubscription() != null && invoice.getCharge() != null) {                    String chargeId = invoice.getCharge();
//                    // Check if a transfer already exists for this charge
//                    if (transferService.hasProcessedCharge(chargeId)) {
//                        System.out.println("Transfer already exists for charge " + chargeId + ", skipping.");
//                        return new ResponseEntity<>("Webhook processed", HttpStatus.OK);
//                    }
//
//                    Charge charge = Charge.retrieve(chargeId);
//                    if (charge != null && "succeeded".equals(charge.getStatus())) {
//                        Subscription subscription = Subscription.retrieve(invoice.getSubscription());
//                        long feeCents = calculateFeeCents(subscription);
//                        if (feeCents > 0) {
//                            try {
//                                // Check available balance before transfer
//                                Balance balance = Balance.retrieve();
//                                long availableBalance = balance.getAvailable().stream()
//                                        .filter(b -> "usd".equals(b.getCurrency()))
//                                        .mapToLong(Balance.Available::getAmount)
//                                        .sum();
//                                if (availableBalance < feeCents) {
//                                    System.err.println("Insufficient balance: " + (availableBalance / 100.0) + " USD, needed: " + (feeCents / 100.0));
//                                    return new ResponseEntity<>("Insufficient balance", HttpStatus.INTERNAL_SERVER_ERROR);
//                                }
//
//                                TransferCreateParams transferParams = TransferCreateParams.builder()
//                                        .setAmount(feeCents)
//                                        .setCurrency("usd")
//                                        .setDestination("acct_1RDvRj4gikNsBARu")
//                                        .setSourceTransaction(chargeId)
//                                        .build();
//                                Transfer transfer = Transfer.create(transferParams);
//                                System.out.println("Transferred " + (feeCents / 100.0) + " to Connected Account for invoice " + invoice.getId());
//                                // Store transfer record
//                                transferService.saveTransfer(chargeId, invoice.getId(), transfer.getId());
//                            } catch (StripeException e) {
//                                System.err.println("Transfer failed for invoice " + invoice.getId() + ": " + e.getMessage());
//                                return new ResponseEntity<>("Transfer error: " + e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
//                            }
//                        }
//                        // Update user status
//                        userService.updateUserAfterPayment(invoice.getCustomer(), true);
//                    } else {
//                        System.out.println("Charge was not successful. Skipping fee transfer for invoice " + invoice.getId());
//                    }
//                }
//            }


            // Handle other event types
            switch (eventType) {
                case "customer.subscription.created":
                    Subscription newSubscription = (Subscription) dataObjectDeserializer.getObject().get();
                    String newCustomerId = newSubscription.getCustomer();
                    String newStatus = newSubscription.getStatus();
                    boolean isInGoodStanding = "active".equals(newStatus) || "trialing".equals(newStatus);
                    userService.updateUserAfterPayment(newCustomerId, isInGoodStanding);

                    // Create NEW_MEMBER recent activity
                    try {
                        // Check if already processed
                        if (!recentActivityRepository.findByStripeEventId(eventId).isPresent()) {
                            Customer newCustomer = Customer.retrieve(newCustomerId);
                            String customerName = newCustomer.getName() != null ? newCustomer.getName() : "New Member";

                            // TODO: Determine clubId based on your business logic
                            // For now, using a placeholder. You may need to:
                            // 1. Add clubId to subscription metadata
                            // 2. Look up user by customerId and find their club association
                            // 3. Use a default club for your single-club setup
                            Long clubId = 1L; // PLACEHOLDER - Replace with actual club lookup logic

                            RecentActivity activity = new RecentActivity();
                            activity.setClubId(clubId);
                            activity.setActivityType("NEW_MEMBER");
                            activity.setDescription(customerName + " joined the club");
                            activity.setCustomerName(customerName);
                            activity.setStripeEventId(eventId);
                            recentActivityRepository.save(activity);

                            System.out.println("Created NEW_MEMBER activity for customer: " + customerName);
                        }
                    } catch (Exception e) {
                        System.err.println("Failed to create NEW_MEMBER activity: " + e.getMessage());
                    }
                    break;

                case "customer.subscription.updated":
                    Subscription subscription = (Subscription) dataObjectDeserializer.getObject().get();
                    String customerId = subscription.getCustomer();
                    String status = subscription.getStatus();
                    boolean isInGoodStandingUpdated = "active".equals(status) || "trialing".equals(status);
                    userService.updateUserAfterPayment(customerId, isInGoodStandingUpdated);
                    break;

                case "charge.succeeded":
                    Charge charge = (Charge) dataObjectDeserializer.getObject().get();

                    // Create PAYMENT recent activity
                    try {
                        // Check if already processed
                        if (!recentActivityRepository.findByStripeEventId(eventId).isPresent()) {
                            String chargeCustomerId = charge.getCustomer();
                            double amount = charge.getAmount() / 100.0;

                            Customer chargeCustomer = Customer.retrieve(chargeCustomerId);
                            String payerName = chargeCustomer.getName() != null ? chargeCustomer.getName() : "Customer";

                            // TODO: Determine clubId based on your business logic
                            // For connected accounts, you may be able to extract it from:
                            // 1. request.getHeader("Stripe-Account")
                            // 2. charge.getMetadata().get("clubId")
                            // 3. Look up client by stripeAccountId and get associated club
                            Long clubId = 1L; // PLACEHOLDER - Replace with actual club lookup logic

                            RecentActivity activity = new RecentActivity();
                            activity.setClubId(clubId);
                            activity.setActivityType("PAYMENT");
                            activity.setDescription("Payment received from " + payerName);
                            activity.setAmount(amount);
                            activity.setCustomerName(payerName);
                            activity.setStripeEventId(eventId);
                            recentActivityRepository.save(activity);

                            System.out.println("Created PAYMENT activity: $" + amount + " from " + payerName);
                        }
                    } catch (Exception e) {
                        System.err.println("Failed to create PAYMENT activity: " + e.getMessage());
                    }
                    break;

                case "invoice.payment_failed":
                    Invoice failedInvoice = (Invoice) dataObjectDeserializer.getObject().get();
                    if (failedInvoice != null && failedInvoice.getSubscription() != null) {
                        userService.updateUserAfterPayment(failedInvoice.getCustomer(), false);
                    }
                    break;

                case "customer.subscription.deleted":
                    Subscription deletedSubscription = (Subscription) dataObjectDeserializer.getObject().get();
                    userService.updateUserAfterPayment(deletedSubscription.getCustomer(), false);
                    break;

                case "account.updated":
                    Account account = (Account) dataObjectDeserializer.getObject().get();
                    String accountId = account.getId();

                    // Check if the account has completed onboarding
                    boolean chargesEnabled = account.getChargesEnabled();
                    boolean detailsSubmitted = account.getDetailsSubmitted();

                    String onboardingStatus;
                    if (chargesEnabled && detailsSubmitted) {
                        onboardingStatus = "COMPLETED";
                        System.out.println("Stripe account " + accountId + " completed onboarding");
                    } else if (detailsSubmitted) {
                        onboardingStatus = "PENDING";
                        System.out.println("Stripe account " + accountId + " details submitted, pending review");
                    } else if (account.getRequirements() != null && !account.getRequirements().getCurrentlyDue().isEmpty()) {
                        onboardingStatus = "RESTRICTED";
                        System.out.println("Stripe account " + accountId + " has requirements currently due");
                    } else {
                        onboardingStatus = "PENDING";
                        System.out.println("Stripe account " + accountId + " is pending onboarding");
                    }

                    try {
                        clubService.updateOnboardingStatus(accountId, onboardingStatus);
                        System.out.println("Updated club onboarding status for account " + accountId + " to " + onboardingStatus);
                    } catch (Exception e) {
                        System.err.println("Failed to update onboarding status for account " + accountId + ": " + e.getMessage());
                    }
                    break;

                default:
                    System.out.println("Unhandled event type: " + eventType + ", ID: " + eventId);
            }

            // Mark event as processed
            eventService.markEventProcessed(eventId);
            return new ResponseEntity<>("Webhook processed", HttpStatus.OK);
        } catch (SignatureVerificationException e) {
            return new ResponseEntity<>("Invalid signature", HttpStatus.BAD_REQUEST);
        } catch (StripeException e) {
            System.err.println("Stripe API error: " + e.getMessage());
            return new ResponseEntity<>("Stripe error: " + e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
        } catch (Exception e) {
            System.err.println("Unexpected error: " + e.getMessage());
            return new ResponseEntity<>("Webhook error: " + e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }


    private void createSubscriptions(String customerId, String paymentMethodId, String membershipPrice, String PromoToken) throws Exception {
        // Validate customer and payment method
        Customer customer = Customer.retrieve(customerId);
        if (customer.getDeleted() != null && customer.getDeleted()) {
            throw new RuntimeException("Customer is deleted: " + customerId);
        }
        if (paymentMethodId == null || !PaymentMethod.retrieve(paymentMethodId).getCustomer().equals(customerId)) {
            throw new RuntimeException("Invalid payment method for customer: " + customerId);
        }
        System.out.println("Customer " + customerId + " has valid payment method: " + paymentMethodId);

        // Determine billing cycle based on current date
        LocalDate currentDate = LocalDate.now(ZoneId.of("UTC"));
        System.out.println("Setting main subscription billing cycle to current date: " + currentDate);

        // Map membershipPrice to Price IDs
        String mainPriceId;
        String mainFeePriceId;
        switch (membershipPrice) {
            case "89.99":
                mainPriceId = "price_1R6aIfGHcVHSTvgIlwN3wmyD";
                mainFeePriceId = "price_1RF4FpGHcVHSTvgIKM8Jilwl"; // $3.60
                break;
            case "99.99":
                mainPriceId = "price_1RF313GHcVHSTvgI4HXgjwOA";
                mainFeePriceId = "price_1RF4GlGHcVHSTvgIVojlVrn7"; // $4.00
                break;
            case "109.99":
                mainPriceId = "price_1RF31hGHcVHSTvgIbTnGo4vT";
                mainFeePriceId = "price_1RF4IsGHcVHSTvgIYOoYfxb9"; // $4.40
                break;
            case "948.00":
                mainPriceId = "price_1RJJuTGHcVHSTvgI2pVN6hfx";
                break;
            default:
                throw new IllegalArgumentException("Invalid membership price: " + membershipPrice);
        }

        // Application fee Price ID (one-time)
        String applicationFeePriceId = "price_1RJOFhGHcVHSTvgI08VPh4XY"; // One-time application fee Price ID

        // Step 1: Create a one-time InvoiceItem for the application fee


        processApplicationFee(customerId, applicationFeePriceId, PromoToken);
        // Step 3: Create the subscription (without the application fee)
        SubscriptionCreateParams.Builder subscriptionBuilder = SubscriptionCreateParams.builder()
                .setCustomer(customerId)
                .addItem(SubscriptionCreateParams.Item.builder()
                        .setPrice(mainPriceId)
                        .build())
                .setDefaultPaymentMethod(paymentMethodId);

        // Check if the membership price is not 948 before adding the tax rate
        if (!membershipPrice.equals("948.00")) {
            subscriptionBuilder.addDefaultTaxRate("txr_1RIsQGGHcVHSTvgIF3A1Nacp");
        }
        SubscriptionCreateParams subscriptionParams = subscriptionBuilder.build();
        Subscription subscription = Subscription.create(subscriptionParams);

        System.out.println("Subscription created: " + subscription.getId());

        LocalDate currentDateMaintanance = LocalDate.now();
        // Calculate the billing anchor 6 months in the future
        LocalDate billingAnchorDate = currentDateMaintanance.plusMonths(6);
        ZonedDateTime billingAnchorDateTime = billingAnchorDate.atStartOfDay(ZoneId.of("UTC"));
        long billingAnchorTimestamp = billingAnchorDateTime.toEpochSecond();

        // Validate billing anchor is in the future
        long currentTimestamp = Instant.now().getEpochSecond();
        if (billingAnchorTimestamp <= currentTimestamp) {
            throw new IllegalArgumentException("Billing anchor timestamp must be in the future: " + billingAnchorDate);
        }

        // Create maintenance subscription with delayed billing cycle
        SubscriptionCreateParams.Builder maintenanceParamsBuilder = SubscriptionCreateParams.builder()
                .setCustomer(customerId)
                .addItem(SubscriptionCreateParams.Item.builder()
                        .setPrice("price_1RF30SGHcVHSTvgIpegCzQ0m") // $59.99
                        .build())
                .setDefaultPaymentMethod(paymentMethodId)
                .setProrationBehavior(SubscriptionCreateParams.ProrationBehavior.NONE)
                .addExpand("schedule")
                .setBillingCycleAnchor(billingAnchorTimestamp);

        System.out.println("Setting maintenance subscription with first charge on " + billingAnchorDate);

        try {
            Subscription maintenanceSubscription = Subscription.create(maintenanceParamsBuilder.build());
            System.out.println("Maintenance subscription created with ID: " + maintenanceSubscription.getId());
        } catch (StripeException e) {
            System.err.println("Failed to create maintenance subscription: " + e.getMessage() + "; request-id: " + e.getRequestId());
            throw e;
        }
    }
    public void processApplicationFee(String customerId, String applicationFeePriceId, String promoToken) throws Exception {
        // Fetch all promos
        List<PromoDTO> promos = promoService.findAll();

        // Check if promoToken matches any promo's codeToken (case-insensitive)
        boolean isValidPromo = promoToken != null && promos.stream()
                .anyMatch(promo -> promo.getCodeToken() != null &&
                        promo.getCodeToken().toUpperCase(Locale.ROOT).equals(promoToken.toUpperCase(Locale.ROOT)));

        // If no valid promo is found, create the invoice item and invoice
        if (!isValidPromo) {
            InvoiceItemCreateParams invoiceItemParams = InvoiceItemCreateParams.builder()
                    .setCustomer(customerId)
                    .setPrice(applicationFeePriceId)
                    .setQuantity(1L)
                    .build();

            InvoiceItem invoiceItem = InvoiceItem.create(invoiceItemParams);

            Invoice invoice = Invoice.create(
                    InvoiceCreateParams.builder()
                            .setCustomer(customerId)
                            .setCollectionMethod(InvoiceCreateParams.CollectionMethod.CHARGE_AUTOMATICALLY)
                            .build()
            );
            invoice.finalizeInvoice();
        }
    }



    @PostMapping("/{userId}/sendPasswordEmail")
    public ResponseEntity<String> sendPasswordResetEmail(@PathVariable Integer userId, @RequestParam String cusId) {
        // Validate cusId format
        if (cusId == null || !cusId.startsWith("cus_")) {
            return ResponseEntity.badRequest().body("Invalid customer ID format");
        }

        try {


            // Fetch customer email from Stripe
            Customer customer = Customer.retrieve(cusId);
            String toEmail = customer.getEmail();
            if (toEmail == null || toEmail.isEmpty()) {
                return ResponseEntity.badRequest().body("No email found for customer ID: " + cusId);
            }

            // Prepare and send email
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true);

            // Hardcoded password reset link with userId
            String resetLink = "https://www.cltliftingclub.com/reset-password?id=" + userId;

            // Professional email content for Clt Lifting
            String subject = "Password Reset Request for Clt Lifting";
            String htmlContent = """
                <html>
                <body style="font-family: Arial, sans-serif; line-height: 1.6; color: #333;">
                    <h2>Password Reset Request</h2>
                    <p>Dear Member,</p>
                    <p>We received a request to reset your password for your CLT Lifting Club LLC account. Click the link below to reset your password:</p>
                    <p><a href="%s" style="background-color: #28a745; color: #fff; padding: 10px 20px; text-decoration: none; border-radius: 5px;">Reset Your Password</a></p>
                    <p>If you did not request a password reset, please ignore this email or contact our support team at support@cltlifting.com.</p>
                    <p>Thank you for choosing CLT Lifting Club LLC!</p>
                    <p>Best regards,<br>The CLT Lifting Team</p>
                    <hr>
                    <p style="font-size: 12px; color: #777;">CLT Lifting Club LLC, 3100 South Blvd, Charlotte, NC, USA</p>
                </body>
                </html>
                """.formatted(resetLink);

            helper.setTo(toEmail);
            helper.setSubject(subject);
            helper.setText(htmlContent, true);
            helper.setFrom("CLT Lifting Club <contact@cltliftingclub.com>");

            mailSender.send(message);

            return ResponseEntity.ok("Password reset email sent to " + toEmail);
        } catch (StripeException e) {
            return ResponseEntity.status(400).body("Stripe error: " + e.getMessage());
        } catch (MessagingException e) {
            return ResponseEntity.status(500).body("Failed to send email: " + e.getMessage());
        }
    }
    @PostMapping("/{userId}/sendAndroidEmail")
    public ResponseEntity<String> sendTestEmail(@PathVariable Long userId) {
        // Fetch user from database
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found with ID: " + userId));

        // Validate stripeCusId format
        String stripeCusId = user.getUserStripeMemberId();
        if (stripeCusId == null || !stripeCusId.startsWith("cus_")) {
            return ResponseEntity.badRequest().body("Invalid customer ID format");
        }

        // Fetch customer email from Stripe
        String toEmail;
        try {
            Customer customer = Customer.retrieve(stripeCusId);
            toEmail = customer.getEmail();
            if (toEmail == null || toEmail.isEmpty()) {
                return ResponseEntity.badRequest().body("No email found for customer ID: " + stripeCusId);
            }
        } catch (StripeException e) {
            return ResponseEntity.status(400).body("Stripe error: " + e.getMessage());
        }

        // Prepare and send email
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true);

            // Testing link and feedback form link (replace with actual URLs)
            String testingLink = "https://play.google.com/store/apps/details?id=com.adickinson.CltLiftingClub";
            String internalTestingLink = "https://play.google.com/apps/internaltest/4700609801587188644";

            String contact = "contact@cltliftingclub.com";

            // Professional HTML email content for CLT Lifting Club
            String htmlContent = """
                <html>
                <body style="font-family: Arial, sans-serif; line-height: 1.6; color: #333;">
                    <h2>Test the CLT Lifting Club App</h2>
                    <p>Dear Member,</p>
                    <p>You’re invited to test the CLT Lifting Club app, launching on the Play Store soon!</p>
                    <p><a href="%s" style="background-color: #28a745; color: #fff; padding: 10px 20px; text-decoration: none; border-radius: 5px;">Join the Test Now</a></p>
                    <p><a href="%s" style="background-color: #28a745; color: #fff; padding: 10px 20px; text-decoration: none; border-radius: 5px;">Join the Internal Test Now</a></p>
                    <p>Steps:<br>
                    1. Click the link above.<br>
                    2. Install the app from the Play Store (no “Unknown Sources” needed).<br>
                    3. Log in with your membership credentials (contact %s if unsure).<br>
                    4. Requires Android 8.0+.<br>
                    <p>Thank you for choosing CLT Lifting Club LLC!</p>
                    <p>Best regards,<br>The CLT Lifting Team</p>
                    <hr>
                    <p style="font-size: 12px; color: #777;">CLT Lifting Club LLC, 3100 South Blvd, Charlotte, NC, USA</p>
                </body>
                </html>
                """.formatted(testingLink, internalTestingLink, contact);

            helper.setTo(toEmail);
            helper.setSubject("Test the CLT Lifting Club App for Play Store Launch – Join Now!");
            helper.setText(htmlContent, true);
            helper.setFrom("CLT Lifting Club <contact@cltliftingclub.com>");

            mailSender.send(message);

            return ResponseEntity.ok("Test email sent to " + toEmail);
        } catch (MessagingException e) {
            return ResponseEntity.status(500).body("Failed to send email: " + e.getMessage());
        }
    }
    @PostMapping("/update-payment-method")
    public Map<String, Object> updatePaymentMethod(@RequestBody Map<String, String> request) {
        Map<String, Object> response = new HashMap<>();
        try {
            String customerId = request.get("customerId");
            String paymentMethodId = request.get("paymentMethodId");

            // Attach payment method to customer
            PaymentMethod paymentMethod = PaymentMethod.retrieve(paymentMethodId);
            paymentMethod.attach(new HashMap<String, Object>() {{
                put("customer", customerId);
            }});

            // Set as default payment method
            Customer customer = Customer.retrieve(customerId);
            customer.update(new HashMap<String, Object>() {{
                put("invoice_settings", new HashMap<String, Object>() {{
                    put("default_payment_method", paymentMethodId);
                }});
            }});

            response.put("success", true);
        } catch (StripeException e) {
            response.put("error", e.getMessage());
        }
        return response;
    }
    @PostMapping("/create-setup-intent")
    public Map<String, String> createSetupIntent(@RequestBody Map<String, String> request) throws StripeException {
        String customerId = request.get("customerId");
        SetupIntent setupIntent = SetupIntent.create(
                new HashMap<String, Object>() {{
                    put("customer", customerId);
                    put("payment_method_types", new String[]{"card"});
                }}
        );
        return new HashMap<String, String>() {{
            put("clientSecret", setupIntent.getClientSecret());
        }};
    }


    @PostMapping("/{StripeCusId}/add-child")
    public ResponseEntity<UserDTO> addChildToParent(@PathVariable String StripeCusId, @RequestBody User childUser) {
        Optional<User> parentlUser = userRepository.findByUserStripeMemberId(StripeCusId);
        UserDTO updatedChild = userService.addChildToParent(parentlUser.get().getId(), childUser);
        String newPriceId = "price_1RjLIeGHcVHSTvgIIWXgBxPK";
        String targetPriceId = "price_1RF30SGHcVHSTvgIpegCzQ0m";

        // Create Stripe subscription using parent's customer ID
        if (parentlUser.get().getUserStripeMemberId() != null) {
            try {
                // Retrieve the parent's subscriptions
                SubscriptionListParams listParams = SubscriptionListParams.builder()
                        .setCustomer(StripeCusId)
                        .build();
                StripeCollection<Subscription> subscriptions = Subscription.list(listParams);

                // Loop through subscriptions to find the one with the target price ID
                Long billingCycleAnchor = null;
                for (Subscription subscription : subscriptions.getData()) {
                    for (SubscriptionItem item : subscription.getItems().getData()) {
                        if (!targetPriceId.equals(item.getPrice().getId())) {
                            billingCycleAnchor = subscription.getCurrentPeriodEnd();
                            break;
                        }
                    }
                    if (billingCycleAnchor != null) {
                        break;
                    }
                }

                // Check if the target subscription was found
                if (billingCycleAnchor == null) {
                    throw new RuntimeException("No subscription found with price ID: " + targetPriceId);
                }

                // Create new subscription with aligned billing cycle anchor and trial
                Subscription subscription = Subscription.create(
                        SubscriptionCreateParams.builder()
                                .setCustomer(StripeCusId)
                                .addItem(
                                        SubscriptionCreateParams.Item.builder()
                                                .setPrice(newPriceId)
                                                .build()
                                )
                                .setBillingCycleAnchor(billingCycleAnchor)
                                .setTrialEnd(billingCycleAnchor) // No payment until anchor date
                                // Proration is allowed by default for anchored invoice
                                .build()
                );

                // Optionally store subscription ID in the child user if needed
                // childUser.setUserStripeMemberId(subscription.getId());
                // userRepository.save(childUser);
            } catch (StripeException e) {
                throw new RuntimeException("Failed to create Stripe subscription: " + e.getMessage(), e);
            }
        } else {
            throw new IllegalArgumentException("Parent must have a Stripe customer ID.");
        }
        return ResponseEntity.ok(updatedChild);
    }

    @PostMapping("/{userId}/sendFamilyInviteEmail")
    public ResponseEntity<String> sendFamilyPlanEmail(@PathVariable String userId, @RequestParam String newCusEmail) {


        try {


            // Prepare and send email
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true);

            // Hardcoded password reset link with userId
            String inviteLink = "https://www.cltliftingclub.com/signup?contract=Family&userId=" + userId;

            // Professional email content for Clt Lifting
            String subject = "Add to Family plan";
            String htmlContent = """
<body style="font-family: Arial, sans-serif; line-height: 1.6; color: #333;">
    <h2>You’ve Been Invited to Join a Family Plan</h2>
    <p>Dear Member,</p>
    <p>You’ve been invited to join a family plan for your CLT Lifting Club LLC account. To accept the invitation and activate your membership, click the link below:</p>
    <p><a href="%s" style="background-color: #007bff; color: #fff; padding: 10px 20px; text-decoration: none; border-radius: 5px;">Join the Family Plan</a></p>
    <p>If you were not expecting this invitation or believe it was sent in error, please ignore this email or contact our support team at support@cltlifting.com.</p>
    <p>We’re excited to have you as part of the CLT Lifting Club family!</p>
    <p>Best regards,<br>The CLT Lifting Team</p>
    <hr>
    <p style="font-size: 12px; color: #777;">CLT Lifting Club LLC, 3100 South Blvd, Charlotte, NC, USA</p>
</body>
</html>
""".formatted(inviteLink);

            helper.setTo(newCusEmail);
            helper.setSubject(subject);
            helper.setText(htmlContent, true);
            helper.setFrom("CLT Lifting Club <contact@cltliftingclub.com>");

            mailSender.send(message);

            return ResponseEntity.ok("Password reset email sent to " + newCusEmail);

        } catch (MessagingException e) {
            return ResponseEntity.status(500).body("Failed to send email: " + e.getMessage());
        }
    }


    @PostMapping("/cancel-subscriptions/{customerId}")
    public String cancelSubscriptions(@PathVariable String customerId) throws StripeException, MessagingException {
        // Retrieve customer details from Stripe
        Customer customer = Customer.retrieve(customerId);
        String customerEmail = customer.getEmail();
        String customerName = customer.getName() != null ? customer.getName() : "Customer";

        // List all active subscriptions for the customer
        Map<String, Object> listParams = new HashMap<>();
        listParams.put("customer", customerId);
        listParams.put("status", "active");

        SubscriptionCollection subscriptions = Subscription.list(listParams);

        if (subscriptions.getData().isEmpty()) {
            return "No active subscriptions found for customer: " + customerId;
        }

        StringBuilder result = new StringBuilder("Canceled subscriptions for customer " + customerId + ":\n");

        // Cancel subscriptions based on price ID
        for (Subscription sub : subscriptions.getData()) {
            boolean hasTargetPrice = false;
            for (com.stripe.model.SubscriptionItem item : sub.getItems().getData()) {
                if ("price_1RF30SGHcVHSTvgIpegCzQ0m".equals(item.getPrice().getId())) {
                    hasTargetPrice = true;
                }
            }

            Long cancelAt;
            if (hasTargetPrice) {
                // Cancel at the end of the current billing period (before upcoming renewal)
                cancelAt = sub.getCurrentPeriodEnd();
            } else {
                // Cancel at the end of the upcoming billing period
                Map<String, Object> invoiceParams = new HashMap<>();
                invoiceParams.put("subscription", sub.getId());

                Invoice upcoming = null;
                try {
                    upcoming = Invoice.upcoming(invoiceParams);
                } catch (StripeException e) {
                    // Handle exception appropriately
                    continue;
                }
                cancelAt = upcoming.getLines().getData().get(0).getPeriod().getEnd();
            }

            Map<String, Object> updateParams = new HashMap<>();
            updateParams.put("cancel_at", cancelAt);

            Subscription updatedSub = null;
            try {
                updatedSub = sub.update(updateParams);
            } catch (StripeException e) {
                // Handle exception appropriately
                continue;
            }

            result.append("Subscription ID: ").append(updatedSub.getId())
                    .append(" will cancel at: ").append(updatedSub.getCancelAt())
                    .append("\n");
        }

        if (result.toString().equals("Canceled subscriptions for customer " + customerId + ":\n")) {
            return "No subscriptions were updated for customer: " + customerId;
        }

        // Send email notification to the customer
        sendCancellationEmail(customerEmail, customerName, subscriptions);

        return result.toString();
    }

    private void sendCancellationEmail(String toEmail, String customerName, SubscriptionCollection subscriptions) throws MessagingException {
        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true);

        helper.setFrom("CLT Lifting Club <contact@cltliftingclub.com>");
        helper.setTo(toEmail);
        helper.setSubject("Sorry to See You Go - Why’d You Leave?");

        // Build email body
        StringBuilder emailBody = new StringBuilder();
        emailBody.append("<p>Hi ").append(customerName).append(",</p>")
                .append("<p>Sorry to hear you’re leaving CLT Lifting Club. As per our cancellation policy, your membership(s) will be canceled at the end of the current or upcoming billing period, as listed below. You’ll continue to have access to your membership benefits until the cancellation date(s).</p>")
                .append("<ul>");

        for (Subscription sub : subscriptions.getData()) {
            boolean hasTargetPrice = false;
            for (com.stripe.model.SubscriptionItem item : sub.getItems().getData()) {
                if ("price_1RF30SGHcVHSTvgIpegCzQ0m".equals(item.getPrice().getId())) {
                    hasTargetPrice = true;
                    break;
                }
            }

            if (!hasTargetPrice) {
                emailBody.append("<li>Subscription ID: ").append(sub.getId())
                        .append(" (Cancellation Date: ").append(new java.util.Date(sub.getCancelAt() * 1000))
                        .append(")</li>");
            }
        }

        emailBody.append("</ul>")
                .append("<p>Do you mind telling us why you’re stepping away? Feel free to come back for a free day pass and feel free to bring a friend.</p>")
                .append("<p>Best,<br>The CLT Lifting Club Team</p>");

        helper.setText(emailBody.toString(), true); // true indicates HTML content
        mailSender.send(message);
    }

    private static final String NEW_CONTACT_EMAIL = "contact@cltliftingclub.com";
    @PostMapping("/send-emails")
    public ResponseEntity<Map<String, List<String>>> sendContactUpdateEmails() {
        List<String> successes = new ArrayList<>();
        List<String> failures = new ArrayList<>();

        List<User> users = userRepository.findAll();

        for (User user : users) {
            String stripeMemberId = user.getUserStripeMemberId();
            if (stripeMemberId == null || stripeMemberId.isEmpty()) {
                failures.add("User ID " + user.getId() + ": No StripeMemberID found");
                continue;
            }

            try {
                // Fetch email from Stripe
                Customer customer = Customer.retrieve(stripeMemberId);
                String email = customer.getEmail();
                if (email == null || email.isEmpty()) {
                    failures.add("User ID " + user.getId() + ": No email found in Stripe for " + stripeMemberId);
                    continue;
                }

                // Send email
                MimeMessage mimeMessage = mailSender.createMimeMessage();
                MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, "UTF-8");
                helper.setTo(email);
                helper.setFrom("CLT Lifting Club <contact@cltliftingclub.com>");
                helper.setSubject("Don’t Miss This! – CLT Lifting Club x Kingdom Kickbacks Social Event");
                String htmlContent = """
<!DOCTYPE html>
<html>
<head>
    <style>
        body { font-family: Arial, sans-serif; color: #333; }
        .container { max-width: 600px; margin: 0 auto; padding: 20px; }
        .header { background-color: #f8f8f8; padding: 10px; text-align: center; }
        .content { padding: 20px; }
        .footer { font-size: 12px; color: #777; text-align: center; }
        a.button {
            display: inline-block;
            padding: 10px 15px;
            background-color: #007BFF;
            color: white !important;
            text-decoration: none;
            border-radius: 5px;
            font-weight: bold;
        }
        a.button:hover {
            background-color: #0056b3;
        }
    </style>
</head>
<body>
    <div class="container">
        <div class="header">
            <h2>CLT Lifting Club</h2>
        </div>
        <div class="content">
            <p>CLT Lifting Club is teaming up with Kingdom Kickbacks for an epic Open Gym Social — a day packed with fitness, connections, and memories you won’t want to miss.</p>
            <p><strong>Here’s what’s going down:</strong></p>
            <ul>
                <li>Food Truck: Smart Eats</li>
                <li>Coffee Cart: Breezeway Coffee</li>
                <li>Cold Plunge: Plunge House</li>
                <li>Saunas to recover and recharge</li>
                <li>Live DJ for the perfect workout vibe</li>
                <li>Full Gym Access + fun fitness challenges</li>
                <li><strong>FREE for you and your friends</strong></li>
            </ul>
            <p><strong>Date:</strong> Saturday, August 16th | 10 AM – 1 PM</p>
            <p><strong>Location:</strong> CLT Lifting Club, 3100 South Boulevard, Charlotte, NC 28203</p>
            <p><strong>Bonus:</strong> Post a workout or event hype photo/video on August 16th using #CLTLiftingClub and tag @CLTLiftingClub for your chance to win a free CLT tee.</p>
            <p><a href="https://www.evite.com/signup-sheet/6025706806444032/?utm_campaign=send_sharable_link&utm_source=evitelink&utm_medium=sharable_invite" class="button">RSVP NOW</a> to let us know you’re coming, walk-ins are still welcome!</p>
            <p>Let’s make this the best South End community event of the summer.</p>
            <p>See you there,<br>The CLT Lifting Club Team</p>
        </div>
        <div class="footer">
            <p>CLT Lifting Club | %s</p>
        </div>
    </div>
</body>
</html>
""".formatted(NEW_CONTACT_EMAIL);
                helper.setText(htmlContent, true);
                mailSender.send(mimeMessage);


                successes.add("User ID " + user.getId() + ": Email sent to " + email);
            } catch (StripeException e) {
                failures.add("User ID " + user.getId() + ": Stripe error for " + stripeMemberId + " - " + e.getMessage());
            } catch (MessagingException e) {
                failures.add("User ID " + user.getId() + ": Email sending failed for " + stripeMemberId + " - " + e.getMessage());
            }
        }

        Map<String, List<String>> response = new HashMap<>();
        response.put("successes", successes);
        response.put("failures", failures);
        return ResponseEntity.ok(response);
    }
}

