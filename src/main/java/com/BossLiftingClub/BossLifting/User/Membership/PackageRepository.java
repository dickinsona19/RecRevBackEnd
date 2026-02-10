package com.BossLiftingClub.BossLifting.User.Membership;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;

public interface PackageRepository extends JpaRepository<MembershipPackage, Long> {
    List<MembershipPackage> findByBusinessTagAndArchivedFalse(String businessTag);
    
    @Query("SELECT p FROM MembershipPackage p WHERE p.businessTag = :businessTag")
    List<MembershipPackage> findAllByBusinessTag(@Param("businessTag") String businessTag);
    
    Optional<MembershipPackage> findByIdAndArchivedFalse(Long id);
}
