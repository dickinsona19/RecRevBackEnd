package com.BossLiftingClub.BossLifting.Business;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BusinessRepository extends JpaRepository<Business, Long> {
    Optional<Business> findByBusinessTag(String businessTag);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT b FROM Business b WHERE b.businessTag = :businessTag")
    Optional<Business> findByBusinessTagWithLock(@Param("businessTag") String businessTag);

    Optional<Business> findByStripeAccountId(String stripeAccountId);

}





