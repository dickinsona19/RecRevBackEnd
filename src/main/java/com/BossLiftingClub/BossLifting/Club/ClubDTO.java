package com.BossLiftingClub.BossLifting.Club;

import com.BossLiftingClub.BossLifting.Products.Products;
import com.BossLiftingClub.BossLifting.Products.ProductsDTO;
import com.BossLiftingClub.BossLifting.User.Membership.Membership;
import com.BossLiftingClub.BossLifting.User.Membership.MembershipDTO;
import com.BossLiftingClub.BossLifting.User.Membership.MembershipRepository;
import lombok.Data;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Data
public class ClubDTO {
    private Integer id;
    private String title;
    private String logoUrl;
    private String status;
    private LocalDateTime createdAt;
    private String clubTag;
    private Integer clientId;
    private Integer staffId;
    private List<MembershipDTO> memberships;

    public static ClubDTO mapToClubDTO(Club club) {
        ClubDTO dto = new ClubDTO();
        dto.setId(club.getId());
        dto.setTitle(club.getTitle());
        dto.setLogoUrl(club.getLogoUrl());
        dto.setStatus(club.getStatus());
        dto.setCreatedAt(club.getCreatedAt());
        dto.setClubTag(club.getClubTag());
        dto.setClientId(club.getClient() != null ? club.getClient().getId() : null);
        dto.setStaffId(club.getStaff() != null ? club.getStaff().getId() : null);

// Safely initialize and copy memberships to avoid ConcurrentModificationException

        Set<Membership> memberships = club.getMemberships();
        System.out.println(memberships);
        List<Membership> membershipsCopy = new ArrayList<>();
        if (memberships != null && !memberships.isEmpty()) {
            membershipsCopy.addAll(memberships); // Defensive copy
        }
        List<MembershipDTO> membershipDTOs = membershipsCopy.stream()
                .map(ClubDTO::mapMembershipToDTO)
                .collect(Collectors.toList());
        dto.setMemberships(membershipDTOs);

        // Debug logging to verify state
        System.out.println("Club " + club.getId() + " memberships size: " + (memberships != null ? memberships.size() : 0));

        return dto;
    }

    private static MembershipDTO mapMembershipToDTO(Membership membership) {
        MembershipDTO dto = new MembershipDTO();
        dto.setId(membership.getId());
        dto.setTitle(membership.getTitle());
        dto.setPrice(membership.getPrice());
        dto.setChargeInterval(membership.getChargeInterval());
        dto.setClubTag(membership.getClub() != null ? membership.getClub().getClubTag() : null);
        return dto;
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getLogoUrl() {
        return logoUrl;
    }

    public void setLogoUrl(String logoUrl) {
        this.logoUrl = logoUrl;
    }

    public Integer getClientId() {
        return clientId;
    }

    public void setClientId(Integer clientId) {
        this.clientId = clientId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }


    public String getClubTag() {
        return clubTag;
    }

    public void setClubTag(String clubTag) {
        this.clubTag = clubTag;
    }

    public Integer getStaffId() {
        return staffId;
    }

    public void setStaffId(Integer staffId) {
        this.staffId = staffId;
    }

    public List<MembershipDTO> getMemberships() {
        return memberships;
    }

    public void setMemberships(List<MembershipDTO> memberships) {
        this.memberships = memberships;
    }

}