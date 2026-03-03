package com.BossLiftingClub.BossLifting.Business;

import com.BossLiftingClub.BossLifting.Client.Client;
import com.BossLiftingClub.BossLifting.Client.ClientRepository;
import com.BossLiftingClub.BossLifting.Business.Staff.Staff;
import com.BossLiftingClub.BossLifting.Business.Staff.StaffRepository;
import jakarta.persistence.EntityNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
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
}

