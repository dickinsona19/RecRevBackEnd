package com.BossLiftingClub.BossLifting.Client;

import com.stripe.exception.StripeException;

import java.util.List;

public interface ClientService {
    ClientDTO createClient(ClientDTO clientDTO);
    ClientDTO getClientById(Integer id);
    List<ClientDTO> getAllClients();
    ClientDTO updateClient(Integer id, ClientDTO clientDTO);
    void deleteClient(Integer id);
    ClientDTO getClientWithBusinesses(Integer id);
    // Backward compatibility
    ClientDTO getClientWithClubs(Integer id);
    Client createClientWithoutStripe(String email, String password);
    
    /**
     * @deprecated Stripe onboarding has been moved to the business/club level.
     * Use BusinessService.createStripeOnboardingLink() instead.
     * This method throws UnsupportedOperationException.
     */
    @Deprecated
    String createStripeOnboardingLink(Integer clientId, String country, String businessType) throws StripeException;
}