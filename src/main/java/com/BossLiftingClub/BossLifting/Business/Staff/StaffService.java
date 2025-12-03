package com.BossLiftingClub.BossLifting.Business.Staff;

import java.util.List;

public interface StaffService {
    StaffDTO createStaff(StaffDTO staffDTO);
    StaffDTO getStaffById(Integer id);
    List<StaffDTO> getAllStaff();
    StaffDTO updateStaff(Integer id, StaffDTO staffDTO);
    void deleteStaff(Integer id);
    
    // New methods for staff system
    StaffDTO inviteStaff(String email, String role, Long businessId, Integer invitedBy);
    StaffDTO acceptInvite(String inviteToken, String password);
    StaffDTO login(String email, String password);
    List<StaffDTO> getStaffByBusiness(Long businessId);
    List<StaffDTO> getStaffByBusinessTag(String businessTag);
    StaffDTO updateStaffRole(Integer staffId, String role);
    void activateStaff(Integer staffId);
    void deactivateStaff(Integer staffId);
}
