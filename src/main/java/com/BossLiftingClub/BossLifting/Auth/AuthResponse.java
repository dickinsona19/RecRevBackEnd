package com.BossLiftingClub.BossLifting.Auth;

import com.BossLiftingClub.BossLifting.Client.ClientDTO;
import com.BossLiftingClub.BossLifting.Business.Staff.StaffDTO;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AuthResponse {
    private String token;
    private String refreshToken;
    private String userType; // CLIENT or STAFF
    private ClientDTO client; // For CLIENT type
    private StaffDTO staff; // For STAFF type
    private String role; // For STAFF: ADMIN, MANAGER, TEAM_MEMBER
}
