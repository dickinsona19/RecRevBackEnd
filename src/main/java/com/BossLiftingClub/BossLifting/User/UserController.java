package com.BossLiftingClub.BossLifting.User;

import com.BossLiftingClub.BossLifting.User.BusinessUser.UserBusinessCreateDTO;
import com.BossLiftingClub.BossLifting.User.BusinessUser.UserBusinessService;
import com.BossLiftingClub.BossLifting.User.FamilyInvitation;
import com.BossLiftingClub.BossLifting.User.FamilyInvitationRepository;
import com.BossLiftingClub.BossLifting.User.PasswordAuth.JwtUtil;
import com.BossLiftingClub.BossLifting.User.SignInLog.SignInLog;
import com.BossLiftingClub.BossLifting.User.SignInLog.SignInLogRepository;
import com.stripe.model.Customer;
import com.stripe.model.billingportal.Session;
import org.springframework.security.crypto.password.PasswordEncoder;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import jakarta.validation.Valid;
import org.checkerframework.checker.units.qual.A;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping({"/users", "/api/users"})
public class UserController {
    private static final Logger logger = LoggerFactory.getLogger(UserController.class);

    private final BarcodeService barcodeService;
    private final UserService userService;
    private final FirebaseService firebaseService;

    @Autowired
    private JavaMailSender mailSender;

    @Autowired
    private UserRepository userRepository;
    @Autowired
    private SignInLogRepository signInLogRepository;
    @Autowired
    private UserBusinessService userBusinessService;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private FamilyInvitationRepository familyInvitationRepository;
    
    @Autowired
    public UserController(UserService userService, BarcodeService barcodeService, FirebaseService firebaseService) {
        this.userService = userService;
        this.barcodeService = barcodeService;
        this.firebaseService = firebaseService;
    }

    /**
     * Get family members (children/dependents) of a user
     * GET /users/{userId}/family-members
     */
    @GetMapping("/{userId}/family-members")
    public ResponseEntity<List<UserDTO>> getFamilyMembers(@PathVariable Long userId) {
        try {
            Optional<User> userOpt = userRepository.findById(userId);
            if (userOpt.isEmpty()) {
                return ResponseEntity.notFound().build();
            }

            User user = userOpt.get();
            List<UserDTO> familyMembers = user.getChildren().stream()
                    .map(UserDTO::new)
                    .toList();

            return ResponseEntity.ok(familyMembers);
        } catch (Exception e) {
            logger.error("Error fetching family members for user {}: {}", userId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Get pending family invitations for a primary owner
     * GET /users/{userId}/pending-invitations
     */
    @GetMapping("/{userId}/pending-invitations")
    public ResponseEntity<List<Map<String, Object>>> getPendingInvitations(@PathVariable Long userId) {
        try {
            List<FamilyInvitation> invitations = familyInvitationRepository
                .findByPrimaryOwnerIdAndStatus(userId, FamilyInvitation.InvitationStatus.PENDING);
            
            List<Map<String, Object>> invitationDTOs = invitations.stream()
                .map(inv -> {
                    Map<String, Object> dto = new HashMap<>();
                    dto.put("id", inv.getId());
                    dto.put("email", inv.getInvitedEmail());
                    dto.put("firstName", inv.getInvitedFirstName());
                    dto.put("lastName", inv.getInvitedLastName());
                    dto.put("status", inv.getStatus().toString());
                    dto.put("createdAt", inv.getCreatedAt());
                    dto.put("membershipId", inv.getMembershipId());
                    dto.put("customPrice", inv.getCustomPrice());
                    dto.put("businessTag", inv.getBusinessTag());
                    return dto;
                })
                .toList();
            
            return ResponseEntity.ok(invitationDTOs);
        } catch (Exception e) {
            logger.error("Error fetching pending invitations for user {}: {}", userId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Get the primary owner (parent) of a user
     * GET /users/{userId}/primary-owner
     */
    @GetMapping("/{userId}/primary-owner")
    public ResponseEntity<UserDTO> getPrimaryOwner(@PathVariable Long userId) {
        try {
            Optional<User> userOpt = userRepository.findById(userId);
            if (userOpt.isEmpty()) {
                return ResponseEntity.notFound().build();
            }

            User user = userOpt.get();
            if (user.getParent() == null) {
                // This user is a Primary Owner (no parent)
                return ResponseEntity.ok(new UserDTO(user));
            }

            // Return the parent (Primary Owner)
            return ResponseEntity.ok(new UserDTO(user.getParent()));
        } catch (Exception e) {
            logger.error("Error fetching primary owner for user {}: {}", userId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Link a user as a dependent (family member) to a primary owner
     * POST /users/{primaryOwnerId}/add-family-member
     * Body: { "dependentUserId": 123 }
     */
    @PostMapping("/{primaryOwnerId}/add-family-member")
    public ResponseEntity<?> addFamilyMember(
            @PathVariable Long primaryOwnerId,
            @RequestBody Map<String, Long> request) {
        try {
            Long dependentUserId = request.get("dependentUserId");
            if (dependentUserId == null) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "dependentUserId is required"));
            }

            Optional<User> primaryOwnerOpt = userRepository.findById(primaryOwnerId);
            if (primaryOwnerOpt.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("error", "Primary owner not found"));
            }

            Optional<User> dependentOpt = userRepository.findById(dependentUserId);
            if (dependentOpt.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("error", "Dependent user not found"));
            }

            User primaryOwner = primaryOwnerOpt.get();
            User dependent = dependentOpt.get();

            // Check if dependent already has a parent
            if (dependent.getParent() != null && !dependent.getParent().getId().equals(primaryOwnerId)) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "User already has a different primary owner"));
            }

            // Prevent circular reference (can't be your own parent)
            if (primaryOwnerId.equals(dependentUserId)) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "A user cannot be their own primary owner"));
            }

            // Prevent parent from being a child of their dependent
            if (primaryOwner.getParent() != null && primaryOwner.getParent().getId().equals(dependentUserId)) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "Circular reference detected"));
            }

            // Link the dependent to the primary owner
            dependent.setParent(primaryOwner);
            userRepository.save(dependent);

            logger.info("Linked user {} as dependent to primary owner {}", dependentUserId, primaryOwnerId);
            return ResponseEntity.ok(Map.of(
                    "message", "Family member added successfully",
                    "primaryOwner", new UserDTO(primaryOwner),
                    "dependent", new UserDTO(dependent)
            ));
        } catch (Exception e) {
            logger.error("Error adding family member: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to add family member: " + e.getMessage()));
        }
    }

    /**
     * Remove a dependent from a primary owner (unlink family member)
     * DELETE /users/{primaryOwnerId}/remove-family-member/{dependentUserId}
     */
    @DeleteMapping("/{primaryOwnerId}/remove-family-member/{dependentUserId}")
    public ResponseEntity<?> removeFamilyMember(
            @PathVariable Long primaryOwnerId,
            @PathVariable Long dependentUserId) {
        try {
            Optional<User> dependentOpt = userRepository.findById(dependentUserId);
            if (dependentOpt.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("error", "Dependent user not found"));
            }

            User dependent = dependentOpt.get();
            if (dependent.getParent() == null || !dependent.getParent().getId().equals(primaryOwnerId)) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "User is not a dependent of this primary owner"));
            }

            // Unlink the dependent
            dependent.setParent(null);
            userRepository.save(dependent);

            logger.info("Removed user {} as dependent from primary owner {}", dependentUserId, primaryOwnerId);
            return ResponseEntity.ok(Map.of("message", "Family member removed successfully"));
        } catch (Exception e) {
            logger.error("Error removing family member: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to remove family member: " + e.getMessage()));
        }
    }
    @PutMapping("/{userId}/over18")
    public ResponseEntity<User> updateUserOver18(@PathVariable long userId) {
        try {
            User updatedUser = userService.updateUserOver18(userId);
            return ResponseEntity.ok(updatedUser);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @Autowired
    private JwtUtil jwtUtil;
    @GetMapping("/{id}/media")
    public ResponseEntity<UserMediaDTO> getUserMedia(@PathVariable Long id) {
        return userService.findById(id)
                .map(user -> ResponseEntity.ok(new UserMediaDTO(user)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
    @PostMapping("/addNewUser")
    public ResponseEntity<?> addNewUser(@Valid @RequestBody UserBusinessCreateDTO userDTO) {
        try {
            User user = userService.handleNewBusiness(userDTO);
            UserDTO userResponse = new UserDTO(user);
            return new ResponseEntity<>(userResponse, HttpStatus.CREATED);
        } catch (IllegalArgumentException e) {
            logger.error("Validation error adding user: {}", e.getMessage());
            return new ResponseEntity<>(new ErrorResponse("Invalid input: " + e.getMessage()), HttpStatus.BAD_REQUEST);
        } catch (Exception e) {
            logger.error("Error adding user: ", e);
            return new ResponseEntity<>(new ErrorResponse("Server error: " + e.getMessage()), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    // Simple error response DTO
    private static class ErrorResponse {
        private String error;

        public ErrorResponse(String error) {
            this.error = error;
        }

        public String getError() {
            return error;
        }

        public void setError(String error) {
            this.error = error;
        }
    }

    @PostMapping("/signin")
    public ResponseEntity<Map<String, Object>> signIn(@RequestBody Map<String, String> requestBody) {
        try {
            String phoneNumber = requestBody.get("phoneNumber");
            String password = requestBody.get("password");

            if (phoneNumber == null || password == null) {
                Map<String, Object> errorResponse = new HashMap<>();
                errorResponse.put("error", "Missing phone number or password");
                return ResponseEntity.status(400).body(errorResponse);
            }

            User user = userService.signInWithPhoneNumber(phoneNumber, password);
            String token = jwtUtil.generateToken(phoneNumber);

            Map<String, Object> response = new HashMap<>();
            response.put("user", user); // Use DTO to control serialization
            response.put("token", token);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("error", e.getMessage());
            return ResponseEntity.status(401).body(errorResponse);
        }
    }


    // Get all users
    @GetMapping
    public List<User> getAllUsers() {
        return userService.findAll();
    }

    @GetMapping("/basic/{clubTag}")
    public List<UserDTOBasic> getAllUsersDTOBasic(@PathVariable String clubTag) {
        // Note: clubTag parameter kept for backward compatibility, internally uses businessTag
        try {
            List<UserDTOBasic> users = userService.getAllUserDTOBasics(clubTag);
            return users;
        } catch (Exception e) {
            logger.error("ERROR fetching users for businessTag: {}", clubTag, e);
            throw e;
        }
    }

    @GetMapping("/search/{businessTag}")
    public ResponseEntity<List<UserDTO>> searchUsers(
            @PathVariable String businessTag,
            @RequestParam String query) {
        try {
            if (query == null || query.trim().isEmpty()) {
                return ResponseEntity.ok(List.of());
            }
            List<User> users = userRepository.searchUsersByBusinessTag(businessTag, query.trim());
            List<UserDTO> userDTOs = users.stream()
                    .map(UserDTO::new)
                    .toList();
            return ResponseEntity.ok(userDTOs);
        } catch (Exception e) {
            logger.error("Error searching users for businessTag: {} with query: {}", businessTag, query, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    // Delete User given userId or Phone Number
    @DeleteMapping("/delete-user")
    public ResponseEntity<Map<String, Object>> deleteUser(@RequestBody Map<String, Object> request) {
        Long userId = null;
        String phoneNumber = null;

        if (request.containsKey("userId")) {
            try {
                Object idObj = request.get("userId");
                if (idObj instanceof Number) {
                    userId = ((Number) idObj).longValue();
                } else if (idObj instanceof String) {
                    userId = Long.parseLong((String) idObj);
                }
            } catch (NumberFormatException ignored) {}
        }

        if (request.containsKey("phoneNumber")) {
            Object phoneObj = request.get("phoneNumber");
            if (phoneObj != null) {
                phoneNumber = phoneObj.toString();
            }
        }

        Optional<User> deletedUser = userService.deleteUserByIdOrPhone(userId, phoneNumber);

        Map<String, Object> response = new HashMap<>();
        if (deletedUser.isPresent()) {
            response.put("message", "User with ID " + deletedUser.get().getId() + " was deleted successfully.");
            return ResponseEntity.ok(response);
        } else {
            String ref = userId != null ? "ID " + userId : "phone number " + phoneNumber;
            response.put("error", "User with " + ref + " not found.");
            return ResponseEntity.status(404).body(response);
        }
    }

    // Get a single user by id
    @GetMapping("/{id}")
    public ResponseEntity<UserDTO> getUserById(@PathVariable Long id) {
        return userRepository.findByIdWithSignInLogs(id)
                .map(UserDTO::new)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    // Create a new user
    @PostMapping
    public User createUser(@RequestBody User user) throws Exception {
        return userService.save(user);
    }

    // Update an existing user
    @PutMapping("/{id}")
    public ResponseEntity<User> updateUser(@PathVariable Long id, @RequestBody User userDetails) {
        return userService.findById(id)
                .map(user -> {
                    if (userDetails.getFirstName() != null) user.setFirstName(userDetails.getFirstName());
                    if (userDetails.getLastName() != null) user.setLastName(userDetails.getLastName());
                    if (userDetails.getPassword() != null && !userDetails.getPassword().isEmpty()) user.setPassword(userDetails.getPassword());
                    if (userDetails.getPhoneNumber() != null) user.setPhoneNumber(userDetails.getPhoneNumber());
                    if (userDetails.getEmail() != null) user.setEmail(userDetails.getEmail()); // Added email update
                    if (userDetails.getIsInGoodStanding() != null) user.setIsInGoodStanding(userDetails.getIsInGoodStanding());
                    // createdAt usually remains unchanged
                    try {
                        return ResponseEntity.ok(userService.save(user));
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/barcode/{barcode}")
    public ResponseEntity<UserDTO> getUserByBarcode(@PathVariable String barcode) throws Exception {
        UserDTO dto = userService.processBarcodeScan(barcode);
        return ResponseEntity.ok(dto);
    }



    @GetMapping(value = "/barcode/image/{token}", produces = MediaType.IMAGE_PNG_VALUE)
    public ResponseEntity<byte[]> getBarcodeImage(@PathVariable String token) {
        try {
            byte[] image = barcodeService.generateBarcode(token, 300, 300);
            return ResponseEntity.ok().contentType(MediaType.IMAGE_PNG).body(image);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    @PostMapping("/fullSetupUser")
    public ResponseEntity<Map<String, String>> createUserAndCheckout(@RequestBody UserRequest userRequest) {
        try {
            // 1. Create a Stripe customer
            Map<String, Object> customerParams = new HashMap<>();
            customerParams.put("name", userRequest.getFirstName() + " " + userRequest.getLastName());
            customerParams.put("phone", userRequest.getPhoneNumber());
            Customer customer = Customer.create(customerParams);

            // 2. Create a Checkout session for activation fee and payment method setup
            Map<String, Object> sessionParams = new HashMap<>();
            sessionParams.put("customer", customer.getId());
            sessionParams.put("mode", "payment"); // For one-time charge
            sessionParams.put("payment_method_types", java.util.Arrays.asList("card"));
            sessionParams.put("success_url", "http://localhost:5173/success");
            sessionParams.put("cancel_url", "http://localhost:5173/cancel");

            // Add a one-time activation fee (e.g., $10)
            Map<String, Object> priceData = new HashMap<>();
            priceData.put("currency", "usd");
            priceData.put("unit_amount", 1000); // $10.00 in cents
            priceData.put("product_data", new HashMap<String, Object>() {{
                put("name", "Activation Fee");
            }});

            Map<String, Object> lineItem = new HashMap<>();
            lineItem.put("price_data", priceData);
            lineItem.put("quantity", 1);

            sessionParams.put("line_items", java.util.Arrays.asList(lineItem));

            // Save payment method for future use
            sessionParams.put("payment_intent_data", new HashMap<String, Object>() {{
                put("setup_future_usage", "off_session"); // Saves payment method
            }});

            Session session = Session.create(sessionParams); // Now using com.stripe.model.checkout.Session

            // 3. Return session ID to frontend
            Map<String, String> response = new HashMap<>();
            response.put("sessionId", session.getId());
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(500).body(Map.of("error", "Failed to create checkout session: " + e.getMessage()));
        }
    }
    public static class ImageUrlDTO {
        private String imageUrl;

        public String getImageUrl() {
            return imageUrl;
        }

        public void setImageUrl(String imageUrl) {
            this.imageUrl = imageUrl;
        }
    }
    // Update user profile picture
    @PostMapping("/{id}/picture")
    public ResponseEntity<UserMediaDTO> updateProfilePicture(
            @PathVariable Long id,
            @RequestParam("file") MultipartFile file) {

        try {
            // Upload to Firebase and get public URL
            String imageUrl = firebaseService.uploadImage(file);

            // Update user with new profile picture URL
            return userService.updateProfilePicture(id, imageUrl)
                    .map(user -> ResponseEntity.ok(new UserMediaDTO(user)))
                    .orElseGet(() -> ResponseEntity.notFound().build());

        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }


    @GetMapping("/referralCode/{referralCode}")
    public ResponseEntity<User> getUserByReferralCode(@PathVariable String referralCode) {
        User user = userService.getUserByReferralCode(referralCode); // Returns User or null
        if (user != null) {
            return ResponseEntity.ok(user); // 200 OK with user
        } else {
            return ResponseEntity.notFound().build(); // 404 Not Found
        }
    }

    @PutMapping("/referralCode/{referralCode}")
    public ResponseEntity<Void> updateUserReferralCode(
            @PathVariable String referralCode,
            @RequestBody String newReferralCode) {
        boolean updated = userService.updateReferralCode(referralCode, newReferralCode);
        if (updated) {
            return ResponseEntity.ok().build(); // 200 OK
        } else {
            return ResponseEntity.notFound().build(); // 404 Not Found
        }
    }

    @PostMapping("/{id}/waiver")
    public ResponseEntity<UserMediaDTO> saveWaiverSignature(
            @PathVariable Long id,
            @RequestParam("file") MultipartFile file) {

        try {
            // Upload to Firebase and get public URL
            String imageUrl = firebaseService.uploadImage(file);

            // Save the image URL as waiver signature for the user
            return userService.updateWaiverSignature(id, imageUrl)
                    .map(user -> ResponseEntity.ok(new UserMediaDTO(user)))
                    .orElseGet(() -> ResponseEntity.notFound().build());

        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }


    @PutMapping("/password/{id}")
    public ResponseEntity<Map<String, String>> updateUserPassword(@PathVariable Long id, @RequestBody Map<String, String> requestBody) {
        try {
            String newPassword = requestBody.get("password");
            if (newPassword == null || newPassword.trim().isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "Password is required and cannot be empty"));
            }

            User updatedUser = userService.updateUserPassword(id, newPassword);
            return ResponseEntity.ok(Map.of("message", "Password updated successfully for user ID: " + id));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "Failed to update password: " + e.getMessage()));
        }
    }

    /**
     * Family member signup endpoint
     * POST /api/users/family-signup
     * Body: { "primaryOwnerId": 123, "firstName": "John", "lastName": "Doe", "email": "john@example.com", "password": "password123" }
     */
    @PostMapping(value = "/family-signup", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> familySignup(@RequestBody Map<String, Object> request) {
        try {
            Long primaryOwnerId = Long.valueOf(request.get("primaryOwnerId").toString());
            String firstName = (String) request.get("firstName");
            String lastName = (String) request.get("lastName");
            String email = (String) request.get("email");
            String password = (String) request.get("password");

            if (primaryOwnerId == null || firstName == null || lastName == null || email == null || password == null) {
                return ResponseEntity.badRequest()
                    .body(Map.of("error", "All fields are required"));
            }

            // Check if email already exists
            Optional<User> existingUser = userRepository.findByEmail(email);
            if (existingUser.isPresent()) {
                return ResponseEntity.badRequest()
                    .body(Map.of("error", "An account with this email already exists"));
            }

            // Get primary owner
            Optional<User> primaryOwnerOpt = userRepository.findById(primaryOwnerId);
            if (primaryOwnerOpt.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "Primary owner not found"));
            }

            User primaryOwner = primaryOwnerOpt.get();

            // Get primary owner's business and Stripe info
            List<com.BossLiftingClub.BossLifting.User.BusinessUser.UserBusiness> userBusinesses = 
                userBusinessService.getBusinessesForUser(primaryOwnerId);
            
            if (userBusinesses.isEmpty()) {
                return ResponseEntity.badRequest()
                    .body(Map.of("error", "Primary owner is not associated with any business"));
            }

            com.BossLiftingClub.BossLifting.User.BusinessUser.UserBusiness primaryUserBusiness = userBusinesses.get(0);
            String stripeCustomerId = primaryUserBusiness.getStripeId();
            com.BossLiftingClub.BossLifting.Business.Business business = primaryUserBusiness.getBusiness();
            // Single-tenant: use platform account (null)
            String stripeAccountId = null;

            if (stripeCustomerId == null || stripeCustomerId.isEmpty()) {
                return ResponseEntity.badRequest()
                    .body(Map.of("error", "Primary owner does not have a Stripe customer ID"));
            }

            // Create new user
            User newUser = new User();
            newUser.setFirstName(firstName.trim());
            newUser.setLastName(lastName.trim());
            newUser.setEmail(email.trim().toLowerCase());
            newUser.setPassword(passwordEncoder.encode(password));
            newUser.setParent(primaryOwner);
            newUser.setUserType(com.BossLiftingClub.BossLifting.User.UserType.DEPENDENT);
            
            newUser = userRepository.save(newUser);

            // Mark pending invitation as accepted
            Optional<FamilyInvitation> invitationOpt = familyInvitationRepository
                .findByInvitedEmailAndPrimaryOwnerId(email.trim().toLowerCase(), primaryOwnerId);
            if (invitationOpt.isPresent()) {
                FamilyInvitation invitation = invitationOpt.get();
                invitation.setStatus(FamilyInvitation.InvitationStatus.ACCEPTED);
                invitation.setAcceptedAt(java.time.LocalDateTime.now());
                invitation.setUserId(newUser.getId());
                familyInvitationRepository.save(invitation);
            }

            // Create Customer Portal session for the primary owner's Stripe customer
            // Family members use the primary owner's Stripe customer for billing
            String frontendBaseUrl = System.getenv("FRONTEND_BASE_URL");
            if (frontendBaseUrl == null || frontendBaseUrl.isEmpty()) {
                frontendBaseUrl = "http://localhost:5173"; // Default to localhost for development
            }
            Map<String, Object> portalParams = new HashMap<>();
            portalParams.put("customer", stripeCustomerId);
            portalParams.put("return_url", request.get("returnUrl") != null 
                ? request.get("returnUrl") 
                : frontendBaseUrl + "/signin");

            com.stripe.net.RequestOptions requestOptions = com.stripe.net.RequestOptions.builder()
                .setStripeAccount(stripeAccountId)
                .build();

            Session portalSession = Session.create(portalParams, requestOptions);

            logger.info("Created family member account for user {} linked to primary owner {}", 
                newUser.getId(), primaryOwnerId);

            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Family member account created successfully",
                "portalUrl", portalSession.getUrl(),
                "userId", newUser.getId()
            ));

        } catch (Exception e) {
            logger.error("Error creating family member account: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Failed to create account: " + e.getMessage()));
        }
    }

}

