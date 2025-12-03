package com.BossLiftingClub.BossLifting.Business.Staff;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface StaffRepository extends JpaRepository<Staff, Integer> {
    Optional<Staff> findByEmail(String email);
    List<Staff> findByEmailAndIsActiveTrue(String email);
    List<Staff> findByBusinessId(Long businessId);
    
    @Query("SELECT s FROM Staff s LEFT JOIN FETCH s.business WHERE s.email = :email AND s.isActive = true")
    List<Staff> findByEmailAndIsActiveTrueWithBusiness(@Param("email") String email);
    
    @Query("SELECT s FROM Staff s LEFT JOIN FETCH s.business WHERE s.id = :id")
    Optional<Staff> findByIdWithBusiness(@Param("id") Integer id);
}
