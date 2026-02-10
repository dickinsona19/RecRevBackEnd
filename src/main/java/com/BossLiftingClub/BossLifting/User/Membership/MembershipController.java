package com.BossLiftingClub.BossLifting.User.Membership;

import com.BossLiftingClub.BossLifting.Email.EmailService;
import com.BossLiftingClub.BossLifting.User.BusinessUser.UserBusiness;
import com.BossLiftingClub.BossLifting.User.BusinessUser.UserBusinessMembership;
import com.BossLiftingClub.BossLifting.User.BusinessUser.UserBusinessRepository;
import com.BossLiftingClub.BossLifting.User.BusinessUser.UserBusinessService;
import com.BossLiftingClub.BossLifting.Stripe.StripeService;
import com.BossLiftingClub.BossLifting.Business.Business;
import com.BossLiftingClub.BossLifting.Promo.Promo;
import com.BossLiftingClub.BossLifting.Promo.PromoRepository;
import com.BossLiftingClub.BossLifting.User.Membership.PackageRepository;
import com.BossLiftingClub.BossLifting.User.Membership.MembershipPackage;
import com.stripe.exception.StripeException;
import java.math.BigDecimal;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/memberships")
public class MembershipController {
    @Autowired
    private MembershipService membershipService;

    @Autowired
    private MembershipRepository membershipRepository;

    @Autowired
    private UserBusinessRepository userBusinessRepository;

    @Autowired
    private EmailService emailService;

    @Autowired
    private StripeService stripeService;

    @Autowired
    private UserBusinessService userBusinessService;

    @Autowired
    private PromoRepository promoRepository;

    @Autowired
    private PackageRepository packageRepository;

    @GetMapping("/business/{businessTag}")
    @Transactional(readOnly = true)
    public ResponseEntity<?> getMembershipsByBusinessTag(@PathVariable String businessTag) {
        try {
            List<MembershipDTO> memberships = membershipService.getMembershipsByBusinessTag(businessTag);
            return ResponseEntity.ok(memberships);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to retrieve memberships: " + e.getMessage()));
        }
    }

    // ✅ Add a new membership
    @PostMapping
    public ResponseEntity<?> addMembership(@Valid @RequestBody MembershipDTO membershipDTO) {
        try {
            Membership membership = membershipDTO.toEntity();
            Membership saved = membershipService.createMembershipWithStripe(membership);
            return ResponseEntity.status(HttpStatus.CREATED).body(saved);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to create membership: " + e.getMessage()));
        }
    }

    // ✅ Update a membership
    @PutMapping("/{id}")
    public ResponseEntity<?> updateMembership(@PathVariable Long id, @Valid @RequestBody MembershipDTO membershipDTO) {
        try {
            Optional<Membership> membershipOpt = membershipRepository.findById(id);
            if (membershipOpt.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("error", "Membership not found with id: " + id));
            }
            
            Membership membership = membershipOpt.get();
            membership.setTitle(membershipDTO.getTitle());
            membership.setPrice(membershipDTO.getPrice());
            membership.setChargeInterval(membershipDTO.getChargeInterval());
            membership.setPublic(membershipDTO.isPublic());
            membership.setPublicDisplayName(membershipDTO.getPublicDisplayName());
            membership.setPublicDescription(membershipDTO.getPublicDescription());
            membership.setPublicBenefits(membershipDTO.getPublicBenefits());
            membership.setMembershipType(membershipDTO.getMembershipType() != null ? membershipDTO.getMembershipType() : "UNLIMITED");
            membership.setPunchCount(membershipDTO.getPunchCount());
            membership.setExpiryDays(membershipDTO.getExpiryDays());
            membership.setOneOffType(membershipDTO.getOneOffType());
            membership.setProcessingFee(membershipDTO.getProcessingFee());
            
            Membership updated = membershipRepository.save(membership);
            return ResponseEntity.ok(MembershipDTO.fromEntity(updated));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to update membership: " + e.getMessage()));
        }
    }

    // ✅ Archive (soft delete) a membership
    @PutMapping("/{id}/archive")
    public ResponseEntity<?> archiveMembership(@PathVariable Long id) {
        try {
            Membership archived = membershipService.archiveMembership(id);
            return ResponseEntity.ok(archived);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "Failed to archive membership: " + e.getMessage()));
        }
    }

    // ✅ Get membership by ID
    @GetMapping("/{id}")
    @Transactional(readOnly = true)
    public ResponseEntity<?> getMembershipById(@PathVariable Long id) {
        try {
            Optional<Membership> membership = membershipRepository.findById(id);
            if (membership.isPresent()) {
                return ResponseEntity.ok(membership.get());
            } else {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("error", "Membership not found with id: " + id));
            }
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to retrieve membership: " + e.getMessage()));
        }
    }

    // ✅ Save membership signature and activate membership
    @PostMapping("/sign")
    @Transactional
    public ResponseEntity<?> signMembership(@RequestBody MembershipSignatureRequest request) {
        Long userId = request.getUserId();
        Long userBusinessId = request.getUserBusinessId();
        Long membershipId = request.getMembershipId();
        String signatureDataUrl = request.getSignatureDataUrl();
        String signerName = request.getSignerName();
        String promoCode = request.getPromoCode();
        Long packageId = request.getPackageId();
        Boolean chargeApplicationFee = request.getChargeApplicationFee();
        Long referredById = request.getReferredById();

        if (userId == null || userBusinessId == null || membershipId == null) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "userId, userBusinessId, and membershipId are required"));
        }

        if (signatureDataUrl == null || signatureDataUrl.isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "signatureDataUrl is required"));
        }

        // Find the UserBusinessMembership by userBusinessId and membershipId
        UserBusiness userBusiness = userBusinessRepository.findById(userBusinessId)
                .orElse(null);
        if (userBusiness == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "UserBusiness not found with id: " + userBusinessId));
        }

        Optional<UserBusinessMembership> membershipOpt = userBusiness.getUserBusinessMemberships().stream()
                .filter(ubm -> ubm.getMembership().getId().equals(membershipId))
                .findFirst();

        UserBusinessMembership membership;
        boolean isNewMembership = false;

        if (membershipOpt.isEmpty()) {
            // Membership doesn't exist yet, create it with PENDING status
            // This happens when admin adds membership but user hasn't signed yet
            Membership membershipPlan = membershipRepository.findById(membershipId).orElse(null);
            if (membershipPlan == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("error", "Membership plan not found with id: " + membershipId));
            }

            membership = new UserBusinessMembership();
            membership.setUserBusiness(userBusiness);
            membership.setMembership(membershipPlan);
            membership.setStatus("PENDING"); // Start as PENDING until signature is submitted
            membership.setAnchorDate(LocalDateTime.now());
            
            // Set price from membership plan
            if (membershipPlan.getPrice() != null) {
                try {
                    BigDecimal actualPrice = new BigDecimal(membershipPlan.getPrice());
                    membership.setActualPrice(actualPrice);
                } catch (NumberFormatException e) {
                    // If price is invalid, leave it null
                }
            }
            
            userBusiness.addMembership(membership);
            isNewMembership = true;
        } else {
            membership = membershipOpt.get();
        }

        // Save the signature
        membership.setSignatureDataUrl(signatureDataUrl);
        membership.setSignedAt(LocalDateTime.now());
        if (signerName != null && !signerName.isEmpty()) {
            membership.setSignerName(signerName);
        }

        // If membership was PENDING (or just created), activate it and create Stripe subscription
        if ("PENDING".equalsIgnoreCase(membership.getStatus()) || isNewMembership) {
            // Check if user has a payment method
            String stripeCustomerId = userBusiness.getStripeId();
            if (stripeCustomerId == null || stripeCustomerId.isEmpty()) {
                // Save the membership with signature but keep it PENDING
                // User will need to add payment method later
                userBusinessRepository.save(userBusiness);
                userBusinessService.calculateAndUpdateStatus(userBusiness);
                return ResponseEntity.ok(Map.of(
                    "message", "Membership signature saved successfully. Please add a payment method to activate the membership.",
                    "status", "PENDING"
                ));
            }

            // Verify payment method exists
            Business business = userBusiness.getBusiness();
            String stripeAccountId = business.getStripeAccountId();
            
            try {
                boolean hasPaymentMethod = stripeService.hasDefaultPaymentMethod(stripeCustomerId, stripeAccountId);
                if (!hasPaymentMethod) {
                    // Save the membership with signature but keep it PENDING
                    userBusinessRepository.save(userBusiness);
                    userBusinessService.calculateAndUpdateStatus(userBusiness);
                    return ResponseEntity.ok(Map.of(
                        "message", "Membership signature saved successfully. Please add a payment method to activate the membership.",
                        "status", "PENDING"
                    ));
                }
            } catch (StripeException e) {
                // If we can't verify payment method, save signature but keep PENDING
                userBusinessRepository.save(userBusiness);
                userBusinessService.calculateAndUpdateStatus(userBusiness);
                return ResponseEntity.ok(Map.of(
                    "message", "Membership signature saved successfully. Payment verification failed. Please contact support.",
                    "status", "PENDING"
                ));
            }

            // Create Stripe subscription
            String stripePriceId = membership.getMembership().getStripePriceId();
            if (stripePriceId != null && !stripePriceId.isEmpty()) {
                try {
                    BigDecimal actualPrice = membership.getActualPrice() != null 
                        ? membership.getActualPrice() 
                        : new BigDecimal(membership.getMembership().getPrice());
                    
                    // For one-offs, determine the correct billing interval from oneOffType
                    // This ensures the subscription uses the right interval (week, month, etc.)
                    String membershipType = membership.getMembership().getMembershipType();
                    String oneOffType = membership.getMembership().getOneOffType();
                    String billingInterval = null; // null means use the Stripe Price's default interval
                    
                    if ("ONE_OFF".equalsIgnoreCase(membershipType) && oneOffType != null && !oneOffType.isEmpty()) {
                        // Parse oneOffType (e.g., "1_WEEK", "2_MONTH", "WEEK_PASS", "MONTH_PASS")
                        String upperType = oneOffType.toUpperCase();
                        if (upperType.contains("_WEEK") || upperType.equals("WEEK_PASS")) {
                            billingInterval = "week";
                        } else if (upperType.contains("_MONTH") || upperType.equals("MONTH_PASS")) {
                            billingInterval = "month";
                        } else if (upperType.contains("_DAY")) {
                            billingInterval = "day";
                        } else if (upperType.contains("_YEAR")) {
                            billingInterval = "year";
                        }
                    }
                    
                    com.stripe.model.Subscription subscription = stripeService.createSubscription(
                        stripeCustomerId,
                        stripePriceId,
                        stripeAccountId,
                        membership.getAnchorDate() != null ? membership.getAnchorDate() : LocalDateTime.now(),
                        promoCode, // promoCode (optional)
                        actualPrice,
                        billingInterval // Pass the interval for one-offs
                    );

                    // Charge application fee (one-time) if configured and not waived
                    boolean shouldChargeFee = Boolean.TRUE.equals(chargeApplicationFee);
                    if (shouldChargeFee) {
                        BigDecimal feeToCharge = null;
                        String feeDescription = null;

                        if (packageId != null) {
                            MembershipPackage pkg = packageRepository.findById(packageId).orElse(null);
                            if (pkg != null) {
                                feeToCharge = pkg.getProcessingFee();
                                feeDescription = "Application fee - Package: " + pkg.getName();
                            }
                        } else {
                            feeToCharge = membership.getMembership().getProcessingFee();
                            feeDescription = "Application fee - Membership: " + membership.getMembership().getTitle();
                        }

                        boolean waived = false;

                        // Waive for promo only if promo has waiveApplicationFee=true
                        if (promoCode != null && !promoCode.trim().isEmpty()) {
                            Promo promo = promoRepository.findByCodeToken(promoCode.trim()).orElse(null);
                            if (promo != null && Boolean.TRUE.equals(promo.getWaiveApplicationFee())) {
                                waived = true;
                            }
                        }

                        // Waive for referrals if business setting enabled and user was referred
                        if (!waived) {
                            Long effectiveReferredById = referredById;
                            if (effectiveReferredById == null && userBusiness.getUser() != null && userBusiness.getUser().getReferredBy() != null) {
                                effectiveReferredById = userBusiness.getUser().getReferredBy().getId();
                            }
                            if (effectiveReferredById != null && Boolean.TRUE.equals(business.getReferredUserWaiveActivationFee())) {
                                waived = true;
                            }
                        }

                        if (!waived && feeToCharge != null && feeToCharge.compareTo(BigDecimal.ZERO) > 0) {
                            try {
                                stripeService.chargeApplicationFee(stripeCustomerId, feeToCharge, stripeAccountId, feeDescription);
                                membership.setProcessingFeePaid(feeToCharge);
                            } catch (StripeException feeEx) {
                                // Fee failed - don't block subscription activation, but log it
                                System.err.println("Warning: Failed to charge application fee: " + feeEx.getMessage());
                            }
                        } else {
                            membership.setProcessingFeePaid(BigDecimal.ZERO);
                        }
                    }
                    
                    // For ONE_OFF memberships, calculate the end date and cancel at that specific date
                    // This ensures the subscription auto-cancels after the correct duration (e.g., 2 weeks)
                    if ("ONE_OFF".equalsIgnoreCase(membershipType) && oneOffType != null && !oneOffType.isEmpty()) {
                        try {
                            // Parse duration from oneOffType (e.g., "2_WEEK" -> 2 weeks)
                            int duration = 1; // default to 1 period
                            String upperType = oneOffType.toUpperCase();
                            
                            // Extract number from patterns like "2_WEEK", "3_MONTH", etc.
                            java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("(\\d+)");
                            java.util.regex.Matcher matcher = pattern.matcher(upperType);
                            if (matcher.find()) {
                                try {
                                    duration = Integer.parseInt(matcher.group(1));
                                } catch (NumberFormatException e) {
                                    // Keep default of 1
                                }
                            }
                            
                            // Calculate end date based on interval and duration
                            LocalDateTime startDate = membership.getAnchorDate() != null 
                                ? membership.getAnchorDate() 
                                : LocalDateTime.now();
                            LocalDateTime cancelDate = startDate;
                            
                            if (upperType.contains("_WEEK") || upperType.equals("WEEK_PASS")) {
                                cancelDate = startDate.plusWeeks(duration);
                            } else if (upperType.contains("_MONTH") || upperType.equals("MONTH_PASS")) {
                                cancelDate = startDate.plusMonths(duration);
                            } else if (upperType.contains("_DAY")) {
                                cancelDate = startDate.plusDays(duration);
                            } else if (upperType.contains("_YEAR")) {
                                cancelDate = startDate.plusYears(duration);
                            } else {
                                // Fallback: use billing interval from subscription
                                cancelDate = startDate.plusWeeks(duration); // default to weeks
                            }
                            
                            stripeService.cancelSubscriptionAtDate(subscription.getId(), cancelDate, stripeAccountId);
                            System.out.println("One-off membership subscription set to cancel at: " + cancelDate + " (duration: " + duration + " periods)");
                        } catch (StripeException cancelException) {
                            // Log but don't fail - subscription is created, just won't auto-cancel
                            System.err.println("Warning: Failed to set cancel_at for one-off subscription: " + cancelException.getMessage());
                        } catch (Exception e) {
                            // Log parsing errors but don't fail
                            System.err.println("Warning: Failed to parse one-off duration, using default: " + e.getMessage());
                            try {
                                // Fallback to cancel at period end
                                stripeService.cancelSubscriptionAtPeriodEnd(subscription.getId(), stripeAccountId);
                            } catch (StripeException ex) {
                                System.err.println("Warning: Failed to set cancel_at_period_end: " + ex.getMessage());
                            }
                        }
                    }
                    
                    membership.setStripeSubscriptionId(subscription.getId());
                    membership.setStatus("ACTIVE"); // Activate the membership
                } catch (StripeException e) {
                    // If subscription creation fails, save signature but keep PENDING
                    userBusinessRepository.save(userBusiness);
                    userBusinessService.calculateAndUpdateStatus(userBusiness);
                    System.err.println("Failed to create subscription: " + e.getMessage());
                    e.printStackTrace();
                    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body(Map.of("error", "Failed to create subscription. Signature saved but membership is pending activation. Error: " + e.getMessage()));
                }
            } else {
                // No Stripe price ID, activate without subscription
                membership.setStatus("ACTIVE");
            }
        }

        userBusinessRepository.save(userBusiness);
        // Recalculate status after adding/updating membership
        userBusinessService.calculateAndUpdateStatus(userBusiness);

        return ResponseEntity.ok(Map.of(
            "message", "Membership signature saved successfully",
            "status", membership.getStatus()
        ));
    }

    // ✅ Send membership signature email
    @PostMapping("/send-signature-email")
    public ResponseEntity<?> sendSignatureEmail(@RequestBody SignatureEmailRequest request) {
        try {
            Long userId = request.getUserId();
            String email = request.getEmail();
            String signatureUrl = request.getSignatureUrl();

            if (userId == null || email == null || email.isEmpty() || signatureUrl == null || signatureUrl.isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "userId, email, and signatureUrl are required"));
            }

            String subject = "Sign Your Membership Agreement";
            String htmlContent = String.format(
                    "<!DOCTYPE html>" +
                    "<html>" +
                    "<head>" +
                    "<style>" +
                    "body { font-family: Arial, sans-serif; color: #333; }" +
                    ".container { max-width: 600px; margin: 0 auto; padding: 20px; }" +
                    ".header { background-color: #f8f8f8; padding: 20px; text-align: center; }" +
                    ".content { padding: 20px; }" +
                    ".button { display: inline-block; padding: 12px 24px; background-color: #007BFF; color: white !important; text-decoration: none; border-radius: 5px; font-weight: bold; margin: 20px 0; }" +
                    ".footer { font-size: 12px; color: #777; text-align: center; margin-top: 20px; }" +
                    "</style>" +
                    "</head>" +
                    "<body>" +
                    "<div class=\"container\">" +
                    "<div class=\"header\">" +
                    "<h2>Membership Agreement Signature Required</h2>" +
                    "</div>" +
                    "<div class=\"content\">" +
                    "<p>Hello,</p>" +
                    "<p>Please click the button below to sign your membership agreement:</p>" +
                    "<p style=\"text-align: center;\"><a href=\"%s\" class=\"button\">Sign Membership Agreement</a></p>" +
                    "<p>If the button doesn't work, copy and paste this link into your browser:</p>" +
                    "<p style=\"word-break: break-all; color: #007BFF;\">%s</p>" +
                    "<p>Thank you!</p>" +
                    "</div>" +
                    "<div class=\"footer\">" +
                    "<p>This is an automated message. Please do not reply to this email.</p>" +
                    "</div>" +
                    "</div>" +
                    "</body>" +
                    "</html>",
                    signatureUrl, signatureUrl
            );

            // Get business contact email from userBusiness if available
            String contactEmail = "contact@cltliftingclub.com"; // Default fallback
            String businessName = "CLT Lifting Club"; // Default fallback
            try {
                List<UserBusiness> userBusinesses = userBusinessRepository.findAll().stream()
                    .filter(ub -> ub.getUser() != null && ub.getUser().getId().equals(userId))
                    .limit(1)
                    .collect(java.util.stream.Collectors.toList());
                if (!userBusinesses.isEmpty()) {
                    UserBusiness userBusiness = userBusinesses.get(0);
                    if (userBusiness.getBusiness() != null) {
                        Business business = userBusiness.getBusiness();
                        String businessContactEmail = business.getContactEmail();
                        if (businessContactEmail != null && !businessContactEmail.isEmpty()) {
                            contactEmail = businessContactEmail;
                        }
                        if (business.getTitle() != null && !business.getTitle().isEmpty()) {
                            businessName = business.getTitle();
                        }
                    }
                }
            } catch (Exception e) {
                // Use default if we can't fetch business
            }
            
            emailService.sendBlastEmail(email, subject, htmlContent, businessName, contactEmail, contactEmail);

            return ResponseEntity.ok(Map.of("message", "Signature email sent successfully"));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to send email: " + e.getMessage()));
        }
    }

    // DTOs for request bodies
    public static class MembershipSignatureRequest {
        private Long userId;
        private Long userBusinessId;
        private Long membershipId;
        private String signatureDataUrl;
        private String signerName;
        private String promoCode;
        private Long packageId;
        private Boolean chargeApplicationFee;
        private Long referredById;

        public Long getUserId() { return userId; }
        public void setUserId(Long userId) { this.userId = userId; }

        public Long getUserBusinessId() { return userBusinessId; }
        public void setUserBusinessId(Long userBusinessId) { this.userBusinessId = userBusinessId; }

        public Long getMembershipId() { return membershipId; }
        public void setMembershipId(Long membershipId) { this.membershipId = membershipId; }

        public String getSignatureDataUrl() { return signatureDataUrl; }
        public void setSignatureDataUrl(String signatureDataUrl) { this.signatureDataUrl = signatureDataUrl; }

        public String getSignerName() { return signerName; }
        public void setSignerName(String signerName) { this.signerName = signerName; }

        public String getPromoCode() { return promoCode; }
        public void setPromoCode(String promoCode) { this.promoCode = promoCode; }

        public Long getPackageId() { return packageId; }
        public void setPackageId(Long packageId) { this.packageId = packageId; }

        public Boolean getChargeApplicationFee() { return chargeApplicationFee; }
        public void setChargeApplicationFee(Boolean chargeApplicationFee) { this.chargeApplicationFee = chargeApplicationFee; }

        public Long getReferredById() { return referredById; }
        public void setReferredById(Long referredById) { this.referredById = referredById; }
    }

    public static class SignatureEmailRequest {
        private Long userId;
        private String email;
        private String signatureUrl;

        public Long getUserId() { return userId; }
        public void setUserId(Long userId) { this.userId = userId; }

        public String getEmail() { return email; }
        public void setEmail(String email) { this.email = email; }

        public String getSignatureUrl() { return signatureUrl; }
        public void setSignatureUrl(String signatureUrl) { this.signatureUrl = signatureUrl; }
    }

    // ✅ Get public memberships by businessTag (for public join page)
    @GetMapping("/public/{businessTag}")
    @Transactional(readOnly = true)
    public ResponseEntity<?> getPublicMembershipsByBusinessTag(@PathVariable String businessTag) {
        try {
            List<Membership> publicMemberships = membershipRepository.findPublicMembershipsByBusinessTag(businessTag);
            List<MembershipDTO> dtos = publicMemberships.stream()
                    .map(MembershipDTO::fromEntity)
                    .collect(java.util.stream.Collectors.toList());
            return ResponseEntity.ok(dtos);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to retrieve public memberships: " + e.getMessage()));
        }
    }
}