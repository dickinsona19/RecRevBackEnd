package com.BossLiftingClub.BossLifting.User.BusinessUser;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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

    @Query("SELECT ub FROM UserBusiness ub " +
           "LEFT JOIN FETCH ub.user u " +
           "LEFT JOIN FETCH ub.userBusinessMemberships ubm " +
           "LEFT JOIN FETCH ubm.membership " +
           "WHERE ub.business.id = :businessId")
    List<UserBusiness> findByBusinessIdWithMemberships(@Param("businessId") Long businessId);

    // Update the existsBy method to use business
    @Query("SELECT COUNT(ub) > 0 FROM UserBusiness ub WHERE ub.user.id = :userId AND ub.business.id = :businessId")
    boolean existsByUser_IdAndBusiness_Id(@Param("userId") Long userId, @Param("businessId") Long businessId);

    // Find UserBusiness by Stripe customer ID
    List<UserBusiness> findByStripeId(String stripeId);

    /**
     * Lightweight list for member table - no UserDTO, no referrer, no status recalculation.
     * Uses native query to fetch only needed columns and avoid N+1 / full entity loading.
     * Search filters by first name, last name, or email (case-insensitive).
     */
    @Query(value = "SELECT ub.id AS user_business_id, u.id AS user_id, u.first_name AS first_name, u.last_name AS last_name, " +
            "u.email AS email, u.phone_number AS phone_number, u.profile_picture_url AS profile_picture_url, " +
            "u.user_type AS user_type, " +
            "COALESCE(ub.calculated_status, 'Inactive') AS calculated_status, " +
            "COALESCE(ub.calculated_user_type, 'Member') AS calculated_user_type, " +
            "ub.created_at AS created_at, " +
            "COALESCE(ub.has_ever_had_membership, false) AS has_ever_had_membership, " +
            "COALESCE(ub.is_delinquent, false) AS is_delinquent, " +
            "ub.stripe_id AS stripe_id, " +
            "(SELECT GROUP_CONCAT(m.title) FROM user_business_membership ubm " +
            " JOIN membership m ON m.id = ubm.membership_id WHERE ubm.user_business_id = ub.id) AS membership_titles " +
            "FROM user_business ub " +
            "JOIN users u ON u.id = ub.user_id " +
            "JOIN businesses b ON b.id = ub.business_id " +
            "WHERE b.business_tag = :businessTag " +
            "AND (COALESCE(TRIM(:search), '') = '' OR LOWER(u.first_name) LIKE LOWER(CONCAT('%', TRIM(:search), '%')) " +
            "  OR LOWER(u.last_name) LIKE LOWER(CONCAT('%', TRIM(:search), '%')) " +
            "  OR LOWER(u.email) LIKE LOWER(CONCAT('%', TRIM(:search), '%'))) " +
            "AND (:delinquentOnly = false OR ub.is_delinquent = true OR LOWER(COALESCE(ub.calculated_status, '')) = 'delinquent') " +
            "ORDER BY ub.created_at DESC",
            countQuery = "SELECT COUNT(*) FROM user_business ub JOIN users u ON u.id = ub.user_id JOIN businesses b ON b.id = ub.business_id " +
            "WHERE b.business_tag = :businessTag " +
            "AND (COALESCE(TRIM(:search), '') = '' OR LOWER(u.first_name) LIKE LOWER(CONCAT('%', TRIM(:search), '%')) " +
            "  OR LOWER(u.last_name) LIKE LOWER(CONCAT('%', TRIM(:search), '%')) " +
            "  OR LOWER(u.email) LIKE LOWER(CONCAT('%', TRIM(:search), '%'))) " +
            "AND (:delinquentOnly = false OR ub.is_delinquent = true OR LOWER(COALESCE(ub.calculated_status, '')) = 'delinquent')",
            nativeQuery = true)
    Page<MemberListProjection> findMemberListByBusinessTag(@Param("businessTag") String businessTag, @Param("search") String search, @Param("delinquentOnly") boolean delinquentOnly, Pageable pageable);

    /**
     * Ids only - for bulk email and similar use cases.
     */
    @Query("SELECT ub.id FROM UserBusiness ub JOIN ub.business b WHERE b.businessTag = :businessTag")
    List<Long> findUserBusinessIdsByBusinessTag(@Param("businessTag") String businessTag);

    @Query("SELECT ub FROM UserBusiness ub JOIN FETCH ub.user u JOIN FETCH ub.business b " +
           "LEFT JOIN FETCH ub.userBusinessMemberships ubm LEFT JOIN FETCH ubm.membership " +
           "WHERE ub.id = :userBusinessId AND b.businessTag = :businessTag")
    Optional<UserBusiness> findByIdAndBusinessTag(@Param("userBusinessId") Long userBusinessId, @Param("businessTag") String businessTag);
}
