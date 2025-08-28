package com.BossLiftingClub.BossLifting.Club.Staff;

import java.util.List;

public interface StaffService {
    StaffDTO createStaff(StaffDTO staffDTO);
    StaffDTO getStaffById(Integer id);
    List<StaffDTO> getAllStaff();
    StaffDTO updateStaff(Integer id, StaffDTO staffDTO);
    void deleteStaff(Integer id);
}