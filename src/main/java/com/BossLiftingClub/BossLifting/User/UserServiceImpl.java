package com.BossLiftingClub.BossLifting.User;

import com.BossLiftingClub.BossLifting.Client.Client;
import com.BossLiftingClub.BossLifting.Club.Club;
import com.BossLiftingClub.BossLifting.Club.ClubRepository;
import com.BossLiftingClub.BossLifting.Stripe.StripeService;
import com.BossLiftingClub.BossLifting.User.ClubUser.UserClub;
import com.BossLiftingClub.BossLifting.User.ClubUser.UserClubMembership;
import com.BossLiftingClub.BossLifting.User.ClubUser.UserClubRepository;
import com.BossLiftingClub.BossLifting.User.ClubUser.UserCreateDTO;
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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final SecureRandom random = new SecureRandom();
    private static final String CHARACTERS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    private final PasswordEncoder passwordEncoder;

    @Autowired
    private SignInLogRepository signInLogRepository;

    @Autowired
    private JavaMailSender mailSender;

    @Autowired
    private ClubRepository clubRepository;

    @Autowired
    private UserClubRepository userClubRepository;

    @Autowired
    private MembershipRepository membershipRepository;

    @Autowired
    private StripeService stripeService;

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
    public User handleNewClub(UserCreateDTO userDTO) throws Exception {
        // Validate input
        if (userDTO.getEmail() == null || userDTO.getEmail().trim().isEmpty()) {
            throw new IllegalArgumentException("Email is required");
        }
        if (userDTO.getClubMembership() == null || userDTO.getClubMembership().getClubId() == null) {
            throw new IllegalArgumentException("Club membership is required");
        }

        // Check if user exists by email
        Optional<User> existingUserOpt = userRepository.findByEmail(userDTO.getEmail());
        User user;

        // Fetch the club (common for both branches)
        Club club = clubRepository.findById(userDTO.getClubMembership().getClubId())
                .orElseThrow(() -> new IllegalArgumentException("Invalid club ID: " + userDTO.getClubMembership().getClubId()));

        if (existingUserOpt.isPresent()) {
            // User exists, check if they're already in this club
            user = existingUserOpt.get();

            // Check if user is already a member of this club
            if (userClubRepository.existsByUser_IdAndClub_Id(user.getId(), club.getId())) {
                throw new IllegalArgumentException("User with email " + userDTO.getEmail() + " is already a member of this club");
            }

            // Add new club membership
            List<UserClub> userClubs = user.getUserClubs();
            if (userClubs == null) {
                userClubs = new ArrayList<>();
                user.setUserClubs(userClubs);
            }

            // Create UserClub entry
            UserClub userClub = new UserClub();
            userClub.setUser(user);
            userClub.setClub(club);
            userClub.setStatus(userDTO.getClubMembership().getStatus() != null ? userDTO.getClubMembership().getStatus() : "ACTIVE");

            // Create Stripe customer on the connected account if not provided
            String stripeCustomerId = userDTO.getClubMembership().getStripeId();
            if (stripeCustomerId == null || stripeCustomerId.isEmpty()) {
                try {
                    Client client = club.getClient();
                    if (client != null && client.getStripeAccountId() != null) {
                        String fullName = user.getFirstName() + " " + user.getLastName();
                        stripeCustomerId = stripeService.createCustomerOnConnectedAccount(
                            user.getEmail(),
                            fullName,
                            client.getStripeAccountId()
                        );
                        System.out.println("Created Stripe customer: " + stripeCustomerId + " for user " + user.getEmail());
                    } else {
                        System.err.println("Cannot create Stripe customer: Club client or Stripe account ID not found");
                    }
                } catch (StripeException e) {
                    System.err.println("Failed to create Stripe customer: " + e.getMessage());
                    // Don't throw - allow user creation to continue
                }
            }
            userClub.setStripeId(stripeCustomerId != null ? stripeCustomerId : "");

            // Save the UserClub first before processing memberships
            userClubs.add(userClub);
            user = userRepository.save(user);

            // If payment method is provided, attach it immediately
            if (userDTO.getPaymentMethodId() != null && !userDTO.getPaymentMethodId().isEmpty() && stripeCustomerId != null) {
                try {
                    Client client = club.getClient();
                    if (client != null && client.getStripeAccountId() != null) {
                        stripeService.attachPaymentMethodOnConnectedAccount(
                            stripeCustomerId,
                            userDTO.getPaymentMethodId(),
                            client.getStripeAccountId()
                        );
                        System.out.println("✅ Payment method " + userDTO.getPaymentMethodId() + " attached to customer " + stripeCustomerId);
                    }
                } catch (StripeException e) {
                    System.err.println("Failed to attach payment method: " + e.getMessage());
                    throw new Exception("Failed to attach payment method: " + e.getMessage());
                }
            }

            // Create memberships immediately if payment method was provided
            if (userDTO.getPaymentMethodId() != null && !userDTO.getPaymentMethodId().isEmpty() && userDTO.getMemberships() != null) {
                UserClub savedUserClub = user.getUserClubs().stream()
                    .filter(uc -> uc.getClub().getId().equals(club.getId()))
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException("UserClub not found after save"));

                for (UserCreateDTO.MembershipRequestDTO membershipRequest : userDTO.getMemberships()) {
                    Membership membership = membershipRepository.findById(membershipRequest.getMembershipId())
                        .orElseThrow(() -> new IllegalArgumentException("Invalid membership ID: " + membershipRequest.getMembershipId()));

                    UserClubMembership userClubMembership = new UserClubMembership();
                    userClubMembership.setUserClub(savedUserClub);
                    userClubMembership.setMembership(membership);
                    userClubMembership.setStatus(membershipRequest.getStatus() != null ? membershipRequest.getStatus() : "ACTIVE");
                    userClubMembership.setAnchorDate(membershipRequest.getAnchorDate() != null ? membershipRequest.getAnchorDate() : LocalDateTime.now());

                    // Create Stripe subscription
                    createStripeSubscriptionForMembership(userClubMembership, club);

                    if (savedUserClub.getUserClubMemberships() == null) {
                        savedUserClub.setUserClubMemberships(new ArrayList<>());
                    }
                    savedUserClub.getUserClubMemberships().add(userClubMembership);
                }
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

            // Create UserClub entry
            UserClub userClub = new UserClub();
            userClub.setUser(user);
            userClub.setClub(club);
            userClub.setStatus(userDTO.getClubMembership().getStatus() != null ? userDTO.getClubMembership().getStatus() : "ACTIVE");

            // Create Stripe customer on the connected account if not provided
            String stripeCustomerId = userDTO.getClubMembership().getStripeId();
            if (stripeCustomerId == null || stripeCustomerId.isEmpty()) {
                try {
                    Client client = club.getClient();
                    if (client != null && client.getStripeAccountId() != null) {
                        String fullName = user.getFirstName() + " " + user.getLastName();
                        stripeCustomerId = stripeService.createCustomerOnConnectedAccount(
                            user.getEmail(),
                            fullName,
                            client.getStripeAccountId()
                        );
                        System.out.println("Created Stripe customer: " + stripeCustomerId + " for user " + user.getEmail());
                    } else {
                        System.err.println("Cannot create Stripe customer: Club client or Stripe account ID not found");
                    }
                } catch (StripeException e) {
                    System.err.println("Failed to create Stripe customer: " + e.getMessage());
                    // Don't throw - allow user creation to continue
                }
            }
            userClub.setStripeId(stripeCustomerId != null ? stripeCustomerId : "");

            user.setUserClubs(new ArrayList<>(List.of(userClub)));

            // Save user first to get IDs
            user = userRepository.save(user);

            // If payment method is provided, attach it immediately
            if (userDTO.getPaymentMethodId() != null && !userDTO.getPaymentMethodId().isEmpty() && stripeCustomerId != null) {
                try {
                    Client client = club.getClient();
                    if (client != null && client.getStripeAccountId() != null) {
                        stripeService.attachPaymentMethodOnConnectedAccount(
                            stripeCustomerId,
                            userDTO.getPaymentMethodId(),
                            client.getStripeAccountId()
                        );
                        System.out.println("✅ Payment method " + userDTO.getPaymentMethodId() + " attached to customer " + stripeCustomerId);
                    }
                } catch (StripeException e) {
                    System.err.println("Failed to attach payment method: " + e.getMessage());
                    throw new Exception("Failed to attach payment method: " + e.getMessage());
                }
            }

            // Create memberships immediately if payment method was provided
            if (userDTO.getPaymentMethodId() != null && !userDTO.getPaymentMethodId().isEmpty() && userDTO.getMemberships() != null) {
                UserClub savedUserClub = user.getUserClubs().get(0);

                for (UserCreateDTO.MembershipRequestDTO membershipRequest : userDTO.getMemberships()) {
                    Membership membership = membershipRepository.findById(membershipRequest.getMembershipId())
                        .orElseThrow(() -> new IllegalArgumentException("Invalid membership ID: " + membershipRequest.getMembershipId()));

                    UserClubMembership userClubMembership = new UserClubMembership();
                    userClubMembership.setUserClub(savedUserClub);
                    userClubMembership.setMembership(membership);
                    userClubMembership.setStatus(membershipRequest.getStatus() != null ? membershipRequest.getStatus() : "ACTIVE");
                    userClubMembership.setAnchorDate(membershipRequest.getAnchorDate() != null ? membershipRequest.getAnchorDate() : LocalDateTime.now());

                    // Create Stripe subscription
                    createStripeSubscriptionForMembership(userClubMembership, club);

                    if (savedUserClub.getUserClubMemberships() == null) {
                        savedUserClub.setUserClubMemberships(new ArrayList<>());
                    }
                    savedUserClub.getUserClubMemberships().add(userClubMembership);
                }
            }
        }

        // Save the updated user (cascades to UserClub due to @OneToMany cascade)
        return userRepository.save(user);
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
     * @param userClubMembership The UserClubMembership to create a subscription for
     * @param club The club the user belongs to
     */
    private void createStripeSubscriptionForMembership(UserClubMembership userClubMembership, Club club) {
        try {
            // Skip if subscription ID is already provided
            if (userClubMembership.getStripeSubscriptionId() != null && !userClubMembership.getStripeSubscriptionId().isEmpty()) {
                System.out.println("Stripe subscription ID already exists: " + userClubMembership.getStripeSubscriptionId());
                return;
            }

            Membership membership = userClubMembership.getMembership();
            String stripePriceId = membership.getStripePriceId();

            // Skip if membership doesn't have a Stripe Price ID
            if (stripePriceId == null || stripePriceId.isEmpty()) {
                System.out.println("Membership " + membership.getId() + " does not have a Stripe Price ID. Skipping subscription creation.");
                return;
            }

            // Get the club's client and Stripe account
            Client client = club.getClient();
            if (client == null || client.getStripeAccountId() == null) {
                System.err.println("Client or Stripe account ID not found for club: " + club.getClubTag());
                return;
            }

            // Get the customer's Stripe ID from UserClub
            UserClub userClub = userClubMembership.getUserClub();
            String stripeCustomerId = userClub.getStripeId();

            if (stripeCustomerId == null || stripeCustomerId.isEmpty()) {
                System.err.println("No Stripe customer ID found for UserClub. Cannot create subscription.");
                return;
            }

            // Create the Stripe subscription
            LocalDateTime anchorDate = userClubMembership.getAnchorDate();
            Subscription subscription = stripeService.createSubscription(
                    stripeCustomerId,
                    stripePriceId,
                    client.getStripeAccountId(),
                    anchorDate
            );

            // Store the subscription ID
            userClubMembership.setStripeSubscriptionId(subscription.getId());
            System.out.println("Created Stripe subscription: " + subscription.getId() + " for membership " + membership.getId());

        } catch (StripeException e) {
            System.err.println("Failed to create Stripe subscription for membership " + userClubMembership.getId() + ": " + e.getMessage());
            // Don't throw - allow the user creation to continue even if Stripe fails
        } catch (Exception e) {
            System.err.println("Unexpected error creating Stripe subscription: " + e.getMessage());
        }
    }
}