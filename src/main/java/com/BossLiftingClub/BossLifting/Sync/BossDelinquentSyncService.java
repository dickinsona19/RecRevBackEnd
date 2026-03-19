package com.BossLiftingClub.BossLifting.Sync;

import com.BossLiftingClub.BossLifting.User.BusinessUser.UserBusiness;
import com.BossLiftingClub.BossLifting.User.BusinessUser.UserBusinessRepository;
import com.BossLiftingClub.BossLifting.User.BusinessUser.UserBusinessService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Marks members as delinquent when Boss-Lifting-Club signals past_due or invoice.payment_failed.
 */
@Service
public class BossDelinquentSyncService {

    private static final Logger logger = LoggerFactory.getLogger(BossDelinquentSyncService.class);

    private final UserBusinessRepository userBusinessRepository;
    private final UserBusinessService userBusinessService;

    public BossDelinquentSyncService(UserBusinessRepository userBusinessRepository,
                                    UserBusinessService userBusinessService) {
        this.userBusinessRepository = userBusinessRepository;
        this.userBusinessService = userBusinessService;
    }

    /**
     * Find UserBusiness records by Stripe customer ID and mark them delinquent.
     */
    @Transactional
    public void markDelinquentByStripeId(String userStripeMemberId) {
        List<UserBusiness> userBusinesses = userBusinessRepository.findByStripeId(userStripeMemberId);
        if (userBusinesses.isEmpty()) {
            logger.debug("No UserBusiness found for stripe ID {}, skipping delinquent sync", userStripeMemberId);
            return;
        }

        for (UserBusiness ub : userBusinesses) {
            ub.setIsDelinquent(true);
            userBusinessRepository.save(ub);
            userBusinessService.calculateAndUpdateStatus(ub);
            logger.info("Marked member delinquent from Boss signal (userBusinessId={}, stripeId={})",
                    ub.getId(), userStripeMemberId);
        }
    }
}
