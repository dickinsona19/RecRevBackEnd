package com.BossLiftingClub.BossLifting.Stripe;

import com.stripe.exception.InvalidRequestException;
import com.stripe.model.*;
import com.stripe.model.checkout.Session;
import com.stripe.param.checkout.SessionCreateParams;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.stripe.Stripe;
import com.stripe.exception.StripeException;
import com.stripe.param.PriceCreateParams;
import com.stripe.param.ProductCreateParams;
import com.stripe.param.PaymentIntentCreateParams;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class StripeService {

    public StripeService(@Value("${stripe.secret.key}") String secretKey) {
        Stripe.apiKey = secretKey; // Set the Stripe API key from application.properties or application.yml
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

}