package com.BossLiftingClub.BossLifting.User.BusinessUser;

import java.time.LocalDateTime;

/**
 * Spring Data projection for lightweight member list query.
 * Column aliases in the native query must match getter names (e.g. userBusinessId -> getUserBusinessId).
 */
public interface MemberListProjection {
    Long getUserBusinessId();
    Long getUserId();
    String getFirstName();
    String getLastName();
    String getEmail();
    String getPhoneNumber();
    String getProfilePictureUrl();
    String getUserType();
    String getCalculatedStatus();
    String getCalculatedUserType();
    LocalDateTime getCreatedAt();
    Boolean getHasEverHadMembership();
    Boolean getIsDelinquent();
    String getStripeId();
    String getMembershipTitles();
}
