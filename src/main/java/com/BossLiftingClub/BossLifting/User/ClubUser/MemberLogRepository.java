package com.BossLiftingClub.BossLifting.User.ClubUser;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MemberLogRepository extends JpaRepository<MemberLog, Long> {

    /**
     * Find all logs for a specific UserClub
     */
    List<MemberLog> findByUserClubIdOrderByCreatedAtDesc(Long userClubId);

    /**
     * Find all logs for a specific UserClub
     */
    List<MemberLog> findByUserClubId(Long userClubId);
}
