package com.BossLiftingClub.BossLifting.User.Membership;

import com.BossLiftingClub.BossLifting.User.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MembershipRepository extends JpaRepository<Membership, Long> {
    Optional<Membership> findByTitle(String Title);
    List<Membership> findByClubClubTag(String clubTag);
}