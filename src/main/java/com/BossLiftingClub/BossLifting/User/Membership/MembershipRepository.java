package com.BossLiftingClub.BossLifting.User.Membership;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;

public interface MembershipRepository extends JpaRepository<Membership, Long> {
    // Find by businessTag (new column)
    List<Membership> findByBusinessTag(String businessTag);
    
    // Backward compatibility - find by clubTag maps to businessTag
    @Query("SELECT m FROM Membership m WHERE m.businessTag = :clubTag")
    List<Membership> findByClubTag(@Param("clubTag") String clubTag);
    
    // Find public memberships by businessTag
    @Query("SELECT m FROM Membership m WHERE m.businessTag = :businessTag AND m.isPublic = true AND m.archived = false")
    List<Membership> findPublicMembershipsByBusinessTag(@Param("businessTag") String businessTag);
}