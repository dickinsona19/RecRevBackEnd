package com.BossLiftingClub.BossLifting.Client;

import com.BossLiftingClub.BossLifting.Club.ClubDTO;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class ClientServiceImpl implements ClientService {
    @Autowired
    private ClientRepository clientRepository;

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
        ClientDTO dto = new ClientDTO();
        dto.setId(client.getId());
        dto.setEmail(client.getEmail());
        dto.setPassword(client.getPassword());
        dto.setCreatedAt(client.getCreatedAt());
        dto.setStatus(client.getStatus());
        dto.setStripeAccountId(client.getStripeAccountId());
        dto.setClubs(client.getClubs().stream().map(club -> ClubDTO.mapToClubDTO(club)).collect(Collectors.toList()));
        return dto;
    }
}