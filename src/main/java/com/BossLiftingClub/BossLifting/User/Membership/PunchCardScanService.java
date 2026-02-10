package com.BossLiftingClub.BossLifting.User.Membership;

import com.BossLiftingClub.BossLifting.User.BusinessUser.UserBusinessMembership;
import com.BossLiftingClub.BossLifting.User.BusinessUser.UserBusinessMembershipRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class PunchCardScanService {
    @Autowired
    private PunchCardScanRepository punchCardScanRepository;

    @Autowired
    private UserBusinessMembershipRepository userBusinessMembershipRepository;

    /**
     * Process a punch card scan with double-scan protection (1 hour window)
     * @param membershipId The UserBusinessMembership ID
     * @return true if scan was successful, false if it was a duplicate within 1 hour
     */
    @Transactional
    public boolean processPunchCardScan(Long membershipId) {
        UserBusinessMembership membership = userBusinessMembershipRepository.findById(membershipId)
                .orElseThrow(() -> new RuntimeException("Membership not found"));

        // Check if this is a punch card membership
        if (membership.getMembership() == null || 
            !"PUNCH_CARD".equals(membership.getMembership().getMembershipType())) {
            throw new RuntimeException("This is not a punch card membership");
        }

        // Check if punches are remaining
        if (membership.getPunchesRemaining() == null || membership.getPunchesRemaining() <= 0) {
            throw new RuntimeException("No punches remaining on this card");
        }

        // Check if card has expired
        if (membership.getPunchesExpiryDate() != null && 
            membership.getPunchesExpiryDate().isBefore(LocalDateTime.now())) {
            throw new RuntimeException("This punch card has expired");
        }

        // Double-scan protection: Check for scans within the last hour
        LocalDateTime oneHourAgo = LocalDateTime.now().minusHours(1);
        List<PunchCardScan> recentScans = punchCardScanRepository.findRecentScans(membershipId, oneHourAgo);

        if (!recentScans.isEmpty()) {
            // Duplicate scan within 1 hour - don't process
            return false;
        }

        // Record the scan
        PunchCardScan scan = new PunchCardScan();
        scan.setUserBusinessMembership(membership);
        scan.setScannedAt(LocalDateTime.now());
        punchCardScanRepository.save(scan);

        // Decrement punch count
        int remaining = membership.getPunchesRemaining() - 1;
        membership.setPunchesRemaining(remaining);
        userBusinessMembershipRepository.save(membership);

        return true;
    }
}
