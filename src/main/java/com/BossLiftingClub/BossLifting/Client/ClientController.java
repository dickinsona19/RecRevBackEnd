package com.BossLiftingClub.BossLifting.Client;


import com.BossLiftingClub.BossLifting.Client.Requests.ClientSignUpRequest;
import com.stripe.exception.StripeException;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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
    public ClientDTO login(@RequestBody LoginRequest loginRequest) {
        Optional<Client> client = clientRepository.findByEmail(loginRequest.email());
        if (client.isPresent() && client.get().getPassword().equals(loginRequest.password())) {
            return clientService.getClientWithClubs(client.get().getId()); // Return the Client object if login is successful
        } else {
            return null; // Return null for invalid credentials
        }
    }

    record LoginRequest(String email, String password) {

        public String getEmail() {
            return email;
        }

        public String getPassword() {
            return password;
        }
    }

    @GetMapping("/clientAll/{id}")
    public ClientDTO getClient(@PathVariable Integer id) {
        return clientService.getClientWithClubs(id);
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
        try {
            String onboardingUrl = clientService.onboardClient(
                    request.getEmail(),
                    request.getCountry(),
                    request.getBusinessType(),
                    request.getPassword()
            );
            return ResponseEntity.ok().body("{\"url\": \"" + onboardingUrl + "\"}");
        } catch (StripeException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("{\"error\": \"Error: " + e.getMessage() + "\"}");
        }
    }

}
