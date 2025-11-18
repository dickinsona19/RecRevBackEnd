package com.BossLiftingClub.BossLifting.Stripe;

import com.stripe.exception.InvalidRequestException;
import com.stripe.model.*;
import com.stripe.model.checkout.Session;
import com.stripe.param.*;
import com.stripe.param.checkout.SessionCreateParams;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.stripe.Stripe;
import com.stripe.exception.StripeException;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class StripeService {

    public StripeService(@Value("${stripe.secret.key:}") String secretKey) {
        if (secretKey == null || secretKey.isEmpty() || "NOT_SET".equals(secretKey)) {
            throw new IllegalStateException("Stripe secret key is required. Please set STRIPE_SECRET_KEY environment variable.");
        }
        Stripe.apiKey = secretKey; // Set the Stripe API key from application.properties or application.yml
    }
    /**
     * Cancel a subscription immediately
     */
    public void cancelSubscription(String subscriptionId, String stripeAccountId) throws StripeException {
        if (subscriptionId == null || subscriptionId.isEmpty()) {
            throw new IllegalArgumentException("Subscription ID cannot be null or empty");
        }

        com.stripe.net.RequestOptions requestOptions = null;
        if (stripeAccountId != null && !stripeAccountId.isEmpty()) {
            requestOptions = com.stripe.net.RequestOptions.builder()
                    .setStripeAccount(stripeAccountId)
                    .build();
        }

        Subscription subscription;
        if (requestOptions != null) {
            subscription = Subscription.retrieve(subscriptionId, requestOptions);
            subscription.cancel(new HashMap<>(), requestOptions);
        } else {
            subscription = Subscription.retrieve(subscriptionId);
            subscription.cancel();
        }
    }

    /**
     * Cancel a subscription at the end of the current billing period
     * @param subscriptionId The Stripe subscription ID
     * @param stripeAccountId The connected account ID (null for platform account)
     * @return The LocalDateTime when the subscription will be cancelled
     * @throws StripeException if the Stripe API call fails
     */
    public LocalDateTime cancelSubscriptionAtPeriodEnd(String subscriptionId, String stripeAccountId) throws StripeException {
        if (subscriptionId == null || subscriptionId.isEmpty()) {
            throw new IllegalArgumentException("Subscription ID cannot be null or empty");
        }

        com.stripe.net.RequestOptions requestOptions = null;
        if (stripeAccountId != null && !stripeAccountId.isEmpty()) {
            requestOptions = com.stripe.net.RequestOptions.builder()
                    .setStripeAccount(stripeAccountId)
                    .build();
        }

        // Retrieve subscription
        Subscription subscription;
        if (requestOptions != null) {
            subscription = Subscription.retrieve(subscriptionId, requestOptions);
        } else {
            subscription = Subscription.retrieve(subscriptionId);
        }

        // Update subscription to cancel at period end
        Map<String, Object> params = new HashMap<>();
        params.put("cancel_at_period_end", true);

        Subscription updatedSubscription;
        if (requestOptions != null) {
            updatedSubscription = subscription.update(params, requestOptions);
        } else {
            updatedSubscription = subscription.update(params);
        }

        // Get the current period end and convert to LocalDateTime
        long currentPeriodEnd = updatedSubscription.getCurrentPeriodEnd();
        return LocalDateTime.ofEpochSecond(currentPeriodEnd, 0, java.time.ZoneOffset.UTC);
    }
    public String createCustomer(String email, String fullName, String paymentMethodId) throws StripeException {
        // Create customer with email and name
        Map<String, Object> customerParams = new HashMap<>();
        customerParams.put("email", email);
        customerParams.put("name", fullName); // Set the full name
        Customer customer = Customer.create(customerParams);

        // If paymentMethodId is provided, attach it and set as default
        if (paymentMethodId != null && !paymentMethodId.trim().isEmpty()) {
            // Attach the payment method to the customer
            PaymentMethod paymentMethod = PaymentMethod.retrieve(paymentMethodId);
            paymentMethod.attach(Map.of("customer", customer.getId()));

            // Set as default payment method for invoices
            customer.update(Map.of(
                    "invoice_settings", Map.of("default_payment_method", paymentMethodId)
            ));
        }
        return customer.getId();
    }

    public void attachPaymentMethod(String customerId, String paymentMethodId) throws StripeException {
        // Retrieve the payment method
        PaymentMethod paymentMethod = PaymentMethod.retrieve(paymentMethodId);

        // Attach it to the customer (if not already attached)
        Map<String, Object> attachParams = new HashMap<>();
        attachParams.put("customer", customerId);
        paymentMethod.attach(attachParams);

        // Explicitly set as default for invoices
        Customer customer = Customer.retrieve(customerId);
        Map<String, Object> updateParams = new HashMap<>();
        updateParams.put("invoice_settings", Map.of(
                "default_payment_method", paymentMethodId
        ));
        customer.update(updateParams);

        // Verify it’s set (optional debugging)
        Customer updatedCustomer = Customer.retrieve(customerId);
        String defaultPaymentMethod = updatedCustomer.getInvoiceSettings().getDefaultPaymentMethod();
        if (!paymentMethodId.equals(defaultPaymentMethod)) {
            throw new StripeException("Failed to set payment method as default", null, null, null) {
                public String getStripeErrorMessage() { return "Default payment method not set"; }
            };
        }
    }


    public void deleteCustomer(String customerId) throws StripeException {
        Customer customer = Customer.retrieve(customerId);
        customer.delete();
    }


    public String createSetupCheckoutSessionWithMetadata(
            String customerId,
            String successUrl,
            String cancelUrl,
            Map<String, String> metadata
    ) throws StripeException {
        SessionCreateParams params = SessionCreateParams.builder()
                .setCustomer(customerId)
                .setMode(SessionCreateParams.Mode.SETUP) // Setup mode for collecting payment method
                .addPaymentMethodType(SessionCreateParams.PaymentMethodType.CARD)
                .setSuccessUrl(successUrl)
                .setCancelUrl(cancelUrl)
                .putAllMetadata(metadata) // Add user data to metadata
                .build();

        Session session = Session.create(params);
        return session.getId();
    }

    /**
     * Create a Checkout Session for payment method collection on a connected account
     * @param customerId The Stripe customer ID
     * @param stripeAccountId The connected account ID
     * @param successUrl The URL to redirect to after successful setup
     * @param cancelUrl The URL to redirect to if setup is cancelled
     * @return The Checkout Session URL
     * @throws StripeException if the Stripe API call fails
     */
    public String createPaymentMethodCheckoutSession(
            String customerId,
            String stripeAccountId,
            String successUrl,
            String cancelUrl
    ) throws StripeException {
        if (customerId == null || customerId.isEmpty()) {
            throw new IllegalArgumentException("Customer ID cannot be null or empty");
        }
        if (stripeAccountId == null || stripeAccountId.isEmpty()) {
            throw new IllegalArgumentException("Stripe Account ID cannot be null or empty");
        }

        SessionCreateParams params = SessionCreateParams.builder()
                .setCustomer(customerId)
                .setMode(SessionCreateParams.Mode.SETUP)
                .addPaymentMethodType(SessionCreateParams.PaymentMethodType.CARD)
                .setSuccessUrl(successUrl)
                .setCancelUrl(cancelUrl)
                .build();

        com.stripe.net.RequestOptions requestOptions = com.stripe.net.RequestOptions.builder()
                .setStripeAccount(stripeAccountId)
                .build();

        Session session = Session.create(params, requestOptions);
        return session.getUrl();
    }

    /**
     * Pause a Stripe subscription using the pause_collection feature
     * @param subscriptionId The Stripe subscription ID to pause
     * @param resumeAt The timestamp when the subscription should automatically resume
     * @param stripeAccountId The connected account ID (null for platform account)
     * @return The updated Subscription object
     * @throws StripeException if the Stripe API call fails
     */
    public Subscription pauseSubscription(String subscriptionId, LocalDateTime resumeAt, String stripeAccountId) throws StripeException {
        if (subscriptionId == null || subscriptionId.isEmpty()) {
            throw new IllegalArgumentException("Subscription ID cannot be null or empty");
        }

        // Convert LocalDateTime to Unix timestamp (seconds since epoch)
        long resumeAtTimestamp = resumeAt.atZone(ZoneId.of("UTC")).toEpochSecond();

        // Use pause_collection to pause billing
        SubscriptionUpdateParams updateParams = SubscriptionUpdateParams.builder()
                .setPauseCollection(
                        SubscriptionUpdateParams.PauseCollection.builder()
                                .setBehavior(SubscriptionUpdateParams.PauseCollection.Behavior.VOID)
                                .setResumesAt(resumeAtTimestamp)
                                .build()
                )
                .build();

        com.stripe.net.RequestOptions requestOptions = null;
        if (stripeAccountId != null && !stripeAccountId.isEmpty()) {
            requestOptions = com.stripe.net.RequestOptions.builder()
                    .setStripeAccount(stripeAccountId)
                    .build();
        }

        Subscription subscription;
        if (requestOptions != null) {
            subscription = Subscription.retrieve(subscriptionId, requestOptions);
            return subscription.update(updateParams, requestOptions);
        } else {
            subscription = Subscription.retrieve(subscriptionId);
            return subscription.update(updateParams);
        }
    }

    /**
     * Resume a paused Stripe subscription
     * @param subscriptionId The Stripe subscription ID to resume
     * @param stripeAccountId The connected account ID (null for platform account)
     * @return The updated Subscription object
     * @throws StripeException if the Stripe API call fails
     */
    public Subscription resumeSubscription(String subscriptionId, String stripeAccountId) throws StripeException {
        if (subscriptionId == null || subscriptionId.isEmpty()) {
            throw new IllegalArgumentException("Subscription ID cannot be null or empty");
        }

        com.stripe.net.RequestOptions requestOptions = null;
        if (stripeAccountId != null && !stripeAccountId.isEmpty()) {
            requestOptions = com.stripe.net.RequestOptions.builder()
                    .setStripeAccount(stripeAccountId)
                    .build();
        }

        Subscription subscription;
        if (requestOptions != null) {
            subscription = Subscription.retrieve(subscriptionId, requestOptions);
        } else {
            subscription = Subscription.retrieve(subscriptionId);
        }

        // Clear pause collection by setting it to null
        Map<String, Object> params = new HashMap<>();
        params.put("pause_collection", "");

        if (requestOptions != null) {
            return subscription.update(params, requestOptions);
        } else {
            return subscription.update(params);
        }
    }

    /**
     * Check if a customer has a default payment method
     * @param customerId The Stripe customer ID
     * @param stripeAccountId The connected account ID (null for platform account)
     * @return true if customer has a default payment method, false otherwise
     * @throws StripeException if the Stripe API call fails
     */
    public boolean hasDefaultPaymentMethod(String customerId, String stripeAccountId) throws StripeException {
        if (customerId == null || customerId.isEmpty()) {
            return false;
        }

        try {
            Customer customer;
            if (stripeAccountId != null && !stripeAccountId.isEmpty()) {
                // Retrieve customer from connected account
                com.stripe.net.RequestOptions requestOptions = com.stripe.net.RequestOptions.builder()
                        .setStripeAccount(stripeAccountId)
                        .build();
                customer = Customer.retrieve(customerId, requestOptions);
            } else {
                // Retrieve customer from platform account
                customer = Customer.retrieve(customerId);
            }

            // Check if customer has a default payment method
            if (customer.getInvoiceSettings() != null &&
                customer.getInvoiceSettings().getDefaultPaymentMethod() != null) {
                return true;
            }

            // Also check if customer has any payment methods attached
            Map<String, Object> params = new HashMap<>();
            params.put("customer", customerId);
            params.put("type", "card");
            params.put("limit", 1);

            PaymentMethodCollection paymentMethods;
            if (stripeAccountId != null && !stripeAccountId.isEmpty()) {
                com.stripe.net.RequestOptions requestOptions = com.stripe.net.RequestOptions.builder()
                        .setStripeAccount(stripeAccountId)
                        .build();
                paymentMethods = PaymentMethod.list(params, requestOptions);
            } else {
                paymentMethods = PaymentMethod.list(params);
            }

            return paymentMethods.getData() != null && !paymentMethods.getData().isEmpty();
        } catch (StripeException e) {
            System.err.println("Error checking payment method: " + e.getMessage());
            return false;
        }
    }

    /**
     * Attach payment method to customer on connected account and set as default
     * @param customerId The Stripe customer ID
     * @param paymentMethodId The payment method ID to attach
     * @param stripeAccountId The connected account ID
     * @throws StripeException if the Stripe API call fails
     */
    public void attachPaymentMethodOnConnectedAccount(String customerId, String paymentMethodId, String stripeAccountId) throws StripeException {
        if (customerId == null || customerId.isEmpty()) {
            throw new IllegalArgumentException("Customer ID cannot be null or empty");
        }
        if (paymentMethodId == null || paymentMethodId.isEmpty()) {
            throw new IllegalArgumentException("Payment Method ID cannot be null or empty");
        }
        if (stripeAccountId == null || stripeAccountId.isEmpty()) {
            throw new IllegalArgumentException("Stripe Account ID cannot be null or empty");
        }

        // Create request options for connected account
        com.stripe.net.RequestOptions connectedAccountOptions = com.stripe.net.RequestOptions.builder()
                .setStripeAccount(stripeAccountId)
                .build();

        // Payment methods can be created on either the platform account or connected account
        // Try to retrieve from platform account first (most common case with Stripe.js)
        PaymentMethod paymentMethod = null;
        try {
            // First try: retrieve from platform account (no account context)
            paymentMethod = PaymentMethod.retrieve(paymentMethodId);
            System.out.println("Retrieved PaymentMethod " + paymentMethodId + " from platform account");
        } catch (StripeException e) {
            // If not found on platform account, try retrieving from connected account
            if (e.getCode() != null && e.getCode().equals("resource_missing")) {
                try {
                    System.out.println("PaymentMethod not found on platform account, trying connected account...");
                    paymentMethod = PaymentMethod.retrieve(paymentMethodId, connectedAccountOptions);
                    System.out.println("Retrieved PaymentMethod " + paymentMethodId + " from connected account");
                } catch (StripeException e2) {
                    // If still not found, throw original error with helpful message
                    throw new StripeException(
                        "PaymentMethod " + paymentMethodId + " not found on platform account or connected account " + stripeAccountId + ". " +
                        "Ensure the PaymentMethod was created correctly. Original error: " + e.getMessage(),
                        null, null, null
                    ) {
                        public String getStripeErrorMessage() { 
                            return "PaymentMethod not found. Please ensure it was created with the correct account context."; 
                        }
                    };
                }
            } else {
                // Re-throw if it's a different error
                throw e;
            }
        }
        
        // Attach the payment method to the customer on the connected account
        Map<String, Object> attachParams = new HashMap<>();
        attachParams.put("customer", customerId);
        
        // If payment method was retrieved from platform account, attach it to connected account customer
        // If it was retrieved from connected account, it should already be on that account
        try {
            paymentMethod.attach(attachParams, connectedAccountOptions);
            System.out.println("Attached PaymentMethod " + paymentMethodId + " to customer " + customerId + " on connected account");
        } catch (StripeException e) {
            // If attach fails because payment method is already attached, that's okay - continue
            if (e.getCode() != null && e.getCode().equals("resource_already_exists")) {
                System.out.println("PaymentMethod " + paymentMethodId + " is already attached to customer " + customerId);
            } else {
                throw e;
            }
        }

        // Explicitly set as default for invoices
        Customer customer = Customer.retrieve(customerId, connectedAccountOptions);
        Map<String, Object> updateParams = new HashMap<>();
        updateParams.put("invoice_settings", Map.of(
                "default_payment_method", paymentMethodId
        ));
        customer.update(updateParams, connectedAccountOptions);

        System.out.println("Payment method " + paymentMethodId + " attached and set as default for customer " + customerId + " on connected account " + stripeAccountId);

        // Verify it's set (optional debugging)
        Customer updatedCustomer = Customer.retrieve(customerId, connectedAccountOptions);
        String defaultPaymentMethod = updatedCustomer.getInvoiceSettings().getDefaultPaymentMethod();
        if (!paymentMethodId.equals(defaultPaymentMethod)) {
            throw new StripeException("Failed to set payment method as default on connected account", null, null, null) {
                public String getStripeErrorMessage() { return "Default payment method not set"; }
            };
        }
    }

    /**
     * Create a Stripe customer on a connected account
     * @param email The customer's email address
     * @param fullName The customer's full name
     * @param stripeAccountId The connected account ID
     * @return The created customer ID
     * @throws StripeException if the Stripe API call fails
     */
    public String createCustomerOnConnectedAccount(String email, String fullName, String stripeAccountId) throws StripeException {
        if (email == null || email.isEmpty()) {
            throw new IllegalArgumentException("Email cannot be null or empty");
        }
        if (stripeAccountId == null || stripeAccountId.isEmpty()) {
            throw new IllegalArgumentException("Stripe Account ID cannot be null or empty");
        }

        // Create customer params
        Map<String, Object> customerParams = new HashMap<>();
        customerParams.put("email", email);
        if (fullName != null && !fullName.isEmpty()) {
            customerParams.put("name", fullName);
        }

        // Create request options for connected account
        com.stripe.net.RequestOptions requestOptions = com.stripe.net.RequestOptions.builder()
                .setStripeAccount(stripeAccountId)
                .build();

        // Create customer on the connected account
        Customer customer = Customer.create(customerParams, requestOptions);
        return customer.getId();
    }

    /**
     * Create a Stripe subscription for a customer on a connected account
     * @param customerId The Stripe customer ID (on the platform account)
     * @param stripePriceId The Stripe price ID to subscribe to
     * @param stripeAccountId The connected account ID
     * @param anchorDate The billing anchor date (when subscription should start)
     * @return The created Subscription object
     * @throws StripeException if the Stripe API call fails
     */
    public Subscription createSubscription(String customerId, String stripePriceId, String stripeAccountId, LocalDateTime anchorDate) throws StripeException {
        if (customerId == null || customerId.isEmpty()) {
            throw new IllegalArgumentException("Customer ID cannot be null or empty");
        }
        if (stripePriceId == null || stripePriceId.isEmpty()) {
            throw new IllegalArgumentException("Price ID cannot be null or empty");
        }
        if (stripeAccountId == null || stripeAccountId.isEmpty()) {
            throw new IllegalArgumentException("Stripe Account ID cannot be null or empty");
        }

        // Convert anchorDate to Unix timestamp using system default timezone
        // If anchorDate is in the past or today, use "now" to avoid Stripe errors
        long billingCycleAnchor = anchorDate.atZone(ZoneId.systemDefault()).toEpochSecond();
        long currentTimestamp = LocalDateTime.now().atZone(ZoneId.systemDefault()).toEpochSecond();

        // If the anchor date is in the past or within the next 10 seconds, don't set billing_cycle_anchor
        // Let Stripe use the default behavior (immediate start)
        boolean shouldSetAnchor = billingCycleAnchor > (currentTimestamp + 10);

        // Create subscription on the connected account
        com.stripe.param.SubscriptionCreateParams.Builder paramsBuilder = com.stripe.param.SubscriptionCreateParams.builder()
                .setCustomer(customerId)
                .addItem(
                        com.stripe.param.SubscriptionCreateParams.Item.builder()
                                .setPrice(stripePriceId)
                                .build()
                );

        // Only set billing cycle anchor if it's in the future
        if (shouldSetAnchor) {
            paramsBuilder.setBillingCycleAnchor(billingCycleAnchor);
        }

        com.stripe.net.RequestOptions requestOptions = com.stripe.net.RequestOptions.builder()
                .setStripeAccount(stripeAccountId)
                .build();

        return Subscription.create(paramsBuilder.build(), requestOptions);
    }

    /**
     * Get payment method details (brand and last 4 digits) for a customer
     * @param customerId The Stripe customer ID
     * @param stripeAccountId The connected account ID (null for platform account)
     * @return Map containing brand and last4 of the default payment method
     * @throws StripeException if the Stripe API call fails
     */
    public Map<String, String> getPaymentMethodDetails(String customerId, String stripeAccountId) throws StripeException {
        if (customerId == null || customerId.isEmpty()) {
            throw new IllegalArgumentException("Customer ID cannot be null or empty");
        }

        try {
            Customer customer;
            com.stripe.net.RequestOptions requestOptions = null;

            if (stripeAccountId != null && !stripeAccountId.isEmpty()) {
                requestOptions = com.stripe.net.RequestOptions.builder()
                        .setStripeAccount(stripeAccountId)
                        .build();
                customer = Customer.retrieve(customerId, requestOptions);
            } else {
                customer = Customer.retrieve(customerId);
            }

            String paymentMethodId = null;

            // Try to get default payment method
            if (customer.getInvoiceSettings() != null &&
                customer.getInvoiceSettings().getDefaultPaymentMethod() != null) {
                paymentMethodId = customer.getInvoiceSettings().getDefaultPaymentMethod();
            }

            // If no default payment method, get the first available one
            if (paymentMethodId == null) {
                Map<String, Object> params = new HashMap<>();
                params.put("customer", customerId);
                params.put("type", "card");
                params.put("limit", 1);

                PaymentMethodCollection paymentMethods;
                if (requestOptions != null) {
                    paymentMethods = PaymentMethod.list(params, requestOptions);
                } else {
                    paymentMethods = PaymentMethod.list(params);
                }

                if (paymentMethods.getData() != null && !paymentMethods.getData().isEmpty()) {
                    paymentMethodId = paymentMethods.getData().get(0).getId();
                }
            }

            if (paymentMethodId == null) {
                throw new StripeException("No payment method found for customer", null, null, null) {};
            }

            // Retrieve payment method details
            PaymentMethod paymentMethod;
            if (requestOptions != null) {
                paymentMethod = PaymentMethod.retrieve(paymentMethodId, requestOptions);
            } else {
                paymentMethod = PaymentMethod.retrieve(paymentMethodId);
            }

            Map<String, String> details = new HashMap<>();
            if (paymentMethod.getCard() != null) {
                details.put("brand", paymentMethod.getCard().getBrand());
                details.put("last4", paymentMethod.getCard().getLast4());
            } else {
                details.put("brand", "card");
                details.put("last4", "****");
            }

            return details;
        } catch (StripeException e) {
            System.err.println("Error getting payment method details: " + e.getMessage());
            throw e;
        }
    }

    /**
     * Get payment history for a customer from Stripe
     * @param customerId The Stripe customer ID
     * @param stripeAccountId The connected account ID (null for platform account)
     * @return List of payment charges with details
     * @throws StripeException if the Stripe API call fails
     */
    public List<Map<String, Object>> getPaymentHistory(String customerId, String stripeAccountId) throws StripeException {
        if (customerId == null || customerId.isEmpty()) {
            throw new IllegalArgumentException("Customer ID cannot be null or empty");
        }

        try {
            com.stripe.net.RequestOptions requestOptions = null;
            if (stripeAccountId != null && !stripeAccountId.isEmpty()) {
                requestOptions = com.stripe.net.RequestOptions.builder()
                        .setStripeAccount(stripeAccountId)
                        .build();
            }

            // Retrieve charges for this customer (limit to 100 most recent)
            Map<String, Object> params = new HashMap<>();
            params.put("customer", customerId);
            params.put("limit", 100);

            ChargeCollection charges;
            if (requestOptions != null) {
                charges = Charge.list(params, requestOptions);
            } else {
                charges = Charge.list(params);
            }

            // Convert charges to a list of maps for easier frontend consumption
            List<Map<String, Object>> paymentHistory = new java.util.ArrayList<>();
            for (Charge charge : charges.getData()) {
                Map<String, Object> chargeData = new HashMap<>();
                chargeData.put("id", charge.getId());
                chargeData.put("amount", charge.getAmount() / 100.0); // Convert cents to dollars
                chargeData.put("currency", charge.getCurrency().toUpperCase());
                chargeData.put("status", charge.getStatus());
                chargeData.put("created", charge.getCreated()); // Unix timestamp
                chargeData.put("description", charge.getDescription());
                chargeData.put("paid", charge.getPaid());
                chargeData.put("refunded", charge.getRefunded());

                // Add refund details if charge has been refunded
                if (charge.getRefunded() || (charge.getAmountRefunded() != null && charge.getAmountRefunded() > 0)) {
                    chargeData.put("amountRefunded", charge.getAmountRefunded() / 100.0); // Convert cents to dollars

                    // Determine if it's a full or partial refund
                    boolean isFullRefund = charge.getAmountRefunded().equals(charge.getAmount());
                    chargeData.put("refundType", isFullRefund ? "full" : "partial");

                    // Get detailed refund information
                    try {
                        Map<String, Object> refundParams = new HashMap<>();
                        refundParams.put("charge", charge.getId());
                        refundParams.put("limit", 10); // Get up to 10 refunds for this charge

                        RefundCollection refunds;
                        if (requestOptions != null) {
                            refunds = Refund.list(refundParams, requestOptions);
                        } else {
                            refunds = Refund.list(refundParams);
                        }

                        // Convert refunds to a list
                        List<Map<String, Object>> refundList = new java.util.ArrayList<>();
                        for (Refund refund : refunds.getData()) {
                            Map<String, Object> refundData = new HashMap<>();
                            refundData.put("id", refund.getId());
                            refundData.put("amount", refund.getAmount() / 100.0);
                            refundData.put("created", refund.getCreated());
                            refundData.put("status", refund.getStatus());
                            refundData.put("reason", refund.getReason());
                            refundList.add(refundData);
                        }
                        chargeData.put("refunds", refundList);
                    } catch (Exception e) {
                        System.err.println("Error fetching refund details for charge " + charge.getId() + ": " + e.getMessage());
                        // Continue without refund details
                    }
                }

                // Add payment method details if available
                if (charge.getPaymentMethodDetails() != null &&
                    charge.getPaymentMethodDetails().getCard() != null) {
                    chargeData.put("cardBrand", charge.getPaymentMethodDetails().getCard().getBrand());
                    chargeData.put("cardLast4", charge.getPaymentMethodDetails().getCard().getLast4());
                }

                // Add receipt URL if available
                if (charge.getReceiptUrl() != null) {
                    chargeData.put("receiptUrl", charge.getReceiptUrl());
                }

                paymentHistory.add(chargeData);
            }

            return paymentHistory;
        } catch (StripeException e) {
            System.err.println("Error fetching payment history: " + e.getMessage());
            throw e;
        }
    }

    /**
     * Create a refund for a charge
     * @param chargeId The Stripe charge ID to refund
     * @param amount The amount in cents to refund (null for full refund)
     * @param stripeAccountId The connected account ID (null for platform account)
     * @return Map containing refund details
     * @throws StripeException if the Stripe API call fails
     */
    public Map<String, Object> createRefund(String chargeId, Long amount, String stripeAccountId) throws StripeException {
        if (chargeId == null || chargeId.isEmpty()) {
            throw new IllegalArgumentException("Charge ID cannot be null or empty");
        }

        try {
            com.stripe.net.RequestOptions requestOptions = null;
            if (stripeAccountId != null && !stripeAccountId.isEmpty()) {
                requestOptions = com.stripe.net.RequestOptions.builder()
                        .setStripeAccount(stripeAccountId)
                        .build();
            }

            // Create refund params
            Map<String, Object> params = new HashMap<>();
            params.put("charge", chargeId);
            if (amount != null && amount > 0) {
                params.put("amount", amount);
            }
            // If amount is null, Stripe will refund the full amount

            // Create refund
            Refund refund;
            if (requestOptions != null) {
                refund = Refund.create(params, requestOptions);
            } else {
                refund = Refund.create(params);
            }

            // Return refund details
            Map<String, Object> result = new HashMap<>();
            result.put("id", refund.getId());
            result.put("amount", refund.getAmount());
            result.put("currency", refund.getCurrency());
            result.put("status", refund.getStatus());
            result.put("chargeId", refund.getCharge());
            result.put("created", refund.getCreated());

            return result;
        } catch (StripeException e) {
            System.err.println("Error creating refund: " + e.getMessage());
            throw e;
        }
    }

    /**
     * Create a one-time payment intent for product purchases
     * @param customerId The Stripe customer ID
     * @param amount The amount in cents to charge
     * @param currency The currency code (e.g., "usd")
     * @param description Description of the purchase
     * @param stripeAccountId The connected account ID (null for platform account)
     * @return Map containing payment intent details
     * @throws StripeException if the Stripe API call fails
     */
    public Map<String, Object> createOneTimePayment(String customerId, Long amount, String currency, String description, String stripeAccountId) throws StripeException {
        if (customerId == null || customerId.isEmpty()) {
            throw new IllegalArgumentException("Customer ID cannot be null or empty");
        }
        if (amount == null || amount <= 0) {
            throw new IllegalArgumentException("Amount must be greater than zero");
        }

        try {
            com.stripe.net.RequestOptions requestOptions = null;
            if (stripeAccountId != null && !stripeAccountId.isEmpty()) {
                requestOptions = com.stripe.net.RequestOptions.builder()
                        .setStripeAccount(stripeAccountId)
                        .build();
            }

            // Get the customer to retrieve the default payment method
            Customer customer;
            if (requestOptions != null) {
                customer = Customer.retrieve(customerId, requestOptions);
            } else {
                customer = Customer.retrieve(customerId);
            }

            String paymentMethodId = null;
            if (customer.getInvoiceSettings() != null &&
                customer.getInvoiceSettings().getDefaultPaymentMethod() != null) {
                paymentMethodId = customer.getInvoiceSettings().getDefaultPaymentMethod();
            }

            if (paymentMethodId == null) {
                // Try to get the first available payment method
                Map<String, Object> pmParams = new HashMap<>();
                pmParams.put("customer", customerId);
                pmParams.put("type", "card");
                pmParams.put("limit", 1);

                PaymentMethodCollection paymentMethods;
                if (requestOptions != null) {
                    paymentMethods = PaymentMethod.list(pmParams, requestOptions);
                } else {
                    paymentMethods = PaymentMethod.list(pmParams);
                }

                if (paymentMethods.getData() != null && !paymentMethods.getData().isEmpty()) {
                    paymentMethodId = paymentMethods.getData().get(0).getId();
                } else {
                    throw new StripeException("No payment method found for customer", null, null, null) {};
                }
            }

            // Create payment intent params
            Map<String, Object> params = new HashMap<>();
            params.put("amount", amount);
            params.put("currency", currency != null ? currency.toLowerCase() : "usd");
            params.put("customer", customerId);
            params.put("payment_method", paymentMethodId);
            params.put("description", description);
            params.put("confirm", true); // Automatically confirm the payment
            params.put("off_session", true); // Allow charging when customer is not present

            // Create payment intent
            PaymentIntent paymentIntent;
            if (requestOptions != null) {
                paymentIntent = PaymentIntent.create(params, requestOptions);
            } else {
                paymentIntent = PaymentIntent.create(params);
            }

            // Return payment intent details
            Map<String, Object> result = new HashMap<>();
            result.put("id", paymentIntent.getId());
            result.put("amount", paymentIntent.getAmount());
            result.put("currency", paymentIntent.getCurrency());
            result.put("status", paymentIntent.getStatus());
            result.put("description", paymentIntent.getDescription());

            // Add charge details if available - need to retrieve the latest charge
            String latestChargeId = paymentIntent.getLatestCharge();
            if (latestChargeId != null && !latestChargeId.isEmpty()) {
                try {
                    Charge charge;
                    if (requestOptions != null) {
                        charge = Charge.retrieve(latestChargeId, requestOptions);
                    } else {
                        charge = Charge.retrieve(latestChargeId);
                    }
                    if (charge.getReceiptUrl() != null) {
                        result.put("receiptUrl", charge.getReceiptUrl());
                    }
                } catch (StripeException e) {
                    // Ignore charge retrieval errors, just don't include receipt URL
                    System.err.println("Could not retrieve charge details: " + e.getMessage());
                }
            }

            return result;
        } catch (StripeException e) {
            System.err.println("Error creating one-time payment: " + e.getMessage());
            throw e;
        }
    }

}