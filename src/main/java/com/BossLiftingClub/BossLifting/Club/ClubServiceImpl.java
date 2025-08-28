package com.BossLiftingClub.BossLifting.Club;

import com.BossLiftingClub.BossLifting.Client.Client;
import com.BossLiftingClub.BossLifting.Client.ClientRepository;
import com.BossLiftingClub.BossLifting.Club.Staff.Staff;
import com.BossLiftingClub.BossLifting.Club.Staff.StaffRepository;
import com.BossLiftingClub.BossLifting.User.Membership.Membership;
import com.BossLiftingClub.BossLifting.User.Membership.MembershipDTO;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class ClubServiceImpl implements ClubService {

    @Autowired
    private ClubRepository clubRepository;

    @Autowired
    private ClientRepository clientRepository;

    @Autowired
    private StaffRepository staffRepository;

    @Override
    @Transactional
    public ClubDTO createClub(ClubDTO clubDTO) {
        Club club = new Club();
        club.setTitle(clubDTO.getTitle());
        club.setLogoUrl(clubDTO.getLogoUrl());
        club.setStatus(clubDTO.getStatus());
        club.setCreatedAt(clubDTO.getCreatedAt());
        club.setClubTag(clubDTO.getClubTag());

        if (clubDTO.getClientId() != null) {
            Client client = clientRepository.findById(clubDTO.getClientId())
                    .orElseThrow(() -> new EntityNotFoundException("Client not found with ID: " + clubDTO.getClientId()));
            club.setClient(client);
        }
        if (clubDTO.getStaffId() != null) {
            Staff staff = staffRepository.findById(clubDTO.getStaffId())
                    .orElseThrow(() -> new EntityNotFoundException("Staff not found with ID: " + clubDTO.getStaffId()));
            club.setStaff(staff);
        }

        Club savedClub = clubRepository.save(club);
        return mapToDTO(savedClub);
    }

    @Override
    @Transactional(readOnly = true)
    public ClubDTO getClubById(Integer id) {
        Club club = clubRepository.findByIdWithMemberships(id)
                .orElseThrow(() -> new EntityNotFoundException("Club not found with ID: " + id));
        return mapToDTO(club);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ClubDTO> getAllClubs() {
        return clubRepository.findAllWithMemberships().stream()
                .map(ClubDTO::mapToClubDTO)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public ClubDTO updateClub(Integer id, ClubDTO clubDTO) {
        Club club = clubRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Club not found with ID: " + id));
        club.setTitle(clubDTO.getTitle());
        club.setLogoUrl(clubDTO.getLogoUrl());
        club.setStatus(clubDTO.getStatus());
        club.setCreatedAt(clubDTO.getCreatedAt());
        club.setClubTag(clubDTO.getClubTag());

        if (clubDTO.getClientId() != null) {
            Client client = clientRepository.findById(clubDTO.getClientId())
                    .orElseThrow(() -> new EntityNotFoundException("Client not found with ID: " + clubDTO.getClientId()));
            club.setClient(client);
        } else {
            club.setClient(null);
        }
        if (clubDTO.getStaffId() != null) {
            Staff staff = staffRepository.findById(clubDTO.getStaffId())
                    .orElseThrow(() -> new EntityNotFoundException("Staff not found with ID: " + clubDTO.getStaffId()));
            club.setStaff(staff);
        } else {
            club.setStaff(null);
        }

        Club updatedClub = clubRepository.save(club);
        return mapToDTO(updatedClub);
    }

    @Override
    @Transactional
    public void deleteClub(Integer id) {
        clubRepository.deleteById(id);
    }

    private ClubDTO mapToDTO(Club club) {
        ClubDTO dto = new ClubDTO();
        dto.setId(club.getId());
        dto.setTitle(club.getTitle());
        dto.setLogoUrl(club.getLogoUrl());
        dto.setStatus(club.getStatus());
        dto.setCreatedAt(club.getCreatedAt());
        dto.setClubTag(club.getClubTag());
        dto.setClientId(club.getClient() != null ? club.getClient().getId() : null);
        dto.setStaffId(club.getStaff() != null ? club.getStaff().getId() : null);

        // Use pre-fetched memberships from JOIN FETCH
        List<Membership> membershipsCopy = club.getMemberships() != null
                ? new ArrayList<>(club.getMemberships())
                : new ArrayList<>();
        List<MembershipDTO> membershipDTOs = membershipsCopy.stream()
                .map(this::mapMembershipToDTO)
                .collect(Collectors.toList());
        dto.setMemberships(membershipDTOs);

        return dto;
    }

    private MembershipDTO mapMembershipToDTO(Membership membership) {
        MembershipDTO dto = new MembershipDTO();
        dto.setId(membership.getId());
        dto.setTitle(membership.getTitle());
        dto.setPrice(membership.getPrice());
        dto.setChargeInterval(membership.getChargeInterval());
        dto.setClubTag(membership.getClub() != null ? membership.getClub().getClubTag() : null);
        return dto;
    }
}