package com.BossLiftingClub.BossLifting.User.Membership;


import com.BossLiftingClub.BossLifting.Club.Club;
import com.BossLiftingClub.BossLifting.Club.ClubRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class MembershipServiceImpl implements MembershipService {
    @Autowired
    private MembershipRepository membershipRepository;

    @Autowired
    private ClubRepository clubRepository;

    @Override
    public MembershipDTO createMembership(MembershipDTO membershipDTO) {
        Membership membership = new Membership();
        membership.setTitle(membershipDTO.getTitle());
        membership.setPrice(membershipDTO.getPrice());
        membership.setChargeInterval(membershipDTO.getChargeInterval());

        if (membershipDTO.getClubTag() != null) {
            Club club = clubRepository.findByClubTag(membershipDTO.getClubTag())
                    .orElseThrow(() -> new RuntimeException("Club not found for clubTag: " + membershipDTO.getClubTag()));
            membership.setClub(club);
        } else {
            throw new IllegalArgumentException("clubTag must not be null");
        }


        Membership savedMembership = membershipRepository.save(membership);
        return mapToDTO(savedMembership);
    }

    @Override
    public MembershipDTO getMembershipById(Long id) {
        Membership membership = membershipRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("membership not found"));
        return mapToDTO(membership);
    }

    @Override
    public List<MembershipDTO> getAllMemberships() {
        return membershipRepository.findAll().stream()
                .map(this::mapToDTO)
                .collect(Collectors.toList());
    }

    @Override
    public MembershipDTO updateMembership(Long id, MembershipDTO membershipDTO) {
        Membership membership = membershipRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("membership not found"));
        membership.setTitle(membershipDTO.getTitle());
        membership.setPrice(membershipDTO.getPrice());
        membership.setChargeInterval(membershipDTO.getChargeInterval());


        Membership updatedMembership = membershipRepository.save(membership);
        return mapToDTO(updatedMembership);
    }

    @Override
    public void deleteMembership(Long id) {
        membershipRepository.deleteById(id);
    }

    private MembershipDTO mapToDTO(Membership membership) {
        MembershipDTO dto = new MembershipDTO();
        dto.setId(membership.getId());
        dto.setTitle(membership.getTitle());
        dto.setPrice(membership.getPrice());
        dto.setChargeInterval(membership.getChargeInterval());
        dto.setClubTag(membership.getClub() != null ? membership.getClub().getClubTag() : null);
        return dto;
    }

}