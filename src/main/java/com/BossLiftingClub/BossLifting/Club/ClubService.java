package com.BossLiftingClub.BossLifting.Club;


import com.stripe.exception.StripeException;

import java.util.List;

public interface ClubService {
    ClubDTO createClub(ClubDTO clubDTO);
//    ClubDTO getClubById(Integer id);
//    List<ClubDTO> getAllClubs();
//    ClubDTO updateClub(long id, ClubDTO clubDTO);
    void deleteClub(long id);

    String createStripeOnboardingLink(String clubTag, String returnUrl, String refreshUrl) throws StripeException;
    String createStripeDashboardLink(String clubTag) throws StripeException;
    void updateOnboardingStatus(String stripeAccountId, String status);
}