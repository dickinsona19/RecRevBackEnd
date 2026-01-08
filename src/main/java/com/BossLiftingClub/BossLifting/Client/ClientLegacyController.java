package com.BossLiftingClub.BossLifting.Client;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Legacy controller to handle /clients/{id} endpoints (without /api prefix)
 * for backward compatibility with frontend
 */
@RestController
@RequestMapping("/clients")
public class ClientLegacyController {
    
    @Autowired
    private ClientService clientService;
    
    /**
     * Get client with businesses
     * GET /clients/{id}
     */
    @GetMapping("/{id}")
    public ResponseEntity<ClientDTO> getClient(@PathVariable Integer id) {
        return ResponseEntity.ok(clientService.getClientWithClubs(id));
    }
}

