package com.BossLiftingClub.BossLifting.Client;

import com.BossLiftingClub.BossLifting.Business.Business;
import com.BossLiftingClub.BossLifting.Business.BusinessDTO;
import com.stripe.exception.StripeException;
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
        client.setBusinesses(new ArrayList<>());

        // Save client to database
        return clientRepository.save(client);
    }

    /**
     * @deprecated Stripe onboarding has been moved to the business/club level.
     * Use BusinessService.createStripeOnboardingLink() instead.
     */
    @Deprecated
    @Transactional
    public String createStripeOnboardingLink(Integer clientId, String country, String businessType) throws StripeException {
        // NOTE: Stripe account ID is now associated with businesses/clubs, not clients
        // This method is deprecated and always throws UnsupportedOperationException
        // Use BusinessService.createStripeOnboardingLink(businessTag, returnUrl, refreshUrl) instead
        throw new UnsupportedOperationException(
            "Stripe onboarding has been moved to the business/club level. " +
            "Please use BusinessService.createStripeOnboardingLink() instead. " +
            "Each business/club should have its own Stripe connected account."
        );
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
    public ClientDTO getClientWithBusinesses(Integer id) {
        Client client = clientRepository.findByIdWithBusinesses(id)
                .orElseThrow(() -> new EntityNotFoundException("Client not found with ID: " + id));
        return mapToDTO(client);
    }
    
    // Backward compatibility - getClientWithClubs maps to getClientWithBusinesses
    @Transactional(readOnly = true)
    public ClientDTO getClientWithClubs(Integer id) {
        return getClientWithBusinesses(id);
    }

    private ClientDTO mapToDTO(Client client) {
        Hibernate.initialize(client.getBusinesses()); // Ensure businesses are loaded
        ClientDTO dto = new ClientDTO();
        dto.setId(client.getId());
        dto.setEmail(client.getEmail());
        dto.setPassword(client.getPassword());
        dto.setCreatedAt(client.getCreatedAt());
        dto.setStatus(client.getStatus());
        List<Business> businesses = client.getBusinesses();
        if (businesses != null) {
            dto.setBusinesses(businesses.stream()
                    .map(BusinessDTO::mapToBusinessDTO)
                    .collect(Collectors.toSet()));
            // Backward compatibility - also set clubs
            dto.setClubs(businesses.stream()
                    .map(BusinessDTO::mapToBusinessDTO)
                    .collect(Collectors.toSet()));
        }
        System.out.println("Client " + client.getId() + " businesses size: " + (businesses != null ? businesses.size() : 0));
        return dto;
    }
}