package com.BossLiftingClub.BossLifting.Promo;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.ResponseEntity;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.web.bind.annotation.*;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/promos")
public class PromoController {

    @Autowired
    private JavaMailSender mailSender;

    @Autowired
    private PromoService promoService;

    private static final String RECIPIENT_EMAIL = "contact@cltliftingclub.com";
    private static final int QR_CODE_WIDTH = 200;
    private static final int QR_CODE_HEIGHT = 200;

    @GetMapping
    public List<PromoDTO> getAllPromos() {
        return promoService.findAll();
    }

    @GetMapping("/business/{businessTag}")
    public List<PromoDTO> getPromosByBusinessTag(@PathVariable String businessTag) {
        return promoService.findAllByBusinessTag(businessTag);
    }

    @GetMapping("/{id}")
    public ResponseEntity<PromoDTO> getPromoById(@PathVariable Long id) {
        Optional<PromoDTO> promoDto = promoService.findById(id);
        return promoDto.map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/by-token/{codeToken}")
    public ResponseEntity<PromoDTO> getPromoByCodeToken(@PathVariable String codeToken) {
        Optional<PromoDTO> promoDto = promoService.findByCodeToken(codeToken);
        return promoDto.map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<PromoDTO> createPromo(@RequestBody PromoCreateDTO createDTO) {
        return ResponseEntity.ok(promoService.createPromo(createDTO));
    }

    @PutMapping("/{id}")
    public ResponseEntity<Promo> updatePromo(@PathVariable Long id, @RequestBody Promo promoDetails) {
        // This method needs refactoring to use DTOs/Service properly, but for now keeping basic functionality
        // Ideally we shouldn't be updating core promo details after Stripe creation easily
        Optional<PromoDTO> promoOptional = promoService.findById(id);
        if (promoOptional.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        // Partial update implementation would go here. 
        // For now, assuming minimal updates or deprecating this flow in favor of immutable promos
        return ResponseEntity.ok().build(); 
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletePromo(@PathVariable Long id) {
        promoService.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/add-user")
    public ResponseEntity<String> addUserToPromo(@RequestParam String codeToken, @RequestParam Long userId) {
        try {
            promoService.addUserToPromo(codeToken, userId);
            return ResponseEntity.ok("User added to promo successfully");
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }
    
    @PostMapping("/generate-qr")
    public String generateQRAndSendEmail(@RequestBody String url) {
        try {
            // Validate URL
            if (url == null || url.trim().isEmpty()) {
                return "Error: URL cannot be empty";
            }

            // Generate QR code as PNG
            byte[] qrCodeBytes = generateQRCode(url);

            // Send email with QR code attachment
            sendEmailWithQRCode(url, qrCodeBytes);

            return "QR code generated and sent to " + RECIPIENT_EMAIL;
        } catch (WriterException | IOException | MessagingException e) {
            return "Error generating or sending QR code: " + e.getMessage();
        }
    }

    private byte[] generateQRCode(String url) throws WriterException, IOException {
        QRCodeWriter qrCodeWriter = new QRCodeWriter();
        BitMatrix bitMatrix = qrCodeWriter.encode(url, BarcodeFormat.QR_CODE, QR_CODE_WIDTH, QR_CODE_HEIGHT);

        ByteArrayOutputStream pngOutputStream = new ByteArrayOutputStream();
        MatrixToImageWriter.writeToStream(bitMatrix, "PNG", pngOutputStream);
        return pngOutputStream.toByteArray();
    }

    private void sendEmailWithQRCode(String url, byte[] qrCodeBytes) throws MessagingException {
        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true); // true for multipart (attachments)

        helper.setFrom(RECIPIENT_EMAIL);
        helper.setTo(RECIPIENT_EMAIL);
        helper.setSubject("Your QR Code");

        // Email body
        String emailBody = "<h3>Your QR Code</h3>" +
                "<p>A QR code for the URL <a href=\"" + url + "\">" + url + "</a> has been generated.</p>" +
                "<p>Please find the QR code attached as a PNG image.</p>" +
                "<p>Thank you!</p>";
        helper.setText(emailBody, true);

        ByteArrayResource qrCodeResource = new ByteArrayResource(qrCodeBytes) {
            @Override
            public String getFilename() {
                return "qrcode.png";
            }
        };
        helper.addAttachment("qrcode.png", qrCodeResource);

        mailSender.send(message);
    }

    @PostMapping("/code/{codeToken}/increment-url-visit")
    public ResponseEntity<PromoDTO> incrementUrlVisitCountByCodeToken(@PathVariable String codeToken) {
        return promoService.incrementUrlVisitCountByCodeToken(codeToken)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

}
