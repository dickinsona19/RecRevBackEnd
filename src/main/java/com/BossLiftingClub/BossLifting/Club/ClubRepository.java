package com.BossLiftingClub.BossLifting.Club;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ClubRepository extends JpaRepository<Club, Integer> {
    Optional<Club> findByClubTag(String clubTag);

    @Query("SELECT c FROM Club c LEFT JOIN FETCH c.memberships WHERE c.id = :id")
    Optional<Club> findByIdWithMemberships(@Param("id") Integer id);

    @Query("SELECT c FROM Club c LEFT JOIN FETCH c.memberships")
    List<Club> findAllWithMemberships();}