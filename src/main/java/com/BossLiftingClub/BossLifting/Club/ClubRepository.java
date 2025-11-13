package com.BossLiftingClub.BossLifting.Club;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ClubRepository extends JpaRepository<Club, Long> {
    Optional<Club> findByClubTag(String clubTag);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM Club c WHERE c.clubTag = :clubTag")
    Optional<Club> findByClubTagWithLock(@Param("clubTag") String clubTag);

    Optional<Club> findByStripeAccountId(String stripeAccountId);

}