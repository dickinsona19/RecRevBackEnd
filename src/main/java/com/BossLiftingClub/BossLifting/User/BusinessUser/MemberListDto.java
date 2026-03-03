package com.BossLiftingClub.BossLifting.User.BusinessUser;

import java.time.LocalDateTime;

/**
 * Lightweight DTO for member list display. Avoids loading full User/UserDTO,
 * referrer, signInLogs, calculateAndUpdateStatus, etc.
 */
public record MemberListDto(
        Long userBusinessId,
        Long userId,
        String firstName,
        String lastName,
        String email,
        String phoneNumber,
        String profilePictureUrl,
        String userType,
        String calculatedStatus,
        String calculatedUserType,
        LocalDateTime createdAt,
        Boolean hasEverHadMembership,
        Boolean isDelinquent,
        String stripeId,
        String membershipTitles
) {}
