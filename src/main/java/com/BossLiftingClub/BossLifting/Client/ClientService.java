package com.BossLiftingClub.BossLifting.Client;

import com.stripe.exception.StripeException;

import java.util.List;

public interface ClientService {
    ClientDTO createClient(ClientDTO clientDTO);
    ClientDTO getClientById(Integer id);
    List<ClientDTO> getAllClients();
    ClientDTO updateClient(Integer id, ClientDTO clientDTO);
    void deleteClient(Integer id);
    ClientDTO getClientWithClubs(Integer id);
    public String onboardClient(String clientEmail, String country, String businessType, String password) throws StripeException;
}