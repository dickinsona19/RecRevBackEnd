package com.BossLiftingClub.BossLifting.User;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEntryQrcodeToken(String token);
    Optional<User> findByPhoneNumber(String phoneNumber);
    Optional<User> findByUserStripeMemberId(String stripeCustomerId);
    User findByReferralCode(String referralCode);
    Optional<User> findByEmail(String email);
    boolean existsByReferralCode(String referralCode);

    @Query("SELECT u FROM User u LEFT JOIN FETCH u.userTitles")
    List<User> findAll();

    @Query("UPDATE User u SET u.referralCode = :newReferralCode WHERE u.referralCode = :currentReferralCode")
    @Modifying
    int updateReferralCode(@Param("currentReferralCode") String currentReferralCode,
                           @Param("newReferralCode") String newReferralCode);

    @Query("SELECT u FROM User u LEFT JOIN FETCH u.signInLogs WHERE u.id = :id")
    Optional<User> findByIdWithSignInLogs(@Param("id") Long id);

    @Query("""
        SELECT u
        FROM User u
        LEFT JOIN FETCH u.membership
        JOIN FETCH u.userClubs uc
        JOIN FETCH uc.club c
        WHERE c.clubTag = :clubTag
    """)
    List<User> findUsersWithMembershipByClubTag(@Param("clubTag") String clubTag);
}