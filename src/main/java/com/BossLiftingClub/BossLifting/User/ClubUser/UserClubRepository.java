package com.BossLiftingClub.BossLifting.User.ClubUser;

import com.BossLiftingClub.BossLifting.User.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserClubRepository extends JpaRepository<UserClub, Long> {
    // Backward compatibility - kept for existing code
    @Deprecated
    @Query("SELECT COUNT(uc) > 0 FROM UserClub uc WHERE uc.user.id = :userId AND uc.business.id = :clubId")
    boolean existsByUser_IdAndClub_Id(@Param("userId") Long userId, @Param("clubId") Long clubId);

    // Find all UserClub relationships for a specific business by businessTag
    @Query("SELECT uc FROM UserClub uc " +
           "JOIN FETCH uc.user u " +
           "LEFT JOIN FETCH u.membership " +
           "JOIN FETCH uc.business b " +
           "LEFT JOIN FETCH uc.membership " +
           "WHERE b.businessTag = :businessTag")
    List<UserClub> findAllByBusinessTag(@Param("businessTag") String businessTag);

    // Backward compatibility - clubTag is now mapped to businessTag
    @Query("SELECT uc FROM UserClub uc " +
           "JOIN FETCH uc.user u " +
           "LEFT JOIN FETCH u.membership " +
           "JOIN FETCH uc.business b " +
           "LEFT JOIN FETCH uc.membership " +
           "WHERE b.businessTag = :clubTag")
    List<UserClub> findAllByClubTag(@Param("clubTag") String clubTag);

    // Find a specific user-business relationship by userId and businessTag
    @Query("SELECT uc FROM UserClub uc JOIN FETCH uc.user u JOIN FETCH uc.business b WHERE u.id = :userId AND b.businessTag = :businessTag")
    Optional<UserClub> findByUserIdAndBusinessTag(@Param("userId") Long userId, @Param("businessTag") String businessTag);

    // Backward compatibility - clubTag is now mapped to businessTag
    @Query("SELECT uc FROM UserClub uc JOIN FETCH uc.user u JOIN FETCH uc.business b WHERE u.id = :userId AND b.businessTag = :clubTag")
    Optional<UserClub> findByUserIdAndClubTag(@Param("userId") Long userId, @Param("clubTag") String clubTag);

    // Find all businesses for a specific user
    List<UserClub> findAllByUserId(Long userId);

    // Find all UserClub records for a specific business ID
    List<UserClub> findByBusinessId(Long businessId);

    // Backward compatibility - clubId is now mapped to businessId (column name)
    @Query("SELECT uc FROM UserClub uc WHERE uc.business.id = :clubId")
    List<UserClub> findByClubId(@Param("clubId") Long clubId);
    
    // Update the existsBy method to use business
    @Query("SELECT COUNT(uc) > 0 FROM UserClub uc WHERE uc.user.id = :userId AND uc.business.id = :businessId")
    boolean existsByUser_IdAndBusiness_Id(@Param("userId") Long userId, @Param("businessId") Long businessId);
}