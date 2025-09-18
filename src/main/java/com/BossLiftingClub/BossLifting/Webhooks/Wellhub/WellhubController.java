package com.BossLiftingClub.BossLifting.Webhooks.Wellhub;


import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

@RestController
@RequestMapping("/api/wellhub")
public class WellhubController {

    private final CopyOnWriteArrayList<SseEmitter> emitters = new CopyOnWriteArrayList<>();
    private final WebClient webClient;

    public WellhubController(
            @Value("${gympass.api.token}") String apiToken,
            @Value("${gympass.api.gym-id}") String gymId
    ) {
        this.webClient = WebClient.builder()
                .baseUrl("https://apitesting.partners.gympass.com")
                .defaultHeader(HttpHeaders.CONTENT_TYPE, "application/json")
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiToken)
                .defaultHeader("X-Gym-Id", gymId)
                .build();
    }

    // --- SSE Subscription for frontend ---
    @GetMapping("/subscribe")
    public SseEmitter subscribe() {
        SseEmitter emitter = new SseEmitter(Long.MAX_VALUE);

        emitter.onCompletion(() -> emitters.remove(emitter));
        emitter.onTimeout(() -> emitters.remove(emitter));
        emitters.add(emitter);

        return emitter;
    }

    // --- Notify + forward payload to Gympass ---
    @PostMapping("/notifyofuser")
    public ResponseEntity<String> handleNotifyOfUser(@RequestBody Map<String, Object> payload) {
        System.out.println("Received payload: " + payload);

        // Broadcast to frontend subscribers
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event()
                        .name("checkin-event")
                        .data(payload));
            } catch (Exception e) {
                emitter.complete();
                emitters.remove(emitter);
            }
        }

        return ResponseEntity.ok("Payload received and forwarded");
    }

    // --- Proxy endpoint to Gympass validate ---
    @PostMapping("/validate")
    public ResponseEntity<String> validateCheckin(@RequestBody Map<String, Object> payload) {
        try {
            // Use exchangeToMono to capture both success and error bodies
            String response = webClient.post()
                    .uri("/access/v1/validate")
                    .bodyValue(payload)
                    .exchangeToMono((ClientResponse clientResponse) ->
                            clientResponse.bodyToMono(String.class)
                                    .map(body -> {
                                        // Return status + body as a combined string
                                        return clientResponse.statusCode().value() + "::" + body;
                                    })
                    )
                    .block();

            if (response == null) {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body("Empty response from Gympass");
            }

            // Split the status and body
            int sep = response.indexOf("::");
            int statusCode = Integer.parseInt(response.substring(0, sep));
            String body = response.substring(sep + 2);

            // Parse JSON body
            ObjectMapper mapper = new ObjectMapper();
            JsonNode root = mapper.readTree(body);

            if (root.has("errors") && root.get("errors").isArray() && root.get("errors").size() > 0) {
                String errorMessage = root.get("errors").get(0).get("message").asText();
                return ResponseEntity.status(statusCode).body(errorMessage);
            }

            return ResponseEntity.status(statusCode).body(body);

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error validating check-in: " + e.getMessage());
        }
    }
}
