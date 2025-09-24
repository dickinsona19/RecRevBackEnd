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
    public String onboardClient(String email, String country, String businessType, String password) throws StripeException {
        // Check if client already exists
        if (clientRepository.findByEmail(email).isPresent()) {
            throw new IllegalStateException("Email already exists");
        }

        // Create Stripe account
        AccountCreateParams params = AccountCreateParams.builder()
                .setType(AccountCreateParams.Type.EXPRESS)
                .setCountry(country)
                .setEmail(email)
                .setBusinessType(AccountCreateParams.BusinessType.valueOf(businessType.toUpperCase()))
                .build();

        Account account = Account.create(params);

        // Create new client
        Client client = new Client();
        client.setEmail(email);
        client.setPassword(passwordEncoder.encode(password)); // You should get this from request
        client.setCreatedAt(LocalDateTime.now());
        client.setStatus("PENDING");
        client.setStripeAccountId(account.getId());
        client.setClubs(new ArrayList<>());

        // Save client to database
        clientRepository.save(client);

        // Create Stripe onboarding link
        AccountLinkCreateParams linkParams = AccountLinkCreateParams.builder()
                .setAccount(account.getId())
                .setRefreshUrl("http://localhost:5173/dashboard")
                .setReturnUrl("http://localhost:5173/dashboard")
                .setType(AccountLinkCreateParams.Type.ACCOUNT_ONBOARDING)
                .build();

        AccountLink accountLink = AccountLink.create(linkParams);
        return accountLink.getUrl();
    }

    @Override
    @Transactional
    public ClientDTO createClient(ClientDTO clientDTO) {
        Client client = new Client();
        client.setEmail(clientDTO.getEmail());
        client.setPassword(clientDTO.getPassword());
        client.setCreatedAt(clientDTO.getCreatedAt());
        client.setStatus(clientDTO.getStatus());
        client.setStripeAccountId(clientDTO.getStripeAccountId());
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
        client.setStripeAccountId(clientDTO.getStripeAccountId());
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
        dto.setStripeAccountId(client.getStripeAccountId());
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