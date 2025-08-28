package com.BossLiftingClub.BossLifting.Club.Staff;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class StaffServiceImpl implements StaffService {
    @Autowired
    private StaffRepository staffRepository;

    @Override
    public StaffDTO createStaff(StaffDTO staffDTO) {
        Staff staff = new Staff();
        staff.setEmail(staffDTO.getEmail());
        staff.setPassword(staffDTO.getPassword());
        staff.setType(staffDTO.getType());
        Staff savedStaff = staffRepository.save(staff);
        return mapToDTO(savedStaff);
    }

    @Override
    public StaffDTO getStaffById(Integer id) {
        Staff staff = staffRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Staff not found"));
        return mapToDTO(staff);
    }

    @Override
    public List<StaffDTO> getAllStaff() {
        return staffRepository.findAll().stream()
                .map(this::mapToDTO)
                .collect(Collectors.toList());
    }

    @Override
    public StaffDTO updateStaff(Integer id, StaffDTO staffDTO) {
        Staff staff = staffRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Staff not found"));
        staff.setEmail(staffDTO.getEmail());
        staff.setPassword(staffDTO.getPassword());
        staff.setType(staffDTO.getType());
        Staff updatedStaff = staffRepository.save(staff);
        return mapToDTO(updatedStaff);
    }

    @Override
    public void deleteStaff(Integer id) {
        staffRepository.deleteById(id);
    }

    private StaffDTO mapToDTO(Staff staff) {
        StaffDTO dto = new StaffDTO();
        dto.setId(staff.getId());
        dto.setEmail(staff.getEmail());
        dto.setPassword(staff.getPassword());
        dto.setType(staff.getType());
        return dto;
    }
}