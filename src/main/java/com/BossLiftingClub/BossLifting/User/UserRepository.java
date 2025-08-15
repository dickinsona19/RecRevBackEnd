package com.BossLiftingClub.BossLifting.User;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEntryQrcodeToken(String token);
    Optional<User> findByPhoneNumber(String phoneNumber);
    Optional<User> findByUserStripeMemberId(String stripeCustomerId);
    User findByReferralCode(String referralCode);
    boolean existsByReferralCode(String referralCode);

    // In UserRepository
    @Query("UPDATE User u SET u.referralCode = :newReferralCode WHERE u.referralCode = :currentReferralCode")
    @Modifying
    int updateReferralCode(@Param("currentReferralCode") String currentReferralCode,
                           @Param("newReferralCode") String newReferralCode);


    @Query("SELECT u FROM User u LEFT JOIN FETCH u.signInLogs WHERE u.id = :id")
    Optional<User> findByIdWithSignInLogs(@Param("id") Long id);
}
