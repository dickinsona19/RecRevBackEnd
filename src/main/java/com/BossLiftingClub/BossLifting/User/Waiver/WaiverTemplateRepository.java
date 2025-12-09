package com.BossLiftingClub.BossLifting.User.Waiver;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface WaiverTemplateRepository extends JpaRepository<WaiverTemplate, Long> {

    Optional<WaiverTemplate> findFirstByBusinessIdAndActiveTrueOrderByVersionDesc(Long businessId);

    List<WaiverTemplate> findByBusinessIdOrderByVersionDesc(Long businessId);

    boolean existsByBusinessIdAndVersion(Long businessId, Integer version);

    Optional<WaiverTemplate> findTopByBusinessIdOrderByVersionDesc(Long businessId);
}

