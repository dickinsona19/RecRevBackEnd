package com.BossLiftingClub.BossLifting.User.Membership;

import java.util.List;



public interface MembershipService {
    MembershipDTO createMembership(MembershipDTO membershipDTO);
    MembershipDTO getMembershipById(Long id);
    List<MembershipDTO> getAllMemberships();
    MembershipDTO updateMembership(Long id, MembershipDTO membershipDTO);
    void deleteMembership(Long id);
}