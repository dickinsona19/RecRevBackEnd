package com.BossLiftingClub.BossLifting.User;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface UserService {
    List<User> findAll();
    Optional<User> findById(Long id);
    User save(User user) throws Exception;
    void deleteById(Long id);
    Optional<User> getUserByBarcodeToken(String barcodeToken);
    User updateUserAfterPayment(String stripeCustomerId, boolean standing);
    User updateUserPaymentFailed(String stripeCustomerId);
    Optional<User> getUserByPhoneNumber(String phoneNumber);
    Optional<User> deleteUserWithPhoneNumber(String PhoneNumber);
    User signIn(Map<String, String> requestBody) throws Exception;
    User signInWithPhoneNumber(String phoneNumber, String password) throws Exception;
    Optional<User> updateProfilePicture(Long id, String profilePicture);
    User getUserByReferralCode(String referralCode);
    User updateUser(User user);
    boolean existsByReferralCode(String referralCode);
    Optional<User> updateWaiverSignature(Long userId, String imageUrl);
    boolean updateReferralCode(String referralCode, String newReferralCode);
    User updateUserOver18(long userId);
    UserDTO addChildToParent(Long parentId, User user);
    UserDTO processBarcodeScan(String barcode) throws Exception;
}
