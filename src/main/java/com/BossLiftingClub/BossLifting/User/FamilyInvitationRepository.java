package com.BossLiftingClub.BossLifting.User;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface FamilyInvitationRepository extends JpaRepository<FamilyInvitation, Long> {
    List<FamilyInvitation> findByPrimaryOwnerIdAndStatus(Long primaryOwnerId, FamilyInvitation.InvitationStatus status);
    List<FamilyInvitation> findByPrimaryOwnerId(Long primaryOwnerId);
    Optional<FamilyInvitation> findByInvitedEmailAndStatus(String email, FamilyInvitation.InvitationStatus status);
    Optional<FamilyInvitation> findByInvitedEmailAndPrimaryOwnerId(String email, Long primaryOwnerId);
}
