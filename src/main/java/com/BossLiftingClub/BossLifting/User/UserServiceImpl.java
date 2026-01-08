package com.BossLiftingClub.BossLifting.User;

import com.BossLiftingClub.BossLifting.Business.Business;
import com.BossLiftingClub.BossLifting.Business.BusinessRepository;
import com.BossLiftingClub.BossLifting.Stripe.StripeService;
import com.BossLiftingClub.BossLifting.User.BusinessUser.UserBusiness;
import com.BossLiftingClub.BossLifting.User.BusinessUser.UserBusinessMembership;
import com.BossLiftingClub.BossLifting.User.BusinessUser.UserBusinessRepository;
import com.BossLiftingClub.BossLifting.User.BusinessUser.UserBusinessCreateDTO;
import com.BossLiftingClub.BossLifting.User.BusinessUser.UserBusinessService;
import com.BossLiftingClub.BossLifting.User.Membership.Membership;
import com.BossLiftingClub.BossLifting.User.Membership.MembershipRepository;
import com.BossLiftingClub.BossLifting.User.SignInLog.SignInLog;
import com.BossLiftingClub.BossLifting.User.SignInLog.SignInLogRepository;
import com.stripe.exception.StripeException;
import com.stripe.model.Subscription;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class UserServiceImpl implements UserService {
    private static final Logger logger = LoggerFactory.getLogger(UserServiceImpl.class);

    private final UserRepository userRepository;
    private final SecureRandom random = new SecureRandom();
    private static final String CHARACTERS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    private final PasswordEncoder passwordEncoder;

    @Autowired
    private SignInLogRepository signInLogRepository;

    @Autowired
    private JavaMailSender mailSender;

    @Autowired
    private BusinessRepository businessRepository;

    @Autowired
    private UserBusinessRepository userBusinessRepository;

    @Autowired
    private MembershipRepository membershipRepository;

    @Autowired
    private StripeService stripeService;

    @Autowired
    private UserBusinessService userBusinessService;

    @Autowired
    public UserServiceImpl(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public boolean updateReferralCode(String currentReferralCode, String newReferralCode) {
        return userRepository.updateReferralCode(currentReferralCode, newReferralCode) > 0;
    }

    @Override
    public User getUserByReferralCode(String referralCode) {
        User userOptional = userRepository.findByReferralCode(referralCode);
        if (userOptional != null) {
            return userOptional;
        }
        return null;
    }

    @Override
    public Optional<User> updateWaiverSignature(Long userId, String imageUrl) {
        return userRepository.findById(userId).map(user -> {
            user.setSignatureData(imageUrl); // assuming you have this field
            return userRepository.save(user);
        });
    }

    @Override
    @Transactional
    public User handleNewBusiness(UserBusinessCreateDTO userDTO) throws Exception {
        logger.info("=== STARTING handleNewBusiness ===");
        logger.info("UserDTO: email={}, firstName={}, lastName={}, paymentMethodId={}, businessId={}", 
                userDTO.getEmail(), userDTO.getFirstName(), userDTO.getLastName(), 
                userDTO.getPaymentMethodId(), userDTO.getBusinessMembership() != null ? userDTO.getBusinessMembership().getBusinessId() : "null");
        
        // Validate input
        if (userDTO.getEmail() == null || userDTO.getEmail().trim().isEmpty()) {
            logger.error("Validation failed: Email is required");
            throw new IllegalArgumentException("Email is required");
        }
        if (userDTO.getBusinessMembership() == null || userDTO.getBusinessMembership().getBusinessId() == null) {
            logger.error("Validation failed: Business membership is required");
            throw new IllegalArgumentException("Business membership is required");
        }

        // Check if user exists by email
        Optional<User> existingUserOpt = userRepository.findByEmail(userDTO.getEmail());
        User user;

        // Fetch the business (common for both branches)
        Long businessId = userDTO.getBusinessMembership().getBusinessId();
        Business business = businessRepository.findById(businessId)
                .orElseThrow(() -> {
                    logger.error("Business not found with ID: {}", businessId);
                    return new IllegalArgumentException("Invalid business ID: " + businessId);
                });
        
        logger.info("Business found: id={}, title={}, businessTag={}, stripeAccountId={}, onboardingStatus={}", 
                business.getId(), business.getTitle(), business.getBusinessTag(), 
                business.getStripeAccountId(), business.getOnboardingStatus());

        if (existingUserOpt.isPresent()) {
            // User exists, check if they're already in this business
            user = existingUserOpt.get();

            // Check if user is already a member of this business
            if (userBusinessRepository.existsByUser_IdAndBusiness_Id(user.getId(), business.getId())) {
                throw new IllegalArgumentException("User with email " + userDTO.getEmail() + " is already a member of this business");
            }

            // Add new business membership
            List<UserBusiness> userBusinesses = user.getUserBusinesses();
            if (userBusinesses == null) {
                userBusinesses = new ArrayList<>();
                user.setUserBusinesses(userBusinesses);
            }

            // Create UserBusiness entry
            UserBusiness userBusiness = new UserBusiness();
            userBusiness.setUser(user);
            userBusiness.setBusiness(business);
            userBusiness.setStatus(userDTO.getBusinessMembership().getStatus() != null ? userDTO.getBusinessMembership().getStatus() : "ACTIVE");

            // Create Stripe customer on the connected account if not provided
            String stripeCustomerId = userDTO.getBusinessMembership().getStripeId();
            logger.info("Existing Stripe customer ID from DTO (existing user path): {}", stripeCustomerId);
            
            if (stripeCustomerId == null || stripeCustomerId.isEmpty()) {
                String stripeAccountId = business.getStripeAccountId();
                logger.info("Business stripeAccountId (existing user path): {}", stripeAccountId);
                
                if (stripeAccountId == null || stripeAccountId.isEmpty()) {
                    logger.error("Business {} (ID: {}) does not have a Stripe account ID. Onboarding status: {}", 
                            business.getBusinessTag(), business.getId(), business.getOnboardingStatus());
                    throw new IllegalStateException(
                            String.format("Business '%s' has not completed Stripe onboarding. Please complete Stripe setup before adding members. Current status: %s", 
                                    business.getTitle(), business.getOnboardingStatus() != null ? business.getOnboardingStatus() : "NOT_STARTED"));
                }
                
                try {
                    String fullName = user.getFirstName() + " " + user.getLastName();
                    logger.info("Creating Stripe customer on connected account (existing user): email={}, name={}, accountId={}", 
                            user.getEmail(), fullName, stripeAccountId);
                    stripeCustomerId = stripeService.createCustomerOnConnectedAccount(
                        user.getEmail(),
                        fullName,
                        stripeAccountId
                    );
                    logger.info("✅ Created Stripe customer: {} for user {} (existing user)", stripeCustomerId, user.getEmail());
                } catch (StripeException e) {
                    logger.error("❌ Failed to create Stripe customer (existing user): {}", e.getMessage(), e);
                    throw new Exception("Failed to create Stripe customer: " + e.getMessage(), e);
                }
            }
            userBusiness.setStripeId(stripeCustomerId != null ? stripeCustomerId : "");
            logger.info("UserBusiness stripeId set to (existing user path): {}", userBusiness.getStripeId());

            // Save the UserBusiness first before processing memberships
            userBusinesses.add(userBusiness);
            user = userRepository.save(user);

            // If payment method is provided, attach it immediately
            logger.info("Checking payment method attachment (existing user path): paymentMethodId={}, stripeCustomerId={}", 
                    userDTO.getPaymentMethodId(), stripeCustomerId);
            
            if (userDTO.getPaymentMethodId() != null && !userDTO.getPaymentMethodId().isEmpty() && stripeCustomerId != null && !stripeCustomerId.isEmpty()) {
                String stripeAccountId = business.getStripeAccountId();
                try {
                    if (stripeAccountId == null || stripeAccountId.isEmpty()) {
                        logger.error("Cannot attach payment method (existing user): Business does not have Stripe account ID");
                        throw new IllegalStateException("Business has not completed Stripe onboarding. Cannot attach payment method.");
                    }
                    
                    logger.info("Attaching payment method {} to customer {} on account {} (existing user)", 
                            userDTO.getPaymentMethodId(), stripeCustomerId, stripeAccountId);
                    stripeService.attachPaymentMethodOnConnectedAccount(
                        stripeCustomerId,
                        userDTO.getPaymentMethodId(),
                        stripeAccountId
                    );
                    logger.info("✅ Payment method {} attached to customer {} (existing user)", userDTO.getPaymentMethodId(), stripeCustomerId);
                } catch (StripeException e) {
                    logger.error("❌ Failed to attach payment method {} to customer {} on account {}: {} (code: {})", 
                            userDTO.getPaymentMethodId(), stripeCustomerId, stripeAccountId != null ? stripeAccountId : "unknown", 
                            e.getMessage(), e.getCode(), e);
                    throw new Exception("Failed to attach payment method: " + e.getMessage() + 
                            (e.getCode() != null ? " (code: " + e.getCode() + ")" : ""), e);
                } catch (IllegalStateException e) {
                    logger.error("❌ Stripe setup issue (existing user): {}", e.getMessage());
                    throw e;
                }
            } else if (userDTO.getPaymentMethodId() != null && !userDTO.getPaymentMethodId().isEmpty()) {
                logger.warn("⚠️ Payment method provided but no Stripe customer ID available (existing user). Payment method ID: {}", userDTO.getPaymentMethodId());
            } else {
                logger.info("No payment method provided or no customer ID - skipping payment method attachment (existing user)");
            }

            // Create memberships immediately if payment method was provided
            if (userDTO.getPaymentMethodId() != null && !userDTO.getPaymentMethodId().isEmpty() && userDTO.getMemberships() != null) {
                UserBusiness savedUserBusiness = user.getUserBusinesses().stream()
                    .filter(ub -> ub.getBusiness().getId().equals(business.getId()))
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException("UserBusiness not found after save"));

                for (UserBusinessCreateDTO.MembershipRequestDTO membershipRequest : userDTO.getMemberships()) {
                    Membership membership = membershipRepository.findById(membershipRequest.getMembershipId())
                        .orElseThrow(() -> new IllegalArgumentException("Invalid membership ID: " + membershipRequest.getMembershipId()));

                    UserBusinessMembership userBusinessMembership = new UserBusinessMembership();
                    userBusinessMembership.setUserBusiness(savedUserBusiness);
                    userBusinessMembership.setMembership(membership);
                    userBusinessMembership.setStatus(membershipRequest.getStatus() != null ? membershipRequest.getStatus() : "ACTIVE");
                    userBusinessMembership.setAnchorDate(membershipRequest.getAnchorDate() != null ? membershipRequest.getAnchorDate() : LocalDateTime.now());
                    userBusinessMembership.setActualPrice(parsePrice(membership.getPrice()));

                    // Create Stripe subscription
                    createStripeSubscriptionForMembership(userBusinessMembership, business);

                    if (savedUserBusiness.getUserBusinessMemberships() == null) {
                        savedUserBusiness.setUserBusinessMemberships(new ArrayList<>());
                    }
                    savedUserBusiness.getUserBusinessMemberships().add(userBusinessMembership);
                }
                
                // Recalculate status after adding memberships
                userBusinessService.calculateAndUpdateStatus(savedUserBusiness);
            }
        } else {
            // Create new user
            if (userDTO.getFirstName() == null || userDTO.getLastName() == null) {
                throw new IllegalArgumentException("First name and last name are required for new users");
            }

            user = new User();
            user.setFirstName(userDTO.getFirstName());
            user.setLastName(userDTO.getLastName());
            user.setEmail(userDTO.getEmail());
            user.setPassword("userpass1"); // Temporary; consider generating or prompting
            user.setIsInGoodStanding(true);

            // Save user first to generate ID (this calls the overridden save() which encodes password and generates tokens)
            user = save(user);

            // Create UserBusiness entry
            UserBusiness userBusiness = new UserBusiness();
            userBusiness.setUser(user);
            userBusiness.setBusiness(business);
            userBusiness.setStatus(userDTO.getBusinessMembership().getStatus() != null ? userDTO.getBusinessMembership().getStatus() : "ACTIVE");

            // Create Stripe customer on the connected account if not provided
            String stripeCustomerId = userDTO.getBusinessMembership().getStripeId();
            logger.info("Existing Stripe customer ID from DTO (new user path): {}", stripeCustomerId);
            
            if (stripeCustomerId == null || stripeCustomerId.isEmpty()) {
                String stripeAccountId = business.getStripeAccountId();
                logger.info("Business stripeAccountId (new user path): {}", stripeAccountId);
                
                if (stripeAccountId == null || stripeAccountId.isEmpty()) {
                    logger.error("Business {} (ID: {}) does not have a Stripe account ID. Onboarding status: {}", 
                            business.getBusinessTag(), business.getId(), business.getOnboardingStatus());
                    throw new IllegalStateException(
                            String.format("Business '%s' has not completed Stripe onboarding. Please complete Stripe setup before adding members. Current status: %s", 
                                    business.getTitle(), business.getOnboardingStatus() != null ? business.getOnboardingStatus() : "NOT_STARTED"));
                }
                
                try {
                    String fullName = user.getFirstName() + " " + user.getLastName();
                    logger.info("Creating Stripe customer on connected account (new user): email={}, name={}, accountId={}", 
                            user.getEmail(), fullName, stripeAccountId);
                    stripeCustomerId = stripeService.createCustomerOnConnectedAccount(
                        user.getEmail(),
                        fullName,
                        stripeAccountId
                    );
                    logger.info("✅ Created Stripe customer: {} for user {} (new user)", stripeCustomerId, user.getEmail());
                } catch (StripeException e) {
                    logger.error("❌ Failed to create Stripe customer (new user): {}", e.getMessage(), e);
                    throw new Exception("Failed to create Stripe customer: " + e.getMessage(), e);
                }
            }
            userBusiness.setStripeId(stripeCustomerId != null ? stripeCustomerId : "");
            logger.info("UserBusiness stripeId set to (new user path): {}", userBusiness.getStripeId());

            user.setUserBusinesses(new ArrayList<>(List.of(userBusiness)));

            // Save user first to get IDs
            user = userRepository.save(user);

            // If payment method is provided, attach it immediately
            logger.info("Checking payment method attachment (new user path): paymentMethodId={}, stripeCustomerId={}", 
                    userDTO.getPaymentMethodId(), stripeCustomerId);
            
            if (userDTO.getPaymentMethodId() != null && !userDTO.getPaymentMethodId().isEmpty() && stripeCustomerId != null && !stripeCustomerId.isEmpty()) {
                String stripeAccountId = business.getStripeAccountId();
                try {
                    if (stripeAccountId == null || stripeAccountId.isEmpty()) {
                        logger.error("Cannot attach payment method (new user): Business does not have Stripe account ID");
                        throw new IllegalStateException("Business has not completed Stripe onboarding. Cannot attach payment method.");
                    }
                    
                    logger.info("Attaching payment method {} to customer {} on account {} (new user)", 
                            userDTO.getPaymentMethodId(), stripeCustomerId, stripeAccountId);
                    stripeService.attachPaymentMethodOnConnectedAccount(
                        stripeCustomerId,
                        userDTO.getPaymentMethodId(),
                        stripeAccountId
                    );
                    logger.info("✅ Payment method {} attached to customer {} (new user)", userDTO.getPaymentMethodId(), stripeCustomerId);
                } catch (StripeException e) {
                    logger.error("❌ Failed to attach payment method {} to customer {} on account {}: {} (code: {})", 
                            userDTO.getPaymentMethodId(), stripeCustomerId, stripeAccountId != null ? stripeAccountId : "unknown", 
                            e.getMessage(), e.getCode(), e);
                    throw new Exception("Failed to attach payment method: " + e.getMessage() + 
                            (e.getCode() != null ? " (code: " + e.getCode() + ")" : ""), e);
                } catch (IllegalStateException e) {
                    logger.error("❌ Stripe setup issue (new user): {}", e.getMessage());
                    throw e;
                }
            } else if (userDTO.getPaymentMethodId() != null && !userDTO.getPaymentMethodId().isEmpty()) {
                logger.warn("⚠️ Payment method provided but no Stripe customer ID available (new user). Payment method ID: {}", userDTO.getPaymentMethodId());
            } else {
                logger.info("No payment method provided or no customer ID - skipping payment method attachment (new user)");
            }

            // Create memberships immediately if payment method was provided
            if (userDTO.getPaymentMethodId() != null && !userDTO.getPaymentMethodId().isEmpty() && userDTO.getMemberships() != null) {
                UserBusiness savedUserBusiness = user.getUserBusinesses().get(0);

                for (UserBusinessCreateDTO.MembershipRequestDTO membershipRequest : userDTO.getMemberships()) {
                    Membership membership = membershipRepository.findById(membershipRequest.getMembershipId())
                        .orElseThrow(() -> new IllegalArgumentException("Invalid membership ID: " + membershipRequest.getMembershipId()));

                    UserBusinessMembership userBusinessMembership = new UserBusinessMembership();
                    userBusinessMembership.setUserBusiness(savedUserBusiness);
                    userBusinessMembership.setMembership(membership);
                    userBusinessMembership.setStatus(membershipRequest.getStatus() != null ? membershipRequest.getStatus() : "ACTIVE");
                    userBusinessMembership.setAnchorDate(membershipRequest.getAnchorDate() != null ? membershipRequest.getAnchorDate() : LocalDateTime.now());
                    userBusinessMembership.setActualPrice(parsePrice(membership.getPrice()));

                    // Create Stripe subscription
                    createStripeSubscriptionForMembership(userBusinessMembership, business);

                    if (savedUserBusiness.getUserBusinessMemberships() == null) {
                        savedUserBusiness.setUserBusinessMemberships(new ArrayList<>());
                    }
                    savedUserBusiness.getUserBusinessMemberships().add(userBusinessMembership);
                }
                
                // Recalculate status after adding memberships
                userBusinessService.calculateAndUpdateStatus(savedUserBusiness);
            }
        }

        // Save the updated user (cascades to UserBusiness due to @OneToMany cascade)
        User savedUser = userRepository.save(user);
        logger.info("=== COMPLETED handleNewBusiness successfully ===");
        logger.info("User ID: {}, Email: {}, Business: {}", savedUser.getId(), savedUser.getEmail(), business.getBusinessTag());
        return savedUser;
    }

    @Override
    @Transactional
    public User updateUser(User user) {
        return userRepository.save(user);
    }

    @Override
    public boolean existsByReferralCode(String referralCode) {
        return userRepository.existsByReferralCode(referralCode);
    }

    @Override
    public Optional<User> updateProfilePicture(Long id, String imageUrl) {
        return userRepository.findById(id).map(user -> {
            user.setProfilePictureUrl(imageUrl);
            return userRepository.save(user);
        });
    }

    @Override
    @Transactional(readOnly = true) // readOnly = true since we're only reading
    public User signInWithPhoneNumber(String phoneNumber, String password) throws Exception {
        // Find user by phone number
        User user = userRepository.findByPhoneNumber(phoneNumber)
                .orElseThrow(() -> new Exception("User with phone number " + phoneNumber + " not found"));

        // Validate password
        if (!passwordEncoder.matches(password, user.getPassword())) {
            throw new Exception("Invalid password");
        }

        // Return user if credentials are valid
        return user;
    }

    @Override
    public List<User> findAll() {
        return userRepository.findAll();
    }

    @Override
    public List<UserDTOBasic> getAllUserDTOBasics(String clubTag) {
        List<User> users = userRepository.findUsersWithMembershipByClubTag(clubTag);
        return users.stream()
                .map(user -> new UserDTOBasic(
                        user.getId(),
                        user.getFirstName(),
                        user.getLastName(),
                        user.getIsInGoodStanding(),
                        user.isOver18(),
                        user.getSignatureData(),
                        user.getProfilePictureUrl(),
                        user.getMembership()
                ))
                .toList();
    }

    @Override
    public Optional<User> findById(Long id) {
        return userRepository.findById(id);
    }

    @Override
    public User updateUserAfterPayment(String stripeCustomerId, boolean standing) {
        Optional<User> optionalUser = userRepository.findByUserStripeMemberId(stripeCustomerId);
        if (optionalUser.isPresent()) {
            User user = optionalUser.get();
            user.setIsInGoodStanding(standing); // Payment succeeded
            return userRepository.save(user);
        } else {
            throw new RuntimeException("User not found for Stripe customer ID: " + stripeCustomerId);
        }
    }

    @Override
    public Optional<User> getUserByPhoneNumber(String phoneNumber) {
        return userRepository.findByPhoneNumber(phoneNumber);
    }

    @Override
    public Optional<User> deleteUserWithPhoneNumber(String phoneNumber) {
        Optional<User> userOpt = userRepository.findByPhoneNumber(phoneNumber);
        if (userOpt.isPresent()) {
            if (userOpt.get().getParent() != null) {
                User parent = userOpt.get().getParent();
                parent.getChildren().remove(userOpt.get());
                userRepository.save(parent);
            }
            userRepository.delete(userOpt.get());
            return userOpt; // Return the deleted user
        }
        return Optional.empty(); // No user found
    }

    @Override
    public Optional<User> deleteUserByIdOrPhone(Long userId, String phoneNumber) {
        Optional<User> userOpt = Optional.empty();

        if (userId != null) {
            userOpt = userRepository.findById(userId);
        }

        if (userOpt.isEmpty() && phoneNumber != null && !phoneNumber.isBlank()) {
            userOpt = userRepository.findByPhoneNumber(phoneNumber);
        }

        if (userOpt.isPresent()) {
            User user = userOpt.get();
            if (user.getParent() != null) {
                User parent = user.getParent();
                parent.getChildren().remove(user);
                userRepository.save(parent);
            }
            userRepository.delete(user);
            return Optional.of(user);
        }

        return Optional.empty();
    }

    @Override
    public User updateUserPaymentFailed(String stripeCustomerId) {
        Optional<User> optionalUser = userRepository.findByUserStripeMemberId(stripeCustomerId);
        if (optionalUser.isPresent()) {
            User user = optionalUser.get();
            user.setIsInGoodStanding(false); // Payment failed
            return userRepository.save(user);
        } else {
            throw new RuntimeException("User not found for Stripe customer ID: " + stripeCustomerId);
        }
    }

    @Override
    public User save(User user) throws Exception {
        // Encode password
        String encodedPassword = passwordEncoder.encode(user.getPassword());
        user.setPassword(encodedPassword);

        // Generate unique QR code token
        String token = generateUniqueToken(10);
        while (userRepository.findByEntryQrcodeToken(token).isPresent()) {
            token = generateUniqueToken(10); // Regenerate if not unique
        }
        user.setEntryQrcodeToken(token);
        user.setReferralCode(generateUniqueReferralCode());

        // Save user with token
        return userRepository.save(user);
    }

    private String generateUniqueToken(int length) {
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(CHARACTERS.charAt(random.nextInt(CHARACTERS.length())));
        }
        return sb.toString();
    }

    private String generateUniqueReferralCode() {
        String code;
        do {
            code = generateUniqueToken(10);
        } while (userRepository.findByReferralCode(code) != null);
        return code;
    }

    @Override
    public void deleteById(Long id) {
        userRepository.deleteById(id);
    }

    @Override
    public Optional<User> getUserByBarcodeToken(String barcodeToken) {
        return userRepository.findByEntryQrcodeToken(barcodeToken);
    }

    @Override
    public User signIn(Map<String, String> requestBody) throws Exception {
        String phoneNumber = requestBody.get("phoneNumber");
        String password = requestBody.get("password");

        // Basic validation
        if (phoneNumber == null || password == null) {
            throw new Exception("Phone number and password are required");
        }

        Optional<User> optionalUser = userRepository.findByPhoneNumber(phoneNumber);

        if (optionalUser.isEmpty()) {
            throw new Exception("Invalid phone number or password");
        }

        User user = optionalUser.get();
        if (!passwordEncoder.matches(password, user.getPassword())) {
            throw new Exception("Invalid phone number or password");
        }

        return user; // Return the user object on successful login
    }

    @Override
    @Transactional
    public User updateUserOver18(long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found with ID: " + userId));
        user.setIsOver18(true);
        return userRepository.save(user);
    }

    @Override
    public UserDTO addChildToParent(Long parentId, User user) {
        // Encode password
        String encodedPassword = passwordEncoder.encode(user.getPassword());
        user.setPassword(encodedPassword);

        // Generate unique QR code token
        String token = generateUniqueToken(10);
        while (userRepository.findByEntryQrcodeToken(token).isPresent()) {
            token = generateUniqueToken(10); // Regenerate if not unique
        }
        user.setEntryQrcodeToken(token);
        user.setReferralCode(generateUniqueReferralCode());

        // Save user with token
        User child = userRepository.save(user);
        User parent = userRepository.findById(parentId)
                .orElseThrow(() -> new EntityNotFoundException("Parent user not found with ID: " + parentId));

        child.setParent(parent);
        parent.getChildren().add(child);

        userRepository.save(parent);
        userRepository.save(child);

        return new UserDTO(child);
    }

    public List<UserDTO> getAllUsers() {
        List<User> users = userRepository.findAll();
        return users.stream()
                .map(UserDTO::new)
                .collect(Collectors.toList());
    }

    @Transactional
    public UserDTO processBarcodeScan(String barcode) throws Exception {
        User user = getUserByBarcodeToken(barcode)
                .orElseThrow(() -> new Exception("User not found"));

        // Save sign-in log
        SignInLog log = new SignInLog();
        log.setUser(user);
        log.setSignInTime(LocalDateTime.now());
        signInLogRepository.save(log);

        // Load user with sign-in logs initialized
        User userWithLogs = userRepository.findByIdWithSignInLogs(user.getId())
                .orElseThrow(() -> new Exception("User not found after saving log"));

        return new UserDTO(userWithLogs);
    }

    @Override
    @Transactional
    public User updateUserPassword(Long id, String newPassword) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("User not found with ID: " + id));
        String encodedPassword = passwordEncoder.encode(newPassword);
        user.setPassword(encodedPassword);
        return userRepository.save(user);
    }

    /**
     * Helper method to create Stripe subscription for a membership
     * @param userBusinessMembership The UserBusinessMembership to create a subscription for
     * @param business The business the user belongs to
     */
    private void createStripeSubscriptionForMembership(UserBusinessMembership userBusinessMembership, Business business) {
        try {
            // Skip if subscription ID is already provided
            if (userBusinessMembership.getStripeSubscriptionId() != null && !userBusinessMembership.getStripeSubscriptionId().isEmpty()) {
                System.out.println("Stripe subscription ID already exists: " + userBusinessMembership.getStripeSubscriptionId());
                return;
            }

            Membership membership = userBusinessMembership.getMembership();
            String stripePriceId = membership.getStripePriceId();

            // Skip if membership doesn't have a Stripe Price ID
            if (stripePriceId == null || stripePriceId.isEmpty()) {
                System.out.println("Membership " + membership.getId() + " does not have a Stripe Price ID. Skipping subscription creation.");
                return;
            }

            // Get the business's Stripe account
            String stripeAccountId = business.getStripeAccountId();
            if (stripeAccountId == null) {
                System.err.println("Client or Stripe account ID not found for business: " + business.getBusinessTag());
                return;
            }

            // Get the customer's Stripe ID from UserBusiness
            UserBusiness userBusiness = userBusinessMembership.getUserBusiness();
            String stripeCustomerId = userBusiness.getStripeId();

            if (stripeCustomerId == null || stripeCustomerId.isEmpty()) {
                System.err.println("No Stripe customer ID found for UserBusiness. Cannot create subscription.");
                return;
            }

            // Create the Stripe subscription
            LocalDateTime anchorDate = userBusinessMembership.getAnchorDate();
            BigDecimal actualPrice = determineActualPrice(userBusinessMembership);
            Subscription subscription = stripeService.createSubscription(
                    stripeCustomerId,
                    stripePriceId,
                    stripeAccountId,
                    anchorDate,
                    null,
                    actualPrice
            );

            // Store the subscription ID
            userBusinessMembership.setStripeSubscriptionId(subscription.getId());
            System.out.println("Created Stripe subscription: " + subscription.getId() + " for membership " + membership.getId());

        } catch (StripeException e) {
            System.err.println("Failed to create Stripe subscription for membership " + userBusinessMembership.getId() + ": " + e.getMessage());
            // Don't throw - allow the user creation to continue even if Stripe fails
        } catch (Exception e) {
            System.err.println("Unexpected error creating Stripe subscription: " + e.getMessage());
        }
    }

    private BigDecimal determineActualPrice(UserBusinessMembership membership) {
        BigDecimal price = membership.getActualPrice();
        if (price == null || price.compareTo(BigDecimal.ZERO) <= 0) {
            price = parsePrice(membership.getMembership().getPrice());
            membership.setActualPrice(price);
        }
        return price;
    }

    private BigDecimal parsePrice(String priceStr) {
        if (priceStr == null || priceStr.isEmpty()) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        String normalized = priceStr.replaceAll("[^0-9.]", "");
        if (normalized.isEmpty()) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        try {
            return new BigDecimal(normalized).setScale(2, RoundingMode.HALF_UP);
        } catch (NumberFormatException e) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
    }
}