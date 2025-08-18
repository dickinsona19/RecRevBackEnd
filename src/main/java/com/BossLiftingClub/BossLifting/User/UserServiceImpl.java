package com.BossLiftingClub.BossLifting.User;


import com.BossLiftingClub.BossLifting.User.SignInLog.SignInLog;
import com.BossLiftingClub.BossLifting.User.SignInLog.SignInLogRepository;
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
    public List<UserDTOBasic> getAllUserDTOBasics(){return userRepository.findAllUserDTOBasic();};
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
    public Optional<User> getUserByBarcodeToken(String barcodeToken){
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


}
