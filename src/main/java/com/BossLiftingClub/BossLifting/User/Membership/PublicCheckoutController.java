package com.BossLiftingClub.BossLifting.User.Membership;

import com.BossLiftingClub.BossLifting.Business.Business;
import com.BossLiftingClub.BossLifting.Business.BusinessRepository;
import com.BossLiftingClub.BossLifting.Promo.Promo;
import com.BossLiftingClub.BossLifting.Promo.PromoRepository;
import com.BossLiftingClub.BossLifting.Stripe.StripeService;
import com.BossLiftingClub.BossLifting.User.User;
import com.BossLiftingClub.BossLifting.User.UserRepository;
import com.stripe.exception.StripeException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/public/checkout")
public class PublicCheckoutController {

    @Autowired
    private MembershipRepository membershipRepository;

    @Autowired
    private BusinessRepository businessRepository;

    @Autowired
    private StripeService stripeService;

    @Autowired
    private PromoRepository promoRepository;

    @Autowired
    private UserRepository userRepository;

    @Value("${app.frontend.url:http://localhost:5173}")
    private String frontendUrl;

    /**
     * Create a Stripe Checkout Session for public membership signup
     */
    @PostMapping("/create-session")
    @Transactional(readOnly = true)
    public ResponseEntity<?> createCheckoutSession(@RequestBody CheckoutSessionRequest request) {
        try {
            // Validate required fields
            if (request.getBusinessTag() == null || request.getBusinessTag().isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "businessTag is required"));
            }
            if (request.getMembershipId() == null) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "membershipId is required"));
            }

            // Fetch business
            Optional<Business> businessOpt = businessRepository.findByBusinessTag(request.getBusinessTag());
            if (businessOpt.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("error", "Business not found with tag: " + request.getBusinessTag()));
            }
            Business business = businessOpt.get();

            // Check if Stripe is configured
            if (business.getStripeAccountId() == null || business.getStripeAccountId().isEmpty()) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(Map.of("error", "Business does not have Stripe configured"));
            }

            // Fetch membership
            Optional<Membership> membershipOpt = membershipRepository.findById(request.getMembershipId());
            if (membershipOpt.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("error", "Membership not found"));
            }
            Membership membership = membershipOpt.get();

            // Verify membership is public and belongs to this business
            if (!membership.isPublic()) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(Map.of("error", "Membership is not available for public signup"));
            }
            if (!membership.getBusinessTag().equals(request.getBusinessTag())) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(Map.of("error", "Membership does not belong to this business"));
            }

            // Validate promo code if provided
            String stripePromoCodeId = null;
            if (request.getPromoCode() != null && !request.getPromoCode().isEmpty()) {
                Optional<Promo> promoOpt = promoRepository.findByCodeToken(request.getPromoCode());
                if (promoOpt.isEmpty()) {
                    return ResponseEntity.badRequest()
                            .body(Map.of("error", "Invalid promo code"));
                }
                Promo promo = promoOpt.get();
                // Verify promo belongs to this business
                if (!promo.getBusiness().getId().equals(business.getId())) {
                    return ResponseEntity.badRequest()
                            .body(Map.of("error", "Promo code does not belong to this business"));
                }
                stripePromoCodeId = promo.getStripePromoCodeId();
            }

            // Validate referral code if provided
            Long referredById = null;
            if (request.getReferralCode() != null && !request.getReferralCode().isEmpty()) {
                User referrer = userRepository.findByReferralCode(request.getReferralCode());
                if (referrer == null) {
                    return ResponseEntity.badRequest()
                            .body(Map.of("error", "Invalid referral code"));
                }
                referredById = referrer.getId();
            }

            // Build success and cancel URLs
            String successUrl = request.getSuccessUrl() != null 
                    ? request.getSuccessUrl() 
                    : frontendUrl + "/join/" + request.getBusinessTag() + "?success=true";
            String cancelUrl = request.getCancelUrl() != null 
                    ? request.getCancelUrl() 
                    : frontendUrl + "/join/" + request.getBusinessTag() + "?canceled=true";

            // Create Stripe Checkout Session
            String checkoutUrl = stripeService.createPublicCheckoutSession(
                    membership.getStripePriceId(),
                    business.getStripeAccountId(),
                    successUrl,
                    cancelUrl,
                    stripePromoCodeId,
                    request.getBusinessTag(),
                    request.getMembershipId(),
                    referredById
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
     * Validate a promo code or referral code
     */
    @PostMapping("/validate-code")
    @Transactional(readOnly = true)
    public ResponseEntity<?> validateCode(@RequestBody CodeValidationRequest request) {
        try {
            if (request.getBusinessTag() == null || request.getBusinessTag().isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "businessTag is required"));
            }
            if (request.getCode() == null || request.getCode().isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "code is required"));
            }
            if (request.getCodeType() == null || request.getCodeType().isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "codeType is required (promo or referral)"));
            }

            // Fetch business
            Optional<Business> businessOpt = businessRepository.findByBusinessTag(request.getBusinessTag());
            if (businessOpt.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("error", "Business not found"));
            }
            Business business = businessOpt.get();

            if ("promo".equalsIgnoreCase(request.getCodeType())) {
                Optional<Promo> promoOpt = promoRepository.findByCodeToken(request.getCode());
                if (promoOpt.isEmpty()) {
                    return ResponseEntity.ok(Map.of("valid", false, "message", "Invalid promo code"));
                }
                Promo promo = promoOpt.get();
                if (!promo.getBusiness().getId().equals(business.getId())) {
                    return ResponseEntity.ok(Map.of("valid", false, "message", "Promo code does not belong to this business"));
                }
                return ResponseEntity.ok(Map.of(
                        "valid", true,
                        "discountType", promo.getDiscountType(),
                        "discountValue", promo.getDiscountValue(),
                        "message", "Promo code applied successfully"
                ));
            } else if ("referral".equalsIgnoreCase(request.getCodeType())) {
                User referrer = userRepository.findByReferralCode(request.getCode());
                if (referrer == null) {
                    return ResponseEntity.ok(Map.of("valid", false, "message", "Invalid referral code"));
                }
                return ResponseEntity.ok(Map.of(
                        "valid", true,
                        "message", "Referral code is valid"
                ));
            } else {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "codeType must be 'promo' or 'referral'"));
            }
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to validate code: " + e.getMessage()));
        }
    }

    // DTOs
    public static class CheckoutSessionRequest {
        private String businessTag;
        private Long membershipId;
        private String promoCode;
        private String referralCode;
        private String successUrl;
        private String cancelUrl;

        public String getBusinessTag() { return businessTag; }
        public void setBusinessTag(String businessTag) { this.businessTag = businessTag; }

        public Long getMembershipId() { return membershipId; }
        public void setMembershipId(Long membershipId) { this.membershipId = membershipId; }

        public String getPromoCode() { return promoCode; }
        public void setPromoCode(String promoCode) { this.promoCode = promoCode; }

        public String getReferralCode() { return referralCode; }
        public void setReferralCode(String referralCode) { this.referralCode = referralCode; }

        public String getSuccessUrl() { return successUrl; }
        public void setSuccessUrl(String successUrl) { this.successUrl = successUrl; }

        public String getCancelUrl() { return cancelUrl; }
        public void setCancelUrl(String cancelUrl) { this.cancelUrl = cancelUrl; }
    }

    public static class CodeValidationRequest {
        private String businessTag;
        private String code;
        private String codeType; // "promo" or "referral"

        public String getBusinessTag() { return businessTag; }
        public void setBusinessTag(String businessTag) { this.businessTag = businessTag; }

        public String getCode() { return code; }
        public void setCode(String code) { this.code = code; }

        public String getCodeType() { return codeType; }
        public void setCodeType(String codeType) { this.codeType = codeType; }
    }
}

