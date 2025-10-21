package com.BossLiftingClub.BossLifting.Auth;

import com.BossLiftingClub.BossLifting.Client.ClientDTO;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AuthResponse {
    private String token;
    private String refreshToken;
    private ClientDTO client;
}
