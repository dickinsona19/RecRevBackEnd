package com.BossLiftingClub.BossLifting.Client;


import com.BossLiftingClub.BossLifting.Auth.AuthResponse;
import com.BossLiftingClub.BossLifting.Auth.LoginRequest;
import com.BossLiftingClub.BossLifting.Auth.RefreshTokenRequest;
import com.BossLiftingClub.BossLifting.Client.Requests.ClientSignUpRequest;
import com.BossLiftingClub.BossLifting.User.PasswordAuth.JwtUtil;
import com.stripe.exception.StripeException;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

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

    @PostMapping
    public ResponseEntity<ClientDTO> createClient(@RequestBody ClientDTO clientDTO) {
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
    public ResponseEntity<ClientDTO> updateClient(@PathVariable Integer id, @RequestBody ClientDTO clientDTO) {
        return ResponseEntity.ok(clientService.updateClient(id, clientDTO));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteClient(@PathVariable Integer id) {
        clientService.deleteClient(id);
        return ResponseEntity.ok().build();
    }
    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest loginRequest) {
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
        AuthResponse authResponse = new AuthResponse(token, refreshToken, clientDTO);
        return ResponseEntity.ok(authResponse);
    }

    @GetMapping("/clientAll/{id}")
    public ClientDTO getClient(@PathVariable Integer id) {
        return clientService.getClientWithClubs(id);
    }

    @PostMapping("/refresh")
    public ResponseEntity<?> refreshToken(@RequestBody RefreshTokenRequest request) {
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
    public ResponseEntity<?> signupClient(@Valid @RequestBody ClientSignUpRequest request, BindingResult result) {
        if (result.hasErrors()) {
            Map<String, String> errors = result.getFieldErrors().stream()
                    .collect(Collectors.toMap(
                            fieldError -> fieldError.getField(),
                            fieldError -> fieldError.getDefaultMessage()
                    ));
            return ResponseEntity.badRequest().body(errors);
        }

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

            // Create Stripe onboarding link for later use
            String onboardingUrl = null;
            try {
                onboardingUrl = clientService.createStripeOnboardingLink(
                        client.getId(),
                        request.getCountry(),
                        request.getBusinessType()
                );
            } catch (StripeException e) {
                // Log error but don't fail signup
                System.err.println("Failed to create Stripe onboarding link: " + e.getMessage());
            }

            // Return response with client data, tokens, and optional Stripe URL
            Map<String, Object> response = new java.util.HashMap<>();
            response.put("token", token);
            response.put("refreshToken", refreshToken);
            response.put("client", clientDTO);
            if (onboardingUrl != null) {
                response.put("url", onboardingUrl);
            }

            return ResponseEntity.ok().body(response);
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Error creating account: " + e.getMessage()));
        }
    }

    @PostMapping("/{id}/stripe-onboarding")
    public ResponseEntity<?> createStripeOnboarding(
            @PathVariable Integer id,
            @RequestBody Map<String, String> request) {
        try {
            String country = request.getOrDefault("country", "US");
            String businessType = request.getOrDefault("businessType", "COMPANY");

            String onboardingUrl = clientService.createStripeOnboardingLink(id, country, businessType);

            return ResponseEntity.ok().body(Map.of("url", onboardingUrl));
        } catch (StripeException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Error creating Stripe onboarding: " + e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", e.getMessage()));
        }
    }

}
