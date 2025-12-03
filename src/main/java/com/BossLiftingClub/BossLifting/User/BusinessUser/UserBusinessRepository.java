package com.BossLiftingClub.BossLifting.User.BusinessUser;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserBusinessRepository extends JpaRepository<UserBusiness, Long> {
    // Find all UserBusiness relationships for a specific business by businessTag
    @Query("SELECT ub FROM UserBusiness ub " +
           "JOIN FETCH ub.user u " +
           "LEFT JOIN FETCH u.membership " +
           "JOIN FETCH ub.business b " +
           "LEFT JOIN FETCH ub.userBusinessMemberships ubm " +
           "LEFT JOIN FETCH ubm.membership " +
           "WHERE b.businessTag = :businessTag")
    List<UserBusiness> findAllByBusinessTag(@Param("businessTag") String businessTag);

    // Find a specific user-business relationship by userId and businessTag
    @Query("SELECT ub FROM UserBusiness ub JOIN FETCH ub.user u JOIN FETCH ub.business b WHERE u.id = :userId AND b.businessTag = :businessTag")
    Optional<UserBusiness> findByUserIdAndBusinessTag(@Param("userId") Long userId, @Param("businessTag") String businessTag);

    // Find all businesses for a specific user
    List<UserBusiness> findAllByUserId(Long userId);

    // Find all UserBusiness records for a specific business ID
    List<UserBusiness> findByBusinessId(Long businessId);

    // Update the existsBy method to use business
    @Query("SELECT COUNT(ub) > 0 FROM UserBusiness ub WHERE ub.user.id = :userId AND ub.business.id = :businessId")
    boolean existsByUser_IdAndBusiness_Id(@Param("userId") Long userId, @Param("businessId") Long businessId);
}
