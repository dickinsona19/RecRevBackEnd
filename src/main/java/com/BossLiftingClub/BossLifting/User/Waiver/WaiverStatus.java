package com.BossLiftingClub.BossLifting.User.Waiver;

/**
 * Tracks the lifecycle of a member's waiver. Gives admins an easy way to see whether a user still
 * needs to sign, is in progress, or is fully compliant with the latest template.
 */
public enum WaiverStatus {
    NOT_SIGNED,
    PENDING_SIGNATURE,
    SIGNED,
    EXPIRED
}

