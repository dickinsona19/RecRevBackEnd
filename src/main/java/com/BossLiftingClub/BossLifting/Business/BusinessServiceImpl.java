package com.BossLiftingClub.BossLifting.Business;

import com.BossLiftingClub.BossLifting.Client.Client;
import com.BossLiftingClub.BossLifting.Client.ClientRepository;
import com.BossLiftingClub.BossLifting.Business.Staff.Staff;
import com.BossLiftingClub.BossLifting.Business.Staff.StaffRepository;
import com.stripe.exception.StripeException;
import com.stripe.model.Account;
import com.stripe.model.AccountLink;
import com.stripe.model.LoginLink;
import com.stripe.param.AccountCreateParams;
import com.stripe.param.AccountLinkCreateParams;
import jakarta.persistence.EntityNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Map;
import java.util.UUID;

@Service
public class BusinessServiceImpl implements BusinessService {
    private static final Logger logger = LoggerFactory.getLogger(BusinessServiceImpl.class);

    @Autowired
    private BusinessRepository businessRepository;

    @Autowired
    private ClientRepository clientRepository;

    @Autowired
    private StaffRepository staffRepository;

    @Override
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public BusinessDTO createBusiness(BusinessDTO businessDTO) {
        logger.info("Creating business with businessTag: {}", businessDTO.getBusinessTag());

        // Validate and generate business_tag if necessary
        String businessTag = businessDTO.getBusinessTag();
        if (businessTag == null || businessTag.isBlank()) {
            logger.info("No businessTag provided, generating a unique one");
            businessTag = generateUniqueBusinessTag();
            businessDTO.setBusinessTag(businessTag);
        } else {
            logger.debug("Checking if businessTag '{}' exists", businessTag);
            if (businessRepository.findByBusinessTagWithLock(businessTag).isPresent()) {
                logger.warn("Duplicate businessTag detected: {}", businessTag);
                throw new IllegalArgumentException("Business tag '" + businessTag + "' already exists");
            }
        }

        Business business = new Business();
        business.setTitle(businessDTO.getTitle());
        business.setLogoUrl(businessDTO.getLogoUrl());
        business.setStatus(businessDTO.getStatus());
        business.setCreatedAt(LocalDateTime.now());
        business.setBusinessTag(businessTag);
        business.setContactEmail(businessDTO.getContactEmail());
        // Set referral program settings (use defaults if not provided)
        business.setReferredUserDiscountMonths(businessDTO.getReferredUserDiscountMonths() != null ? businessDTO.getReferredUserDiscountMonths() : 1);
        business.setReferredUserWaiveActivationFee(businessDTO.getReferredUserWaiveActivationFee() != null ? businessDTO.getReferredUserWaiveActivationFee() : true);
        business.setReferrerDiscountMonths(businessDTO.getReferrerDiscountMonths() != null ? businessDTO.getReferrerDiscountMonths() : 1);
        business.setReferrerWaiveActivationFee(businessDTO.getReferrerWaiveActivationFee() != null ? businessDTO.getReferrerWaiveActivationFee() : true);

        if (businessDTO.getClientId() != null) {
            Client client = clientRepository.findById(businessDTO.getClientId())
                    .orElseThrow(() -> {
                        logger.error("Client not found with ID: {}", businessDTO.getClientId());
                        return new EntityNotFoundException("Client not found with ID: " + businessDTO.getClientId());
                    });
            business.setClient(client);
            // Add the business to the client's businesses list
            if (client.getBusinesses() == null) {
                client.setBusinesses(new ArrayList<>());
            }
            client.getBusinesses().add(business);
            logger.info("Associating business with client ID: {}", businessDTO.getClientId());
        }

        if (businessDTO.getStaffId() != null) {
            Staff staff = staffRepository.findById(businessDTO.getStaffId())
                    .orElseThrow(() -> {
                        logger.error("Staff not found with ID: {}", businessDTO.getStaffId());
                        return new EntityNotFoundException("Staff not found with ID: " + businessDTO.getStaffId());
                    });
            business.setStaff(staff);
        }

        logger.info("Saving business with businessTag: {}", businessTag);
        try {
            Business savedBusiness = businessRepository.save(business);
            logger.info("Business saved successfully with ID: {}", savedBusiness.getId());
            return BusinessDTO.mapToBusinessDTO(savedBusiness);
        } catch (Exception e) {
            logger.error("Failed to save business with businessTag: {}. Error: {}", businessTag, e.getMessage());
            throw e;
        }
    }

    @Override
    @Transactional(readOnly = true)
    public BusinessDTO getBusinessById(Long id) {
        logger.info("Getting business with ID: {}", id);
        Business business = businessRepository.findById(id)
                .orElseThrow(() -> {
                    logger.error("Business not found with ID: {}", id);
                    return new EntityNotFoundException("Business not found with ID: " + id);
                });
        return BusinessDTO.mapToBusinessDTO(business);
    }

    @Override
    @Transactional(readOnly = true)
    public BusinessDTO getBusinessByTag(String businessTag) {
        logger.info("Getting business with tag: {}", businessTag);
        Business business = businessRepository.findByBusinessTag(businessTag)
                .orElseThrow(() -> {
                    logger.error("Business not found with tag: {}", businessTag);
                    return new EntityNotFoundException("Business not found with tag: " + businessTag);
                });
        return BusinessDTO.mapToBusinessDTO(business);
    }

    @Override
    @Transactional
    public BusinessDTO updateBusiness(Long id, BusinessDTO businessDTO) {
        logger.info("Updating business with ID: {}", id);
        Business business = businessRepository.findById(id)
                .orElseThrow(() -> {
                    logger.error("Business not found with ID: {}", id);
                    return new EntityNotFoundException("Business not found with ID: " + id);
                });

        // Update fields
        if (businessDTO.getTitle() != null) {
            business.setTitle(businessDTO.getTitle());
        }
        if (businessDTO.getLogoUrl() != null) {
            business.setLogoUrl(businessDTO.getLogoUrl());
        }
        if (businessDTO.getStatus() != null) {
            business.setStatus(businessDTO.getStatus());
        }
        if (businessDTO.getContactEmail() != null) {
            business.setContactEmail(businessDTO.getContactEmail());
        }
        // Update referral program settings
        if (businessDTO.getReferredUserDiscountMonths() != null) {
            business.setReferredUserDiscountMonths(businessDTO.getReferredUserDiscountMonths());
        }
        if (businessDTO.getReferredUserWaiveActivationFee() != null) {
            business.setReferredUserWaiveActivationFee(businessDTO.getReferredUserWaiveActivationFee());
        }
        if (businessDTO.getReferrerDiscountMonths() != null) {
            business.setReferrerDiscountMonths(businessDTO.getReferrerDiscountMonths());
        }
        if (businessDTO.getReferrerWaiveActivationFee() != null) {
            business.setReferrerWaiveActivationFee(businessDTO.getReferrerWaiveActivationFee());
        }

        Business savedBusiness = businessRepository.save(business);
        logger.info("Business updated successfully with ID: {}", id);
        return BusinessDTO.mapToBusinessDTO(savedBusiness);
    }

    @Override
    @Transactional
    public void deleteBusiness(long id) {
        logger.info("Deleting business with ID: {}", id);
        Business business = businessRepository.findById(id)
                .orElseThrow(() -> {
                    logger.error("Business not found with ID: {}", id);
                    return new EntityNotFoundException("Business not found with ID: " + id);
                });
        if (business.getClient() != null) {
            // Remove the business from the client's businesses list
            Client client = business.getClient();
            client.getBusinesses().remove(business);
            clientRepository.save(client);
        }
        businessRepository.deleteById(id);
        logger.info("Business deleted successfully with ID: {}", id);
    }

    private String generateUniqueBusinessTag() {
        String prefix = "BIZ_";
        String randomId;
        String potentialTag;
        int attempts = 0;
        final int maxAttempts = 50;

        do {
            randomId = UUID.randomUUID().toString().substring(0, 8);
            potentialTag = prefix + randomId;
            attempts++;
            logger.debug("Attempt {}: Checking generated businessTag: {}", attempts, potentialTag);
            if (attempts >= maxAttempts) {
                logger.error("Failed to generate unique businessTag after {} attempts", maxAttempts);
                throw new IllegalStateException("Unable to generate a unique business tag after " + maxAttempts + " attempts");
            }
        } while (businessRepository.findByBusinessTagWithLock(potentialTag).isPresent());

        logger.info("Generated unique businessTag: {}", potentialTag);
        return potentialTag;
    }

    @Override
    @Transactional
    public String createStripeOnboardingLink(String businessTag, String returnUrl, String refreshUrl) throws StripeException {
        logger.info("Creating Stripe onboarding link for business: {}", businessTag);

        Business business = businessRepository.findByBusinessTag(businessTag)
                .orElseThrow(() -> {
                    logger.error("Business not found with businessTag: {}", businessTag);
                    return new EntityNotFoundException("Business not found with businessTag: " + businessTag);
                });

        String stripeAccountId = business.getStripeAccountId();

        // If business doesn't have a Stripe account yet, create one
        if (stripeAccountId == null || stripeAccountId.isEmpty()) {
            logger.info("Creating new Stripe connected account for business: {}", businessTag);
            AccountCreateParams accountParams = AccountCreateParams.builder()
                    .setType(AccountCreateParams.Type.EXPRESS)
                    .build();

            Account account = Account.create(accountParams);
            stripeAccountId = account.getId();

            // Save the Stripe account ID to the business
            business.setStripeAccountId(stripeAccountId);
            business.setOnboardingStatus("PENDING");
            businessRepository.save(business);
            logger.info("Stripe account created: {} for business: {}", stripeAccountId, businessTag);
        }

        // Create the account link for onboarding
        AccountLinkCreateParams linkParams = AccountLinkCreateParams.builder()
                .setAccount(stripeAccountId)
                .setRefreshUrl(refreshUrl)
                .setReturnUrl(returnUrl)
                .setType(AccountLinkCreateParams.Type.ACCOUNT_ONBOARDING)
                .build();

        AccountLink accountLink = AccountLink.create(linkParams);
        logger.info("Onboarding link created successfully for business: {}", businessTag);

        return accountLink.getUrl();
    }

    @Override
    @Transactional(readOnly = true)
    public String createStripeDashboardLink(String businessTag) throws StripeException {
        logger.info("Creating Stripe dashboard link for business: {}", businessTag);

        Business business = businessRepository.findByBusinessTag(businessTag)
                .orElseThrow(() -> {
                    logger.error("Business not found with businessTag: {}", businessTag);
                    return new EntityNotFoundException("Business not found with businessTag: " + businessTag);
                });

        String stripeAccountId = business.getStripeAccountId();

        if (stripeAccountId == null || stripeAccountId.isEmpty()) {
            logger.error("Business {} does not have a Stripe account configured", businessTag);
            throw new IllegalStateException("Business does not have Stripe configured. Please complete onboarding first.");
        }

        if (!"COMPLETED".equals(business.getOnboardingStatus())) {
            logger.error("Business {} has not completed Stripe onboarding. Status: {}", businessTag, business.getOnboardingStatus());
            throw new IllegalStateException("Business has not completed Stripe onboarding. Please complete the setup first.");
        }

        // Create login link for Express Dashboard
        LoginLink loginLink = LoginLink.createOnAccount(
                stripeAccountId,
                (Map<String, Object>) null
        );

        logger.info("Dashboard link created successfully for business: {}", businessTag);
        return loginLink.getUrl();
    }

    @Override
    @Transactional
    public void updateOnboardingStatus(String stripeAccountId, String status) {
        logger.info("Updating onboarding status for Stripe account: {} to {}", stripeAccountId, status);

        Business business = businessRepository.findByStripeAccountId(stripeAccountId)
                .orElseThrow(() -> {
                    logger.error("Business not found with Stripe account ID: {}", stripeAccountId);
                    return new EntityNotFoundException("Business not found with Stripe account ID: " + stripeAccountId);
                });

        business.setOnboardingStatus(status);
        businessRepository.save(business);
        logger.info("Onboarding status updated successfully for business: {}", business.getBusinessTag());
    }
    
    @Override
    @Transactional
    public void checkAndUpdateOnboardingStatus(String businessTag) throws StripeException {
        logger.info("Checking onboarding status for business: {}", businessTag);
        
        Business business = businessRepository.findByBusinessTag(businessTag)
                .orElseThrow(() -> {
                    logger.error("Business not found with businessTag: {}", businessTag);
                    return new EntityNotFoundException("Business not found with businessTag: " + businessTag);
                });
        
        String stripeAccountId = business.getStripeAccountId();
        if (stripeAccountId == null || stripeAccountId.isEmpty()) {
            logger.warn("Business {} does not have a Stripe account ID", businessTag);
            return;
        }
        
        try {
            // Query Stripe directly to get current account status
            com.stripe.model.Account account = com.stripe.model.Account.retrieve(stripeAccountId);
            
            boolean chargesEnabled = account.getChargesEnabled();
            boolean detailsSubmitted = account.getDetailsSubmitted();
            
            String onboardingStatus;
            if (chargesEnabled && detailsSubmitted) {
                onboardingStatus = "COMPLETED";
                logger.info("Stripe account {} has completed onboarding (chargesEnabled: {}, detailsSubmitted: {})", 
                        stripeAccountId, chargesEnabled, detailsSubmitted);
            } else if (detailsSubmitted) {
                onboardingStatus = "PENDING";
                logger.info("Stripe account {} has submitted details but is pending review", stripeAccountId);
            } else if (account.getRequirements() != null && !account.getRequirements().getCurrentlyDue().isEmpty()) {
                onboardingStatus = "RESTRICTED";
                logger.info("Stripe account {} has requirements currently due", stripeAccountId);
            } else {
                onboardingStatus = "PENDING";
                logger.info("Stripe account {} is still pending onboarding", stripeAccountId);
            }
            
            // Update the business status
            business.setOnboardingStatus(onboardingStatus);
            businessRepository.save(business);
            logger.info("Updated onboarding status for business {} to {}", businessTag, onboardingStatus);
            
        } catch (StripeException e) {
            logger.error("Error checking Stripe account status for business {}: {}", businessTag, e.getMessage());
            throw e;
        }
    }

    @Override
    @Transactional(readOnly = true)
    public java.util.Optional<BusinessDTO> findByStripeAccountId(String stripeAccountId) {
        return businessRepository.findByStripeAccountId(stripeAccountId)
                .map(BusinessDTO::mapToBusinessDTO);
    }
}

