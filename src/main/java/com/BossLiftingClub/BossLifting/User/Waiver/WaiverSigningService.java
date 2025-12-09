package com.BossLiftingClub.BossLifting.User.Waiver;

import com.BossLiftingClub.BossLifting.Business.Business;
import com.BossLiftingClub.BossLifting.Business.BusinessRepository;
import com.BossLiftingClub.BossLifting.Email.EmailService;
import com.BossLiftingClub.BossLifting.User.FirebaseService;
import com.BossLiftingClub.BossLifting.User.User;
import com.BossLiftingClub.BossLifting.User.UserRepository;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts.FontName;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.Optional;

@Service
public class WaiverSigningService {

    private static final Logger logger = LoggerFactory.getLogger(WaiverSigningService.class);

    private final WaiverTemplateRepository waiverTemplateRepository;
    private final UserWaiverRepository userWaiverRepository;
    private final UserRepository userRepository;
    private final BusinessRepository businessRepository;
    private final FirebaseService firebaseService;
    private final EmailService emailService;

    @Value("${app.frontend.url:http://localhost:5173}")
    private String frontendUrl;

    public WaiverSigningService(WaiverTemplateRepository waiverTemplateRepository,
                                UserWaiverRepository userWaiverRepository,
                                UserRepository userRepository,
                                BusinessRepository businessRepository,
                                FirebaseService firebaseService,
                                EmailService emailService) {
        this.waiverTemplateRepository = waiverTemplateRepository;
        this.userWaiverRepository = userWaiverRepository;
        this.userRepository = userRepository;
        this.businessRepository = businessRepository;
        this.firebaseService = firebaseService;
        this.emailService = emailService;
    }

    @Transactional
    public void sendWaiverEmail(Long userId, Long businessId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        Business business = businessRepository.findById(businessId)
                .orElseThrow(() -> new IllegalArgumentException("Business not found"));

        Optional<WaiverTemplate> templateOpt = waiverTemplateRepository
                .findFirstByBusinessIdAndActiveTrueOrderByVersionDesc(businessId);

        if (templateOpt.isEmpty()) {
            throw new IllegalStateException("No active waiver template found for this business");
        }

        String waiverSignUrl = frontendUrl + "/waiver/sign/" + userId;
        String businessName = business.getTitle() != null ? business.getTitle() : "the gym";

        String subject = "Please Sign Your Liability Waiver - " + businessName;
        String htmlBody = String.format("""
            <!DOCTYPE html>
            <html>
            <head>
                <style>
                    body { font-family: Arial, sans-serif; color: #333; line-height: 1.6; }
                    .container { max-width: 600px; margin: 0 auto; padding: 20px; }
                    .header { background-color: #f8f8f8; padding: 20px; text-align: center; border-radius: 8px 8px 0 0; }
                    .content { padding: 20px; background-color: #ffffff; }
                    .button { display: inline-block; padding: 12px 24px; background-color: #007BFF; color: white !important; text-decoration: none; border-radius: 5px; font-weight: bold; margin: 20px 0; }
                    .button:hover { background-color: #0056b3; }
                    .footer { font-size: 12px; color: #777; text-align: center; padding: 20px; }
                </style>
            </head>
            <body>
                <div class="container">
                    <div class="header">
                        <h2>%s</h2>
                    </div>
                    <div class="content">
                        <p>Hello %s,</p>
                        <p>Before you can access %s, you need to sign our liability waiver.</p>
                        <p>Please click the button below to review and sign the waiver:</p>
                        <p style="text-align: center;">
                            <a href="%s" class="button">Sign Waiver</a>
                        </p>
                        <p>Or copy and paste this link into your browser:</p>
                        <p style="word-break: break-all; color: #007BFF;">%s</p>
                        <p>This link will expire in 30 days.</p>
                    </div>
                    <div class="footer">
                        <p>If you did not expect this email, please ignore it.</p>
                    </div>
                </div>
            </body>
            </html>
            """, businessName, user.getFirstName(), businessName, waiverSignUrl, waiverSignUrl);

        try {
            emailService.sendBlastEmail(
                    user.getEmail(),
                    subject,
                    htmlBody,
                    businessName,
                    business.getContactEmail()
            );
            logger.info("Waiver email sent to user {} ({})", userId, user.getEmail());
        } catch (Exception e) {
            logger.error("Failed to send waiver email to user {}: {}", userId, e.getMessage());
            throw new RuntimeException("Failed to send waiver email: " + e.getMessage(), e);
        }
    }

    @Transactional
    public UserWaiver signWaiver(Long userId, Long businessId, String signatureImageBase64, String signerIp) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        Business business = businessRepository.findById(businessId)
                .orElseThrow(() -> new IllegalArgumentException("Business not found"));

        WaiverTemplate template = waiverTemplateRepository
                .findFirstByBusinessIdAndActiveTrueOrderByVersionDesc(businessId)
                .orElseThrow(() -> new IllegalStateException("No active waiver template found for this business"));

        // Check if user already signed this template version
        Optional<UserWaiver> existingWaiver = userWaiverRepository
                .findByUserIdAndWaiverTemplateId(userId, template.getId());

        if (existingWaiver.isPresent()) {
            throw new IllegalStateException("User has already signed this waiver version");
        }

        try {
            // Download the template PDF
            byte[] templatePdfBytes = downloadFile(template.getFileUrl());

            // Merge signature into PDF
            byte[] signedPdfBytes = mergeSignatureIntoPdf(
                    templatePdfBytes,
                    user.getFirstName() + " " + user.getLastName(),
                    signatureImageBase64,
                    signerIp
            );

            // Upload signed PDF to Firebase
            String signedPdfUrl = uploadSignedPdf(signedPdfBytes, userId, template.getId());

            // Upload signature image to Firebase
            String signatureImageUrl = uploadSignatureImage(signatureImageBase64, userId, template.getId());

            // Create UserWaiver record
            UserWaiver userWaiver = new UserWaiver();
            userWaiver.setUser(user);
            userWaiver.setBusiness(business);
            userWaiver.setWaiverTemplate(template);
            userWaiver.setSignedAt(LocalDateTime.now());
            userWaiver.setSignatureImageUrl(signatureImageUrl);
            userWaiver.setFinalPdfUrl(signedPdfUrl);
            userWaiver.setSignerIp(signerIp);

            UserWaiver savedWaiver = userWaiverRepository.save(userWaiver);

            // Update user's waiver status
            user.setWaiverStatus(WaiverStatus.SIGNED);
            user.setWaiverSignedDate(LocalDateTime.now());
            userRepository.save(user);

            logger.info("Waiver signed successfully for user {} (template version {})", userId, template.getVersion());
            return savedWaiver;
        } catch (Exception e) {
            logger.error("Failed to sign waiver for user {}: {}", userId, e.getMessage(), e);
            throw new RuntimeException("Failed to sign waiver: " + e.getMessage(), e);
        }
    }

    public Optional<UserWaiver> getUserWaiver(Long userId) {
        // Get the most recent signed waiver for the user
        return userWaiverRepository.findByUserIdOrderBySignedAtDesc(userId)
                .stream()
                .findFirst();
    }

    private byte[] downloadFile(String url) throws IOException {
        try (InputStream inputStream = new URL(url).openStream();
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, bytesRead);
            }
            return outputStream.toByteArray();
        }
    }

    private byte[] mergeSignatureIntoPdf(byte[] templatePdfBytes, String userName, String signatureBase64, String signerIp) throws IOException {
        try (PDDocument document = Loader.loadPDF(templatePdfBytes)) {
            // Add a new page for signature
            PDPage signaturePage = new PDPage(PDRectangle.A4);
            document.addPage(signaturePage);

            try (PDPageContentStream contentStream = new PDPageContentStream(document, signaturePage)) {
                float pageHeight = signaturePage.getMediaBox().getHeight();
                float margin = 50;
                float yPosition = pageHeight - margin;

                // Title
                PDType1Font helveticaBold = new PDType1Font(FontName.HELVETICA_BOLD);
                contentStream.beginText();
                contentStream.setFont(helveticaBold, 16);
                contentStream.newLineAtOffset(margin, yPosition);
                contentStream.showText("Waiver Signature Page");
                contentStream.endText();
                yPosition -= 30;

                // User name
                PDType1Font helvetica = new PDType1Font(FontName.HELVETICA);
                contentStream.beginText();
                contentStream.setFont(helvetica, 12);
                contentStream.newLineAtOffset(margin, yPosition);
                contentStream.showText("Signed by: " + userName);
                contentStream.endText();
                yPosition -= 25;

                // Signature image
                try {
                    String base64Data = signatureBase64.contains(",") ? signatureBase64.split(",")[1] : signatureBase64;
                    byte[] signatureBytes = Base64.getDecoder().decode(base64Data);
                    PDImageXObject pdImage = PDImageXObject.createFromByteArray(document, signatureBytes, "signature");
                    
                    float imageWidth = 200;
                    float imageHeight = (imageWidth / pdImage.getWidth()) * pdImage.getHeight();
                    yPosition -= imageHeight + 10;
                    
                    contentStream.drawImage(pdImage, margin, yPosition, imageWidth, imageHeight);
                    yPosition -= imageHeight + 20;
                } catch (Exception e) {
                    logger.warn("Failed to embed signature image: {}", e.getMessage());
                }

                // Timestamp
                String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss UTC"));
                contentStream.beginText();
                contentStream.setFont(helvetica, 10);
                contentStream.newLineAtOffset(margin, yPosition);
                contentStream.showText("Signed on: " + timestamp);
                contentStream.endText();
                yPosition -= 20;

                // IP Address (if provided)
                if (signerIp != null && !signerIp.isEmpty()) {
                    contentStream.beginText();
                    contentStream.setFont(helvetica, 10);
                    contentStream.newLineAtOffset(margin, yPosition);
                    contentStream.showText("IP Address: " + signerIp);
                    contentStream.endText();
                }
            }

            // Save to byte array
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            document.save(outputStream);
            return outputStream.toByteArray();
        }
    }

    private String uploadSignedPdf(byte[] pdfBytes, Long userId, Long templateId) throws IOException {
        MultipartFile multipartFile = new ByteArrayMultipartFile(
                pdfBytes,
                "signed-waiver-" + userId + "-" + templateId + ".pdf",
                "application/pdf"
        );
        return firebaseService.uploadFile(multipartFile, "signed-waivers/" + userId);
    }

    private String uploadSignatureImage(String base64Image, Long userId, Long templateId) throws IOException {
        String base64Data = base64Image.split(",")[1];
        byte[] imageBytes = Base64.getDecoder().decode(base64Data);
        MultipartFile multipartFile = new ByteArrayMultipartFile(
                imageBytes,
                "signature-" + userId + "-" + templateId + ".png",
                "image/png"
        );
        return firebaseService.uploadFile(multipartFile, "signatures/" + userId);
    }

    // Helper class to convert byte array to MultipartFile
    private static class ByteArrayMultipartFile implements MultipartFile {
        private final byte[] content;
        private final String name;
        private final String contentType;

        public ByteArrayMultipartFile(byte[] content, String name, String contentType) {
            this.content = content;
            this.name = name;
            this.contentType = contentType;
        }

        @Override
        public String getName() { return name; }

        @Override
        public String getOriginalFilename() { return name; }

        @Override
        public String getContentType() { return contentType; }

        @Override
        public boolean isEmpty() { return content.length == 0; }

        @Override
        public long getSize() { return content.length; }

        @Override
        public byte[] getBytes() { return content; }

        @Override
        public InputStream getInputStream() { return new ByteArrayInputStream(content); }

        @Override
        public void transferTo(java.io.File dest) throws IOException, IllegalStateException {
            java.nio.file.Files.write(dest.toPath(), content);
        }
    }
}

