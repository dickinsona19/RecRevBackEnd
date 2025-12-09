package com.BossLiftingClub.BossLifting.User.Waiver;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserWaiverRepository extends JpaRepository<UserWaiver, Long> {

    Optional<UserWaiver> findByUserIdAndWaiverTemplateId(Long userId, Long waiverTemplateId);

    List<UserWaiver> findByUserIdOrderBySignedAtDesc(Long userId);
}

