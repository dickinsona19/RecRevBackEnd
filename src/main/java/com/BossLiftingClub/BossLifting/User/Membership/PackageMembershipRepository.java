package com.BossLiftingClub.BossLifting.User.Membership;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface PackageMembershipRepository extends JpaRepository<PackageMembership, Long> {
    List<PackageMembership> findByPackageEntityId(Long packageId);
    
    void deleteByPackageEntityId(Long packageId);
}
