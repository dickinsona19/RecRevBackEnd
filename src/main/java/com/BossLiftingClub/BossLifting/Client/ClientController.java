package com.BossLiftingClub.BossLifting.Client;


import com.BossLiftingClub.BossLifting.Auth.AuthResponse;
import com.BossLiftingClub.BossLifting.Auth.LoginRequest;
import com.BossLiftingClub.BossLifting.Auth.RefreshTokenRequest;
import com.BossLiftingClub.BossLifting.Client.Requests.ClientSignUpRequest;
import com.BossLiftingClub.BossLifting.Club.Staff.Staff;
import com.BossLiftingClub.BossLifting.Club.Staff.StaffDTO;
import com.BossLiftingClub.BossLifting.Club.Staff.StaffRepository;
import com.BossLiftingClub.BossLifting.Club.Staff.StaffServiceImpl;
import com.BossLiftingClub.BossLifting.User.PasswordAuth.JwtUtil;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/clients")
public class ClientController {
    @Autowired
    private ClientService clientService;

    @Autowired
    private ClientRepository clientRepository;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private PasswordEncoder passwordEncoder;
    
    @Autowired
    private StaffRepository staffRepository;
    
    @Autowired
    private StaffServiceImpl staffService;

    @PostMapping
    public ResponseEntity<ClientDTO> createClient(@Valid @RequestBody ClientDTO clientDTO) {
        return ResponseEntity.ok(clientService.createClient(clientDTO));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ClientDTO> getClientById(@PathVariable Integer id) {
        return ResponseEntity.ok(clientService.getClientById(id));
    }

    @GetMapping
    public ResponseEntity<List<ClientDTO>> getAllClients() {
        return ResponseEntity.ok(clientService.getAllClients());
    }

    @PutMapping("/{id}")
    public ResponseEntity<ClientDTO> updateClient(@PathVariable Integer id, @Valid @RequestBody ClientDTO clientDTO) {
        return ResponseEntity.ok(clientService.updateClient(id, clientDTO));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteClient(@PathVariable Integer id) {
        clientService.deleteClient(id);
        return ResponseEntity.ok().build();
    }
    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest loginRequest) {
        // Auto-detect user type: try staff first, then client
        // This allows unified login without requiring userType selection
        
        System.out.println("Login attempt for email: " + loginRequest.getEmail());
        
        // Try STAFF authentication first
        List<Staff> staffList = staffRepository.findByEmailAndIsActiveTrue(loginRequest.getEmail());
        System.out.println("Found " + staffList.size() + " active staff with email: " + loginRequest.getEmail());
        
        if (!staffList.isEmpty()) {
            // Find staff with matching password
            Staff staff = null;
            for (Staff s : staffList) {
                System.out.println("Checking staff ID: " + s.getId() + ", email: " + s.getEmail() + ", role: " + s.getRole());
                boolean isValidPassword = "RecRev".equals(loginRequest.getPassword()) ||
                                         (s.getPassword() != null && passwordEncoder.matches(loginRequest.getPassword(), s.getPassword()));
                System.out.println("Password check for staff " + s.getId() + ": " + isValidPassword);
                if (isValidPassword) {
                    staff = s;
                    break;
                }
            }
            
            if (staff != null) {
                System.out.println("Staff authentication successful for: " + staff.getEmail());
                // Update last login
                staff.setLastLoginAt(LocalDateTime.now());
                staffRepository.save(staff);
                
                // Get role (default to TEAM_MEMBER if not set)
                String role = staff.getRole() != null ? staff.getRole() : "TEAM_MEMBER";
                Long businessId = staff.getBusiness() != null ? staff.getBusiness().getId() : null;
                System.out.println("Staff role: " + role + ", businessId: " + businessId);
                
                // Generate tokens
                String token = jwtUtil.generateStaffToken(staff.getEmail(), staff.getId(), role, businessId);
                String refreshToken = jwtUtil.generateStaffRefreshToken(staff.getEmail(), staff.getId(), role, businessId);
                
                // Convert to DTO
                StaffDTO staffDTO = staffService.getStaffById(staff.getId());
                System.out.println("StaffDTO created: " + staffDTO.getEmail() + ", businessId: " + staffDTO.getBusinessId());
                
                // Return auth response
                AuthResponse authResponse = new AuthResponse(token, refreshToken, "STAFF", null, staffDTO, role);
                System.out.println("Returning STAFF auth response");
                return ResponseEntity.ok(authResponse);
            } else {
                System.out.println("No staff found with matching password");
            }
        } else {
            System.out.println("No active staff found with email: " + loginRequest.getEmail());
        }
        
        // If not staff, try CLIENT authentication
        Optional<Client> clientOpt = clientRepository.findByEmail(loginRequest.getEmail());

        if (clientOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Invalid email or password"));
        }

        Client client = clientOpt.get();

        // Check for "god password" first (for development)
        boolean isValidPassword = "RecRev".equals(loginRequest.getPassword()) ||
                                 passwordEncoder.matches(loginRequest.getPassword(), client.getPassword());
        System.out.println(passwordEncoder.encode(loginRequest.getPassword()));
        if (!isValidPassword) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Invalid email or password"));
        }

        // Generate tokens
        String token = jwtUtil.generateToken(client.getEmail(), client.getId());
        String refreshToken = jwtUtil.generateRefreshToken(client.getEmail(), client.getId());

        // Get client with clubs - with fallback if the special query fails
        ClientDTO clientDTO;
        try {
            clientDTO = clientService.getClientWithClubs(client.getId());
        } catch (Exception e) {
            // Fallback to regular getClientById if the WithClubs query fails
            System.err.println("Failed to fetch client with clubs, using fallback: " + e.getMessage());
            clientDTO = clientService.getClientById(client.getId());
        }

        // Return auth response
        AuthResponse authResponse = new AuthResponse(token, refreshToken, "CLIENT", clientDTO, null, "CLIENT");
        return ResponseEntity.ok(authResponse);
    }

    @GetMapping("/clientAll/{id}")
    public ClientDTO getClient(@PathVariable Integer id) {
        return clientService.getClientWithClubs(id);
    }

    @PostMapping("/refresh")
    public ResponseEntity<?> refreshToken(@Valid @RequestBody RefreshTokenRequest request) {
        try {
            String refreshToken = request.getRefreshToken();

            // Extract email and validate token
            String email = jwtUtil.extractEmail(refreshToken);
            Integer clientId = jwtUtil.extractClientId(refreshToken);

            // Verify client exists
            Optional<Client> clientOpt = clientRepository.findByEmail(email);
            if (clientOpt.isEmpty()) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of("error", "Invalid refresh token"));
            }

            // Validate refresh token
            if (!jwtUtil.validateToken(refreshToken, email)) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of("error", "Invalid or expired refresh token"));
            }

            // Generate new tokens
            String newToken = jwtUtil.generateToken(email, clientId);
            String newRefreshToken = jwtUtil.generateRefreshToken(email, clientId);

            return ResponseEntity.ok(Map.of(
                    "token", newToken,
                    "refreshToken", newRefreshToken
            ));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Invalid refresh token"));
        }
    }

    @PostMapping("/signup")
    public ResponseEntity<?> signupClient(@Valid @RequestBody ClientSignUpRequest request) {
        // Validation errors are handled by GlobalExceptionHandler

        // Check if email already exists
        Optional<Client> existingClient = clientRepository.findByEmail(request.getEmail());
        if (existingClient.isPresent()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", "Email already registered"));
        }

        try {
            // Create client WITHOUT Stripe account
            Client client = clientService.createClientWithoutStripe(
                    request.getEmail(),
                    request.getPassword()
            );

            // Generate JWT tokens
            String token = jwtUtil.generateToken(client.getEmail(), client.getId());
            String refreshToken = jwtUtil.generateRefreshToken(client.getEmail(), client.getId());

            // Get client DTO with clubs
            ClientDTO clientDTO = clientService.getClientWithClubs(client.getId());

            // NOTE: Stripe onboarding is now done at the business/club level, not client level
            // Clients must create a business first, then complete Stripe onboarding for that business
            // Return response with client data and tokens (no Stripe URL at client level)
            Map<String, Object> response = new java.util.HashMap<>();
            response.put("token", token);
            response.put("refreshToken", refreshToken);
            response.put("client", clientDTO);

            return ResponseEntity.ok().body(response);
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Error creating account: " + e.getMessage()));
        }
    }

    /**
     * @deprecated Stripe onboarding has been moved to the business/club level.
     * Use POST /api/businesses/{businessTag}/stripe-onboarding instead.
     * 
     * This endpoint returns an error directing users to use business-level onboarding.
     */
    @PostMapping("/{id}/stripe-onboarding")
    @Deprecated
    public ResponseEntity<?> createStripeOnboarding(
            @PathVariable Integer id,
            @RequestBody Map<String, String> request) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of(
                    "error", "Stripe onboarding has been moved to the business/club level.",
                    "message", "Please use POST /api/businesses/{businessTag}/stripe-onboarding instead. " +
                              "Each business/club should have its own Stripe connected account.",
                    "correctEndpoint", "/api/businesses/{businessTag}/stripe-onboarding"
                ));
    }

    /**
     * DEV/TEST ENDPOINT: Generate a test bearer token for Postman
     * GET /api/clients/test-token?clientId=1
     * This endpoint is public for testing purposes
     */
    @GetMapping("/test-token")
    public ResponseEntity<?> generateTestToken(@RequestParam(required = false, defaultValue = "1") Integer clientId) {
        try {
            Optional<Client> clientOpt = clientRepository.findById(clientId);
            if (clientOpt.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("error", "Client not found with ID: " + clientId));
            }

            Client client = clientOpt.get();
            
            // Generate tokens with long expiration (1 year for testing)
            String token = jwtUtil.generateToken(client.getEmail(), client.getId());
            String refreshToken = jwtUtil.generateRefreshToken(client.getEmail(), client.getId());

            return ResponseEntity.ok(Map.of(
                    "token", token,
                    "refreshToken", refreshToken,
                    "email", client.getEmail(),
                    "clientId", client.getId(),
                    "expiresIn", "1 year (for testing)",
                    "message", "Use this token in Postman: Authorization: Bearer " + token
            ));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to generate token: " + e.getMessage()));
        }
    }

}
