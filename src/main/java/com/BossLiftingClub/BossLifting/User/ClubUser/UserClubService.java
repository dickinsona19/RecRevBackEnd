package com.BossLiftingClub.BossLifting.User.ClubUser;

import com.BossLiftingClub.BossLifting.Club.Club;
import com.BossLiftingClub.BossLifting.Club.ClubRepository;
import com.BossLiftingClub.BossLifting.User.Membership.Membership;
import com.BossLiftingClub.BossLifting.User.User;
import com.BossLiftingClub.BossLifting.User.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
public class UserClubService {

    @Autowired
    private UserClubRepository userClubRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ClubRepository clubRepository;

    /**
     * Create a new user-club relationship
     */
    @Transactional
    public UserClub createUserClubRelationship(Long userId, String clubTag, Long membershipId, String status) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found with id: " + userId));

        Club club = clubRepository.findByClubTag(clubTag)
                .orElseThrow(() -> new RuntimeException("Club not found with tag: " + clubTag));

        // Check if relationship already exists
        Optional<UserClub> existingRelationship = userClubRepository.findByUserIdAndClubTag(userId, clubTag);
        if (existingRelationship.isPresent()) {
            throw new RuntimeException("User is already a member of this club");
        }

        UserClub userClub = new UserClub();
        userClub.setUser(user);
        userClub.setClub(club);
        userClub.setStatus(status);
        userClub.setStripeId(null); // Will be set when payment is processed

        // Membership can be null initially
        if (membershipId != null) {
            // You'll need to fetch the membership if needed
            // For now, we'll set it later
        }

        return userClubRepository.save(userClub);
    }

    /**
     * Get all users for a specific club by clubTag
     */
    @Transactional(readOnly = true)
    public List<UserClub> getUsersByClubTag(String clubTag) {
        return userClubRepository.findAllByClubTag(clubTag);
    }

    /**
     * Update the status of a user-club relationship
     */
    @Transactional
    public UserClub updateUserClubStatus(Long userId, String clubTag, String newStatus) {
        UserClub userClub = userClubRepository.findByUserIdAndClubTag(userId, clubTag)
                .orElseThrow(() -> new RuntimeException("User-club relationship not found"));

        userClub.setStatus(newStatus);
        return userClubRepository.save(userClub);
    }

    /**
     * Update the Stripe ID for a user-club relationship
     */
    @Transactional
    public UserClub updateUserClubStripeId(Long userId, String clubTag, String stripeId) {
        UserClub userClub = userClubRepository.findByUserIdAndClubTag(userId, clubTag)
                .orElseThrow(() -> new RuntimeException("User-club relationship not found"));

        userClub.setStripeId(stripeId);
        return userClubRepository.save(userClub);
    }

    /**
     * Remove a user from a club
     */
    @Transactional
    public void removeUserFromClub(Long userId, String clubTag) {
        UserClub userClub = userClubRepository.findByUserIdAndClubTag(userId, clubTag)
                .orElseThrow(() -> new RuntimeException("User-club relationship not found"));

        userClubRepository.delete(userClub);
    }

    /**
     * Get all clubs for a specific user
     */
    @Transactional(readOnly = true)
    public List<UserClub> getClubsForUser(Long userId) {
        return userClubRepository.findAllByUserId(userId);
    }

    /**
     * Get a specific user-club relationship
     */
    @Transactional(readOnly = true)
    public Optional<UserClub> getUserClubRelationship(Long userId, String clubTag) {
        return userClubRepository.findByUserIdAndClubTag(userId, clubTag);
    }
}
