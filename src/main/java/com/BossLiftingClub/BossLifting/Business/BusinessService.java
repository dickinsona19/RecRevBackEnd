package com.BossLiftingClub.BossLifting.Business;


import com.stripe.exception.StripeException;

import java.util.List;

public interface BusinessService {
    BusinessDTO createBusiness(BusinessDTO businessDTO);
    BusinessDTO getBusinessById(Long id);
    BusinessDTO getBusinessByTag(String businessTag);
//    List<BusinessDTO> getAllBusinesses();
    BusinessDTO updateBusiness(Long id, BusinessDTO businessDTO);
    void deleteBusiness(long id);

    String createStripeOnboardingLink(String businessTag, String returnUrl, String refreshUrl) throws StripeException;
    String createStripeDashboardLink(String businessTag) throws StripeException;
    void updateOnboardingStatus(String stripeAccountId, String status);
    void checkAndUpdateOnboardingStatus(String businessTag) throws StripeException;
    java.util.Optional<BusinessDTO> findByStripeAccountId(String stripeAccountId);
}





