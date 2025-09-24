package com.BossLiftingClub.BossLifting.User.Membership;

import java.util.List;

public interface MembershipService {
    List<MembershipDTO> getMembershipsByClubTag(String clubTag);
    Membership archiveMembership(Long id);
    Membership createMembershipWithStripe(Membership membership);
}