package com.BossLiftingClub.BossLifting.User.Membership;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface MembershipRepository extends JpaRepository<Membership, Long> {
    List<Membership> findByClubTag(String clubTag);
}