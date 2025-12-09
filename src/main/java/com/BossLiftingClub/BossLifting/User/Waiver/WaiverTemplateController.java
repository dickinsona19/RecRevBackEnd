package com.BossLiftingClub.BossLifting.User.Waiver;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;

@RestController
@RequestMapping("/api/waivers")
public class WaiverTemplateController {

    private final WaiverTemplateService waiverTemplateService;
    private final WaiverSigningService waiverSigningService;

    public WaiverTemplateController(WaiverTemplateService waiverTemplateService,
                                    WaiverSigningService waiverSigningService) {
        this.waiverTemplateService = waiverTemplateService;
        this.waiverSigningService = waiverSigningService;
    }

    @GetMapping("/business/{businessId}/template")
    public ResponseEntity<WaiverTemplateResponse> getActiveTemplate(@PathVariable Long businessId) {
        return waiverTemplateService.getActiveTemplate(businessId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping(value = "/business/{businessId}/template", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> uploadTemplate(@PathVariable Long businessId,
                                            @RequestParam("file") org.springframework.web.multipart.MultipartFile file) {
        try {
            WaiverTemplateResponse response = waiverTemplateService.uploadTemplate(businessId, file);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
        } catch (Exception ex) {
            return ResponseEntity.status(500).body(Map.of("error", "Failed to upload template: " + ex.getMessage()));
        }
    }

    @PostMapping("/send-email")
    public ResponseEntity<?> sendWaiverEmail(@RequestBody Map<String, Object> request) {
        try {
            Long userId = Long.valueOf(request.get("userId").toString());
            Long businessId = Long.valueOf(request.get("businessId").toString());
            
            waiverSigningService.sendWaiverEmail(userId, businessId);
            return ResponseEntity.ok(Map.of("message", "Waiver email sent successfully"));
        } catch (IllegalArgumentException | IllegalStateException ex) {
            return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
        } catch (Exception ex) {
            return ResponseEntity.status(500).body(Map.of("error", "Failed to send email: " + ex.getMessage()));
        }
    }

    @PostMapping("/sign")
    public ResponseEntity<?> signWaiver(@RequestBody Map<String, Object> request,
                                        HttpServletRequest httpRequest) {
        try {
            Long userId = Long.valueOf(request.get("userId").toString());
            Long businessId = Long.valueOf(request.get("businessId").toString());
            String signatureImage = request.get("signatureImage").toString();
            
            String signerIp = httpRequest.getRemoteAddr();
            if (httpRequest.getHeader("X-Forwarded-For") != null) {
                signerIp = httpRequest.getHeader("X-Forwarded-For").split(",")[0].trim();
            }
            
            UserWaiver signedWaiver = waiverSigningService.signWaiver(userId, businessId, signatureImage, signerIp);
            return ResponseEntity.ok(Map.of(
                    "message", "Waiver signed successfully",
                    "waiverId", signedWaiver.getId(),
                    "finalPdfUrl", signedWaiver.getFinalPdfUrl()
            ));
        } catch (IllegalArgumentException | IllegalStateException ex) {
            return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
        } catch (Exception ex) {
            return ResponseEntity.status(500).body(Map.of("error", "Failed to sign waiver: " + ex.getMessage()));
        }
    }

    @GetMapping("/user/{userId}")
    public ResponseEntity<?> getUserWaiver(@PathVariable Long userId) {
        try {
            return waiverSigningService.getUserWaiver(userId)
                    .map(waiver -> ResponseEntity.ok(Map.of(
                            "id", waiver.getId(),
                            "signedAt", waiver.getSignedAt().toString(),
                            "signatureImageUrl", waiver.getSignatureImageUrl() != null ? waiver.getSignatureImageUrl() : "",
                            "finalPdfUrl", waiver.getFinalPdfUrl() != null ? waiver.getFinalPdfUrl() : "",
                            "signerIp", waiver.getSignerIp() != null ? waiver.getSignerIp() : "",
                            "waiverTemplateId", waiver.getWaiverTemplate().getId(),
                            "waiverTemplateVersion", waiver.getWaiverTemplate().getVersion()
                    )))
                    .orElse(ResponseEntity.notFound().build());
        } catch (Exception ex) {
            return ResponseEntity.status(500).body(Map.of("error", "Failed to fetch user waiver: " + ex.getMessage()));
        }
    }
}

