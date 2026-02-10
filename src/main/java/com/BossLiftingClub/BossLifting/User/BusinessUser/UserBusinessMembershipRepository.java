package com.BossLiftingClub.BossLifting.User.BusinessUser;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserBusinessMembershipRepository extends JpaRepository<UserBusinessMembership, Long> {
    
    /**
     * Find membership by Stripe subscription ID
     */
    Optional<UserBusinessMembership> findByStripeSubscriptionId(String stripeSubscriptionId);
    
    /**
     * Find all memberships for a user-business relationship
     */
    List<UserBusinessMembership> findByUserBusinessId(Long userBusinessId);
    
    /**
     * Find all memberships for a user across all businesses
     * Used to find Primary Owner's subscriptions
     */
    @Query("SELECT ubm FROM UserBusinessMembership ubm " +
           "JOIN FETCH ubm.userBusiness ub " +
           "JOIN FETCH ub.user u " +
           "WHERE u.id = :userId")
    List<UserBusinessMembership> findByUserId(@Param("userId") Long userId);
    
    /**
     * Find active memberships for a user in a specific business
     */
    @Query("SELECT ubm FROM UserBusinessMembership ubm " +
           "JOIN FETCH ubm.userBusiness ub " +
           "JOIN FETCH ub.user u " +
           "JOIN FETCH ub.business b " +
           "WHERE u.id = :userId AND b.businessTag = :businessTag AND ubm.status = 'ACTIVE'")
    List<UserBusinessMembership> findActiveByUserIdAndBusinessTag(@Param("userId") Long userId, @Param("businessTag") String businessTag);
}
