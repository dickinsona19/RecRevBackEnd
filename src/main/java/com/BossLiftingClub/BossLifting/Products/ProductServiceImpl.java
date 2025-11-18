package com.BossLiftingClub.BossLifting.Products;

import com.google.api.client.util.Value;
import com.stripe.Stripe;
import com.stripe.exception.StripeException;
import com.stripe.model.*;
import com.stripe.param.InvoiceCreateParams;
import com.stripe.param.InvoiceItemCreateParams;
import com.stripe.param.PaymentIntentConfirmParams;
import com.stripe.param.TransferCreateParams;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class ProductServiceImpl implements ProductService {

    @Value("${stripe.secret.key:}")
    private String stripeApiKey;

    private final ProductRepository productRepository;

    public ProductServiceImpl(ProductRepository productRepository) {
        this.productRepository = productRepository;
        Stripe.apiKey = stripeApiKey;
    }

    @Override
    public List<Products> getAllProducts() {
        return productRepository.findAll();
    }

    @Override
    public Products addProduct(Products product) {
        return productRepository.save(product); // Just save to DB
    }

    @Override
    public Products updateProduct(Long id, Products updatedProduct) {
        return productRepository.findById(id).map(product -> {
            product.setName(updatedProduct.getName());
            product.setDefinition(updatedProduct.getDefinition());
            product.setPrice(updatedProduct.getPrice());
            product.setCategory(updatedProduct.getCategory());
            product.setImageUrl(updatedProduct.getImageUrl());
            return productRepository.save(product);
        }).orElseThrow(() -> new RuntimeException("Product not found"));
    }

    @Override
    public void deleteProduct(Long id) {
        Products product = productRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Product not found"));

        productRepository.deleteById(id);
    }
    @Override
    public String createInvoiceForUser(Long productId, String stripeCustomerId, int quantity) {
        Products product = productRepository.findById(productId)
                .orElseThrow(() -> new RuntimeException("Product not found"));

        try {
            // Validate inputs
            if (quantity <= 0) {
                throw new IllegalArgumentException("Quantity must be greater than zero");
            }
            if (product.getPrice() <= 0) {
                throw new IllegalArgumentException("Product price must be greater than zero");
            }

            // Validate customer and payment method
            Customer customer = Customer.retrieve(stripeCustomerId);
            if (customer.getDeleted() != null && customer.getDeleted()) {
                throw new RuntimeException("Customer is deleted: " + stripeCustomerId);
            }
            String defaultPaymentMethodId = customer.getInvoiceSettings().getDefaultPaymentMethod();
            if (defaultPaymentMethodId == null) {
                throw new RuntimeException("Customer has no default payment method: " + stripeCustomerId + ". Please add a payment method.");
            }
            // Verify payment method
            PaymentMethod paymentMethod = PaymentMethod.retrieve(defaultPaymentMethodId);
            if (!"card".equals(paymentMethod.getType()) || paymentMethod.getCard() == null) {
                throw new RuntimeException("Invalid default payment method for customer: " + stripeCustomerId + ", type: " + paymentMethod.getType());
            }
            System.out.println("Customer " + stripeCustomerId + " has valid default payment method: " + defaultPaymentMethodId +
                    ", card: " + paymentMethod.getCard().getLast4());

            long unitAmountCents = (long) (product.getPrice() * 100); // Convert price to cents
            long totalAmountCents = unitAmountCents * quantity; // Total pre-tax amount in cents for reference
            String taxRateId = "txr_1RF33tGHcVHSTvgIzTwKENXt"; // 7.5% tax rate

            // Validate tax rate
            TaxRate taxRate = TaxRate.retrieve(taxRateId);
            if (!taxRate.getActive()) {
                throw new RuntimeException("Tax rate is inactive: " + taxRateId);
            }

            // Log input details
            System.out.println("Creating invoice for customer " + stripeCustomerId +
                    ", product: " + product.getName() +
                    ", price: " + product.getPrice() +
                    ", quantity: " + quantity +
                    ", unit_amount: " + (unitAmountCents / 100.0) +
                    ", total pre-tax: " + (totalAmountCents / 100.0) + " USD");

            // 1️⃣ Create invoice with explicit payment method
            InvoiceCreateParams invoiceParams = InvoiceCreateParams.builder()
                    .setCustomer(stripeCustomerId)
                    .setCollectionMethod(InvoiceCreateParams.CollectionMethod.CHARGE_AUTOMATICALLY)
                    .setDefaultPaymentMethod(defaultPaymentMethodId) // Explicitly set payment method
                    .setAutoAdvance(true) // Attempts to finalize and pay automatically
                    .build();
            Invoice invoice = Invoice.create(invoiceParams);
            System.out.println("Created Invoice with ID: " + invoice.getId() +
                    ", status: " + invoice.getStatus() +
                    ", amount_due: " + (invoice.getAmountDue() / 100.0));

            // 2️⃣ Create invoice item linked to the invoice
            InvoiceItemCreateParams invoiceItemParams = InvoiceItemCreateParams.builder()
                    .setCustomer(stripeCustomerId)
                    .setInvoice(invoice.getId()) // Explicitly link to the invoice
                    .setUnitAmount(unitAmountCents) // Price per unit in cents
                    .setQuantity((long) quantity) // Number of units
                    .setCurrency("usd")
                    .setDescription(product.getName())
                    .addTaxRate(taxRateId)
                    .build();
            InvoiceItem invoiceItem = InvoiceItem.create(invoiceItemParams);
            System.out.println("Created InvoiceItem with ID: " + invoiceItem.getId() +
                    ", amount: " + (invoiceItem.getAmount() / 100.0) +
                    ", description: " + invoiceItem.getDescription());

            // 3️⃣ Finalize and pay invoice
            invoice = invoice.finalizeInvoice();
            System.out.println("Finalized Invoice with ID: " + invoice.getId() +
                    ", status: " + invoice.getStatus() +
                    ", amount_due: " + (invoice.getAmountDue() / 100.0) +
                    ", payment_intent: " + invoice.getPaymentIntent());

            if (invoice.getAmountDue() <= 0) {
                throw new RuntimeException("Invoice has no amount due: check invoice items or payment method. " +
                        "InvoiceItem ID: " + invoiceItem.getId() +
                        ", Invoice ID: " + invoice.getId());
            }

            // 4️⃣ Handle open invoice (attempt payment if not paid)
            if ("open".equals(invoice.getStatus()) && invoice.getPaymentIntent() != null) {
                System.out.println("Invoice is open, attempting to pay with payment intent: " + invoice.getPaymentIntent());
                try {
                    PaymentIntent paymentIntent = PaymentIntent.retrieve(invoice.getPaymentIntent());
                    if (!"succeeded".equals(paymentIntent.getStatus())) {
                        PaymentIntentConfirmParams confirmParams = PaymentIntentConfirmParams.builder()
                                .setPaymentMethod(defaultPaymentMethodId)
                                .build();
                        paymentIntent = paymentIntent.confirm(confirmParams);
                        System.out.println("Payment intent confirmed, status: " + paymentIntent.getStatus());
                    }
                    // Refresh invoice to check payment status
                    invoice = Invoice.retrieve(invoice.getId());
                    System.out.println("Refreshed Invoice with ID: " + invoice.getId() +
                            ", status: " + invoice.getStatus() +
                            ", charge: " + invoice.getCharge());
                } catch (StripeException e) {
                    System.out.println("Payment intent confirmation failed: " + e.getMessage() + "; request-id: " + e.getRequestId());
                    throw new RuntimeException("Failed to confirm payment intent: " + e.getMessage() +
                            ", invoice: " + invoice.getId() + ", payment_intent: " + invoice.getPaymentIntent());
                }
            }

            // 5️⃣ Verify payment success
            if (!"paid".equals(invoice.getStatus()) || invoice.getCharge() == null) {
                throw new RuntimeException("Invoice payment failed: status is " + invoice.getStatus() +
                        ", charge: " + invoice.getCharge() +
                        ", payment_intent: " + invoice.getPaymentIntent());
            }

            // 6️⃣ Transfer 4% to Connected Account
            long feeAmountCents = (long) (totalAmountCents * 0.04); // 4% of pre-tax amount
            if (feeAmountCents > 0) {
                // Check available balance
                Balance balance = Balance.retrieve();
                long availableBalance = balance.getAvailable().stream()
                        .filter(b -> "usd".equals(b.getCurrency()))
                        .mapToLong(Balance.Available::getAmount)
                        .sum();
                if (availableBalance < feeAmountCents) {
                    // Optionally queue transfer (implement as discussed previously)
                    throw new RuntimeException("Insufficient balance for transfer: available " +
                            (availableBalance / 100.0) + " USD, needed " + (feeAmountCents / 100.0));
                }

                TransferCreateParams transferParams = TransferCreateParams.builder()
                        .setAmount(feeAmountCents)
                        .setCurrency("usd")
                        .setDestination("acct_1RDvRj4gikNsBARu")
                        .setSourceTransaction(invoice.getCharge()) // Tie transfer to the charge
                        .setTransferGroup(invoice.getId())
                        .build();
                Transfer transfer = Transfer.create(transferParams);
                System.out.println("Transferred " + (feeAmountCents / 100.0) + " USD to Connected Account for invoice " + invoice.getId());
            }

            // 7️⃣ Return hosted invoice URL
            return invoice.getHostedInvoiceUrl();

        } catch (StripeException e) {
            throw new RuntimeException("Stripe error: " + e.getMessage() + "; request-id: " + e.getRequestId());
        }
    }
}
