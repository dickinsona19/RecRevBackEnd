package com.BossLiftingClub.BossLifting.Products;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ProductRepository extends JpaRepository<Products, Long> {
    List<Products> findByBusinessTag(String businessTag);
    
    // Backward compatibility - clubTag maps to businessTag
    @Query("SELECT p FROM Products p WHERE p.businessTag = :clubTag")
    List<Products> findByClubTag(@Param("clubTag") String clubTag);
}
