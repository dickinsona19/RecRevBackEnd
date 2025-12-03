package com.BossLiftingClub.BossLifting.User.BusinessUser;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MemberLogRepository extends JpaRepository<MemberLog, Long> {

    /**
     * Find all logs for a specific UserBusiness
     */
    List<MemberLog> findByUserBusiness_IdOrderByCreatedAtDesc(Long userBusinessId);

    /**
     * Find all logs for a specific UserBusiness
     */
    List<MemberLog> findByUserBusiness_Id(Long userBusinessId);
}
