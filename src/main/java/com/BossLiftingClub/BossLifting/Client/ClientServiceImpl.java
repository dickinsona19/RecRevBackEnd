package com.BossLiftingClub.BossLifting.Client;

import com.BossLiftingClub.BossLifting.Club.Club;
import com.BossLiftingClub.BossLifting.Club.ClubDTO;
import com.stripe.exception.StripeException;
import com.stripe.model.Account;
import com.stripe.model.AccountLink;
import com.stripe.param.AccountCreateParams;
import com.stripe.param.AccountLinkCreateParams;
import jakarta.persistence.EntityNotFoundException;
import org.hibernate.Hibernate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class ClientServiceImpl implements ClientService {
    @Autowired
    private ClientRepository clientRepository;

    private final PasswordEncoder passwordEncoder;

    public ClientServiceImpl(PasswordEncoder passwordEncoder) {
        this.passwordEncoder = passwordEncoder;
    }


    @Transactional
    public Client createClientWithoutStripe(String email, String password) {
        // Check if client already exists
        if (clientRepository.findByEmail(email).isPresent()) {
            throw new IllegalStateException("Email already exists");
        }

        // Create new client without Stripe account
        Client client = new Client();
        client.setEmail(email);
        client.setPassword(passwordEncoder.encode(password));
        client.setCreatedAt(LocalDateTime.now());
        client.setStatus("ACTIVE");
        client.setClubs(new ArrayList<>());

        // Save client to database
        return clientRepository.save(client);
    }

    @Transactional
    public String createStripeOnboardingLink(Integer clientId, String country, String businessType) throws StripeException {
        Client client = clientRepository.findById(clientId)
                .orElseThrow(() -> new IllegalStateException("Client not found"));

        // NOTE: Stripe account ID is now associated with clubs, not clients
        // This method needs to be refactored to accept a clubId parameter
        // For now, throwing an exception to indicate this needs implementation
        throw new UnsupportedOperationException(
            "Stripe onboarding has been moved to the club level. " +
            "Please use club-specific onboarding instead. " +
            "Each club should have its own Stripe connected account."
        );

        // TODO: Implement club-level onboarding
        // Example implementation would be:
        // 1. Accept clubId parameter instead of/in addition to clientId
        // 2. Get the club from clubRepository
        // 3. Check if club.getStripeAccountId() exists
        // 4. If not, create Stripe account and save to club.setStripeAccountId()
        // 5. Create and return onboarding link
    }

    @Override
    @Transactional
    public ClientDTO createClient(ClientDTO clientDTO) {
        Client client = new Client();
        client.setEmail(clientDTO.getEmail());
        client.setPassword(clientDTO.getPassword());
        client.setCreatedAt(clientDTO.getCreatedAt());
        client.setStatus(clientDTO.getStatus());
        Client savedClient = clientRepository.save(client);
        return mapToDTO(savedClient);
    }


    @Override
    @Transactional(readOnly = true)
    public ClientDTO getClientById(Integer id) {
        Client client = clientRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Client not found"));
        return mapToDTO(client);
    }
    @Override
    @Transactional(readOnly = true)
    public List<ClientDTO> getAllClients() {
        return clientRepository.findAll().stream()
                .map(this::mapToDTO)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public ClientDTO updateClient(Integer id, ClientDTO clientDTO) {
        Client client = clientRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Client not found"));
        client.setEmail(clientDTO.getEmail());
        client.setPassword(clientDTO.getPassword());
        client.setCreatedAt(clientDTO.getCreatedAt());
        client.setStatus(clientDTO.getStatus());
        Client updatedClient = clientRepository.save(client);
        return mapToDTO(updatedClient);
    }

    @Override
    @Transactional
    public void deleteClient(Integer id) {
        clientRepository.deleteById(id);
    }




    @Override
    @Transactional(readOnly = true)
    public ClientDTO getClientWithClubs(Integer id) {
        Client client = clientRepository.findByIdWithClubs(id)
                .orElseThrow(() -> new EntityNotFoundException("Client not found with ID: " + id));
        return mapToDTO(client);
    }

    private ClientDTO mapToDTO(Client client) {
        Hibernate.initialize(client.getClubs()); // Ensure clubs are loaded
        ClientDTO dto = new ClientDTO();
        dto.setId(client.getId());
        dto.setEmail(client.getEmail());
        dto.setPassword(client.getPassword());
        dto.setCreatedAt(client.getCreatedAt());
        dto.setStatus(client.getStatus());
        List<Club> clubs = client.getClubs();
        if (clubs != null) {
            dto.setClubs(clubs.stream()
                    .map(ClubDTO::mapToClubDTO)
                    .collect(Collectors.toSet()));
        }
        System.out.println("Client " + client.getId() + " clubs size: " + (clubs != null ? clubs.size() : 0));
        return dto;
    }
}