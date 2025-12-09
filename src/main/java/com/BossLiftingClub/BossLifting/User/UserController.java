package com.BossLiftingClub.BossLifting.User;

import com.BossLiftingClub.BossLifting.User.BusinessUser.UserBusinessCreateDTO;
import com.BossLiftingClub.BossLifting.User.PasswordAuth.JwtUtil;
import com.BossLiftingClub.BossLifting.User.SignInLog.SignInLog;
import com.BossLiftingClub.BossLifting.User.SignInLog.SignInLogRepository;
import com.stripe.model.Customer;
import com.stripe.model.billingportal.Session;

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
@RequestMapping("/users")
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
    public UserController(UserService userService, BarcodeService barcodeService, FirebaseService firebaseService) {
        this.userService = userService;
        this.barcodeService = barcodeService;
        this.firebaseService = firebaseService;
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

}

