package com.BossLiftingClub.BossLifting.Business.Staff;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class StaffInvitationDTO {
    private Long id;
    private Long businessId;
    private String businessName;
    private String invitedEmail;
    private String role;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime inviteTokenExpiry;
    private Integer invitedBy;
}
