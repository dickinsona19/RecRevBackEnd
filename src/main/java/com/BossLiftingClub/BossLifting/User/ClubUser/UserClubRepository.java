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
    boolean existsByUser_IdAndClub_Id(Long userId, Long clubId);

    // Find all UserClub relationships for a specific club by clubTag
    @Query("SELECT uc FROM UserClub uc " +
           "JOIN FETCH uc.user u " +
           "LEFT JOIN FETCH u.membership " +
           "JOIN FETCH uc.club c " +
           "LEFT JOIN FETCH uc.membership " +
           "WHERE c.clubTag = :clubTag")
    List<UserClub> findAllByClubTag(@Param("clubTag") String clubTag);

    // Find a specific user-club relationship by userId and clubTag
    @Query("SELECT uc FROM UserClub uc JOIN FETCH uc.user u JOIN FETCH uc.club c WHERE u.id = :userId AND c.clubTag = :clubTag")
    Optional<UserClub> findByUserIdAndClubTag(@Param("userId") Long userId, @Param("clubTag") String clubTag);

    // Find all clubs for a specific user
    List<UserClub> findAllByUserId(Long userId);

    // Find all UserClub records for a specific club ID
    List<UserClub> findByClubId(Long clubId);
}