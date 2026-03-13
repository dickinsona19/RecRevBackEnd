package com.BossLiftingClub.BossLifting.Sync;

import com.BossLiftingClub.BossLifting.Business.Business;
import com.BossLiftingClub.BossLifting.Business.BusinessRepository;
import com.BossLiftingClub.BossLifting.User.User;
import com.BossLiftingClub.BossLifting.User.UserRepository;
import com.BossLiftingClub.BossLifting.User.BusinessUser.UserBusiness;
import com.BossLiftingClub.BossLifting.User.BusinessUser.UserBusinessRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Syncs members from Boss sign-up into RecRev.
 * Creates User + UserBusiness with same entryQrcodeToken for BIZ_afb4e929.
 */
@Service
public class BossSyncService {

    private static final Logger logger = LoggerFactory.getLogger(BossSyncService.class);
    private static final String BOSS_SYNC_DEFAULT_PASSWORD = "BossSyncPlaceholder1";
    private static final String DEFAULT_BUSINESS_TAG = "BIZ_afb4e929";

    private final UserRepository userRepository;
    private final BusinessRepository businessRepository;
    private final UserBusinessRepository userBusinessRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${boss.sync.business-tag:" + DEFAULT_BUSINESS_TAG + "}")
    private String businessTag;

    @Autowired
    public BossSyncService(UserRepository userRepository,
                          BusinessRepository businessRepository,
                          UserBusinessRepository userBusinessRepository,
                          PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.businessRepository = businessRepository;
        this.userBusinessRepository = userBusinessRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public void syncMemberFromBoss(BossSignupSyncRequest request) {
        if (request.getEntryQrcodeToken() == null || request.getEntryQrcodeToken().isBlank()) {
            throw new IllegalArgumentException("entryQrcodeToken is required");
        }
        if (request.getFirstName() == null || request.getLastName() == null) {
            throw new IllegalArgumentException("firstName and lastName are required");
        }

        if (userRepository.findByEntryQrcodeToken(request.getEntryQrcodeToken()).isPresent()) {
            logger.info("Member with entryQrcodeToken {} already exists in RecRev, skipping sync", request.getEntryQrcodeToken());
            return;
        }

        Business business = businessRepository.findByBusinessTag(businessTag)
                .orElseThrow(() -> new IllegalArgumentException("Business not found: " + businessTag));

        if (request.getUserStripeMemberId() != null &&
                userRepository.findByUserStripeMemberId(request.getUserStripeMemberId()).isPresent()) {
            logger.info("Member with Stripe ID {} already exists in RecRev, skipping sync", request.getUserStripeMemberId());
            return;
        }

        User user = new User();
        user.setFirstName(request.getFirstName());
        user.setLastName(request.getLastName());
        user.setEmail(request.getEmail() != null && !request.getEmail().isBlank()
                ? request.getEmail()
                : (request.getPhoneNumber() != null ? request.getPhoneNumber() + "@cltliftingclub.placeholder.com" : null));
        user.setPhoneNumber(request.getPhoneNumber());
        user.setEntryQrcodeToken(request.getEntryQrcodeToken().trim());
        user.setUserStripeMemberId(request.getUserStripeMemberId());
        user.setPassword(passwordEncoder.encode(BOSS_SYNC_DEFAULT_PASSWORD));
        user.setIsInGoodStanding(false);
        user.setLockedInRate(request.getLockedInRate());
        user.setReferralCode(generateUniqueReferralCode());

        user = userRepository.save(user);

        UserBusiness userBusiness = new UserBusiness(user, business, request.getUserStripeMemberId(), "ACTIVE");
        userBusiness.setHasEverHadMembership(true);
        userBusinessRepository.save(userBusiness);

        logger.info("Synced member from Boss: {} {} (entryQrcodeToken={})", user.getFirstName(), user.getLastName(), user.getEntryQrcodeToken());
    }

    private String generateUniqueReferralCode() {
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
        StringBuilder sb = new StringBuilder(10);
        java.util.Random r = new java.util.Random();
        for (int i = 0; i < 10; i++) {
            sb.append(chars.charAt(r.nextInt(chars.length())));
        }
        String code = sb.toString();
        return userRepository.existsByReferralCode(code) ? generateUniqueReferralCode() : code;
    }
}
