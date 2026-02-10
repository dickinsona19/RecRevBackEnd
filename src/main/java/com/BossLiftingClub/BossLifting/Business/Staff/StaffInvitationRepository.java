package com.BossLiftingClub.BossLifting.Business.Staff;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface StaffInvitationRepository extends JpaRepository<StaffInvitation, Long> {
    List<StaffInvitation> findByBusinessIdAndStatus(Long businessId, StaffInvitation.InvitationStatus status);
    
    Optional<StaffInvitation> findByInviteTokenAndStatus(String inviteToken, StaffInvitation.InvitationStatus status);
    
    Optional<StaffInvitation> findByInvitedEmailAndBusinessIdAndStatus(String email, Long businessId, StaffInvitation.InvitationStatus status);
}
