package com.BossLiftingClub.BossLifting.Business;

public interface BusinessService {
    BusinessDTO createBusiness(BusinessDTO businessDTO);
    BusinessDTO getBusinessById(Long id);
    BusinessDTO getBusinessByTag(String businessTag);
    BusinessDTO updateBusiness(Long id, BusinessDTO businessDTO);
    void deleteBusiness(long id);
}





