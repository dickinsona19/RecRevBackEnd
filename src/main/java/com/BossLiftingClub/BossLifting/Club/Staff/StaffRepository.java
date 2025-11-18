package com.BossLiftingClub.BossLifting.Club.Staff;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface StaffRepository extends JpaRepository<Staff, Integer> {
    Optional<Staff> findByEmail(String email);
    List<Staff> findByEmailAndIsActiveTrue(String email);
    List<Staff> findByBusinessId(Long businessId);
}