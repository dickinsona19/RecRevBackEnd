package com.BossLiftingClub.BossLifting.Client;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ClientRepository extends JpaRepository<Client, Integer> {
    Optional<Client> findByEmail(String email);
    
    // Find client with businesses
    @Query("SELECT c FROM Client c LEFT JOIN FETCH c.businesses WHERE c.id = :id")
    Optional<Client> findByIdWithBusinesses(@Param("id") Integer id);
    
    // Backward compatibility - clubs maps to businesses
    @Query("SELECT c FROM Client c LEFT JOIN FETCH c.businesses WHERE c.id = :id")
    Optional<Client> findByIdWithClubs(@Param("id") Integer id);

}