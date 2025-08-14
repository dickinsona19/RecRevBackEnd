package com.BossLiftingClub.BossLifting.User.PotentialUser;

import com.BossLiftingClub.BossLifting.Promo.Promo;
import com.BossLiftingClub.BossLifting.Promo.PromoRepository;
import com.BossLiftingClub.BossLifting.User.FirebaseService;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import jakarta.persistence.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.http.HttpStatus;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Nullable;
import java.io.IOException;
import java.util.*;

// Entity
@Entity
@Table(name = "potential_user") // Explicitly specify the table name
public class PotentialUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id") // Matches the database column name
    private Long id;

    @Column(name = "first_name") // Maps to first_name in the database
    private String firstName;

    @Column(name = "last_name") // Maps to last_name in the database
    private String lastName;

    @Column(name = "email") // Matches the database column name
    private String email;

    @Column(name = "waiver_signature") // Maps to waiver_signature in the database
    private String waiverSignature;

    @Column(name = "phone_number") // Maps to phone_number in the database
    private String phoneNumber;

    @Column(name = "has_reddemed_free_pass") // Maps to has_reddemed_free_pass in the database
    private boolean hasReddemedFreePass = false;

    // Default constructor
    public PotentialUser() {}

    // Parameterized constructor
    public PotentialUser(String firstName, String lastName, String email, String waiverSignature, String phoneNumber, boolean hasReddemedFreePass) {
        this.firstName = firstName;
        this.lastName = lastName;
        this.email = email;
        this.waiverSignature = waiverSignature;
        this.phoneNumber = phoneNumber;
        this.hasReddemedFreePass = hasReddemedFreePass;
    }
    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getFirstName() { return firstName; }
    public void setFirstName(String firstName) { this.firstName = firstName; }

    public String getLastName() { return lastName; }
    public void setLastName(String lastName) { this.lastName = lastName; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getWaiverSignature() { return waiverSignature; }
    public void setWaiverSignature(String waiverSignature) { this.waiverSignature = waiverSignature; }

    public String getPhoneNumber() { return phoneNumber; }
    public void setPhoneNumber(String phoneNumber) { this.phoneNumber = phoneNumber; }

    public boolean isHasReddemedFreePass() { return hasReddemedFreePass; }
    public void setHasReddemedFreePass(boolean hasReddemedFreePass) { this.hasReddemedFreePass = hasReddemedFreePass; }
}

// Repository
interface PotentialUserRepository extends JpaRepository<PotentialUser, Long> {
    PotentialUser findByEmail(String email);
}

// Service
@Service
class PotentialUserService {
    @Autowired
    private PotentialUserRepository repository;
    @Autowired
    private PromoRepository promoRepository;
    private final PotentialUserRepository potentialUserRepository;

    public PotentialUserService(PotentialUserRepository potentialUserRepository) {
        this.potentialUserRepository = potentialUserRepository;
    }
    public List<PotentialUser> getAllUsers() {
        return repository.findAll();
    }

    public PotentialUser addUser(PotentialUser user, String promoCode) {
        // Check if a user with the same email already exists
        PotentialUser existingUser = repository.findByEmail(user.getEmail());
        if (existingUser != null) {
            return existingUser; // Don't add a new one, return the existing user
        }

        if (promoCode != null) {
            // Search for the promo
            Optional<Promo> optionalPromo = promoRepository.findByCodeToken(promoCode);
            if (optionalPromo.isPresent()) {
                Promo promo = optionalPromo.get();
                // Increment freePassCount if promo exists
                promo.setFreePassCount(promo.getFreePassCount() + 1);
                promoRepository.save(promo);
            }
        }

        // Save and return the new user
        return repository.save(user);
    }

    public void deleteUser(Long id) {
        repository.deleteById(id);
    }

    public Optional<PotentialUser> updateWaiverSignature(Long id, String imageUrl) {
        Optional<PotentialUser> potentialUserOpt = potentialUserRepository.findById(id);
        if (potentialUserOpt.isPresent()) {
            PotentialUser potentialUser = potentialUserOpt.get();
            potentialUser.setWaiverSignature(imageUrl); // Assuming a field like waiverSignature exists
            return Optional.of(potentialUserRepository.save(potentialUser));
        }
        return Optional.empty();
    }
    public Optional<PotentialUser> updateFreePassStatus(Long id, boolean hasRedeemedFreePass) {
        Optional<PotentialUser> potentialUserOpt = potentialUserRepository.findById(id);
        if (potentialUserOpt.isPresent()) {
            PotentialUser potentialUser = potentialUserOpt.get();
            potentialUser.setHasReddemedFreePass(hasRedeemedFreePass);
            return Optional.of(potentialUserRepository.save(potentialUser));
        }
        return Optional.empty();
    }
}

// Controller
@RestController
@RequestMapping("/api/potential-users")
class PotentialUserController {
    @Autowired
    private PotentialUserService service;

    private final FirebaseService firebaseService;

    @Autowired
    private JavaMailSender mailSender;

    @Autowired
    private PotentialUserRepository potentialUserRepository;

    private static final String NEW_CONTACT_EMAIL = "contact@cltliftingclub.com";
    public PotentialUserController(PotentialUserService potentialUserService, FirebaseService firebaseService) {
        this.service = potentialUserService;
        this.firebaseService = firebaseService;
    }
    @GetMapping
    public List<PotentialUser> getAllUsers() {
        return service.getAllUsers();
    }

    @PostMapping
    public PotentialUser addUser(@RequestBody PotentialUser user, @RequestParam(name = "from", required = false) @Nullable String promoCode) {
        return service.addUser(user, promoCode);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteUser(@PathVariable Long id) {
        service.deleteUser(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/waiver")
    public ResponseEntity<PotentialUser> saveWaiverSignature(
            @PathVariable Long id,
            @RequestParam("file") MultipartFile file) {

        try {
            // Upload to Firebase and get public URL
            String imageUrl = firebaseService.uploadImage(file);

            // Save the image URL as waiver signature for the potential user
            return service.updateWaiverSignature(id, imageUrl)
                    .map(ResponseEntity::ok)
                    .orElseGet(() -> ResponseEntity.notFound().build());

        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    @PostMapping("/{id}/free-pass")
    public ResponseEntity<PotentialUser> redeemFreePass(@PathVariable Long id) {
        return service.updateFreePassStatus(id, true)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
    @PostMapping("/send-emails")
    public ResponseEntity<Map<String, List<String>>> sendPotentialUserInviteEmails() {
        List<String> successes = new ArrayList<>();
        List<String> failures = new ArrayList<>();

        List<PotentialUser> potentialUsers = potentialUserRepository.findAll();

        for (PotentialUser potentialUser : potentialUsers) {
            String email = potentialUser.getEmail();
            if (email == null || email.isEmpty()) {
                failures.add("PotentialUser ID " + potentialUser.getId() + ": No email found");
                continue;
            }

            try {
                // Send email
                MimeMessage mimeMessage = mailSender.createMimeMessage();
                MimeMessageHelper helper = null;
                try {
                    helper = new MimeMessageHelper(mimeMessage, true, "UTF-8");
                } catch (MessagingException e) {
                    throw new RuntimeException(e);
                }
                helper.setTo(email);
                helper.setFrom(NEW_CONTACT_EMAIL);
                helper.setSubject("Don’t Miss This! – CLT Lifting Club x Kingdom Kickbacks Social Event");
                String htmlContent = """
    <!DOCTYPE html>
    <html>
    <head>
        <style>
            body { font-family: Arial, sans-serif; color: #333; }
            .container { max-width: 600px; margin: 0 auto; padding: 20px; }
            .header { background-color: #f8f8f8; padding: 10px; text-align: center; }
            .content { padding: 20px; }
            .footer { font-size: 12px; color: #777; text-align: center; }
            a.button {
                display: inline-block;
                padding: 10px 15px;
                background-color: #007BFF;
                color: white !important;
                text-decoration: none;
                border-radius: 5px;
                font-weight: bold;
            }
            a.button:hover {
                background-color: #0056b3;
            }
        </style>
    </head>
    <body>
        <div class="container">
            <div class="header">
                <h2>CLT Lifting Club</h2>
            </div>
            <div class="content">
                <p>CLT Lifting Club is teaming up with Kingdom Kickbacks for an epic Open Gym Social — a day packed with fitness, connections, and memories you won’t want to miss.</p>
                <p><strong>Here’s what’s going down:</strong></p>
                <ul>
                    <li>Food Truck: Smart Eats</li>
                    <li>Coffee Cart: Breezeway Coffee</li>
                    <li>Cold Plunge: Plunge House</li>
                    <li>Saunas to recover and recharge</li>
                    <li>Live DJ for the perfect workout vibe</li>
                    <li>Full Gym Access + fun fitness challenges</li>
                    <li><strong>FREE for you and your friends</strong></li>
                </ul>
                <p><strong>Date:</strong> Saturday, August 16th | 10 AM – 1 PM</p>
                <p><strong>Location:</strong> CLT Lifting Club, 3100 South Boulevard, Charlotte, NC 28203</p>
                <p><strong>Bonus:</strong> Post a workout or event hype photo/video on August 16th using #CLTLiftingClub and tag @CLTLiftingClub for your chance to win a free CLT tee.</p>
                <p><a href="https://www.evite.com/signup-sheet/6025706806444032/?utm_campaign=send_sharable_link&utm_source=evitelink&utm_medium=sharable_invite" class="button">RSVP NOW</a> to let us know you’re coming, walk-ins are still welcome!</p>
                <p>Let’s make this the best South End community event of the summer.</p>
                <p>See you there,<br>The CLT Lifting Club Team</p>
            </div>
            <div class="footer">
                <p>CLT Lifting Club | %s</p>
            </div>
        </div>
    </body>
    </html>
    """.formatted(NEW_CONTACT_EMAIL);
                helper.setText(htmlContent, true);
                mailSender.send(mimeMessage);


                successes.add("PotentialUser ID " + potentialUser.getId() + ": Email sent to " + email);
            } catch (MessagingException e) {
                failures.add("PotentialUser ID " + potentialUser.getId() + ": Email sending failed for " + email + " - " + e.getMessage());
            }
        }

        Map<String, List<String>> response = new HashMap<>();
        response.put("successes", successes);
        response.put("failures", failures);
        return ResponseEntity.ok(response);
    }
    @PostMapping("/clean-duplicates")
    @Transactional
    public ResponseEntity<String> cleanDuplicateEmails() {
        List<PotentialUser> potentialUsers = potentialUserRepository.findAll();
        Map<String, PotentialUser> emailToUser = new HashMap<>();
        int deletedCount = 0;

        for (PotentialUser user : potentialUsers) {
            String email = user.getEmail();
            if (emailToUser.containsKey(email)) {
                potentialUserRepository.delete(user);
                deletedCount++;
            } else {
                emailToUser.put(email, user);
            }
        }

        return ResponseEntity.ok("Deleted " + deletedCount + " duplicate potential user(s)");
    }

}