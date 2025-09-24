package com.BossLiftingClub.BossLifting.User.ClubUser;

import com.BossLiftingClub.BossLifting.User.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface UserClubRepository extends JpaRepository<UserClub, Long> {
    boolean existsByUser_IdAndClub_Id(Long userId, Long clubId);
}