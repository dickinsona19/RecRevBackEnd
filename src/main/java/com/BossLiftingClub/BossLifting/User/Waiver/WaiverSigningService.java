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
import org.apache.pdfbox.pdmodel.interactive.form.PDAcroForm;
import org.apache.pdfbox.pdmodel.interactive.form.PDField;
import org.apache.pdfbox.pdmodel.interactive.form.PDTextField;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationWidget;
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
import java.net.URI;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.List;
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
    private final com.BossLiftingClub.BossLifting.User.BusinessUser.UserBusinessService userBusinessService;

    @Value("${app.frontend.url:http://localhost:5173}")
    private String frontendUrl;

    public WaiverSigningService(WaiverTemplateRepository waiverTemplateRepository,
                                UserWaiverRepository userWaiverRepository,
                                UserRepository userRepository,
                                BusinessRepository businessRepository,
                                FirebaseService firebaseService,
                                EmailService emailService,
                                com.BossLiftingClub.BossLifting.User.BusinessUser.UserBusinessService userBusinessService) {
        this.waiverTemplateRepository = waiverTemplateRepository;
        this.userWaiverRepository = userWaiverRepository;
        this.userRepository = userRepository;
        this.businessRepository = businessRepository;
        this.firebaseService = firebaseService;
        this.emailService = emailService;
        this.userBusinessService = userBusinessService;
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
                    business.getContactEmail(),
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
            // Download the template PDF using Firebase SDK
            byte[] templatePdfBytes = firebaseService.downloadFile(template.getFileUrl());

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

            // Recalculate status and user type for all UserBusiness relationships for this user
            // since waiver status affects the calculated status
            try {
                java.util.List<com.BossLiftingClub.BossLifting.User.BusinessUser.UserBusiness> userBusinesses = user.getUserBusinesses();
                if (userBusinesses != null) {
                    for (com.BossLiftingClub.BossLifting.User.BusinessUser.UserBusiness ub : userBusinesses) {
                        userBusinessService.calculateAndUpdateStatus(ub);
                    }
                }
            } catch (Exception e) {
                logger.error("Failed to recalculate status after waiver signing for user {}: {}", userId, e.getMessage());
                // Don't fail the waiver signing if status recalculation fails
            }

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

    /**
     * Migration-safe: attempts full waiver signing but never throws. On any failure, sets user's
     * signature/waiver fields and returns. Prevents transaction rollback when PDF merge fails (e.g. encrypted template).
     */
    @Transactional
    public void signWaiverFromSignatureUrlForMigration(Long userId, Long businessId, String signatureImageUrl, LocalDateTime signedAt) {
        try {
            UserWaiver w = signWaiverFromSignatureUrl(userId, businessId, signatureImageUrl, signedAt);
            if (w != null) {
                logger.info("Waiver attached from migration for user {}", userId);
            }
        } catch (Exception e) {
            logger.warn("Could not attach waiver PDF for user {}: {} - creating UserWaiver with signature URL only", userId, e.getMessage());
            User user = userRepository.findById(userId).orElse(null);
            Business business = businessRepository.findById(businessId).orElse(null);
            WaiverTemplate template = waiverTemplateRepository.findFirstByBusinessIdAndActiveTrueOrderByVersionDesc(businessId).orElse(null);
            if (user != null && business != null && template != null) {
                LocalDateTime signDate = signedAt != null ? signedAt : LocalDateTime.now();
                user.setSignatureData(signatureImageUrl);
                user.setWaiverSignedDate(signDate);
                user.setWaiverStatus(WaiverStatus.SIGNED);
                userRepository.save(user);
                UserWaiver userWaiver = new UserWaiver();
                userWaiver.setUser(user);
                userWaiver.setBusiness(business);
                userWaiver.setWaiverTemplate(template);
                userWaiver.setSignedAt(signDate);
                userWaiver.setSignatureImageUrl(signatureImageUrl);
                userWaiver.setFinalPdfUrl(null);
                userWaiverRepository.save(userWaiver);
            }
        }
    }

    /**
     * Sign waiver for a migrated user using an existing signature image URL.
     * Fetches the image from the URL, merges it into the waiver PDF (same as manual flow), and creates the UserWaiver record.
     */
    @Transactional
    public UserWaiver signWaiverFromSignatureUrl(Long userId, Long businessId, String signatureImageUrl, LocalDateTime signedAt) {
        if (signatureImageUrl == null || signatureImageUrl.isBlank()) {
            throw new IllegalArgumentException("signatureImageUrl is required");
        }
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        Business business = businessRepository.findById(businessId)
                .orElseThrow(() -> new IllegalArgumentException("Business not found"));
        WaiverTemplate template = waiverTemplateRepository
                .findFirstByBusinessIdAndActiveTrueOrderByVersionDesc(businessId)
                .orElseThrow(() -> new IllegalStateException("No active waiver template found for this business"));

        Optional<UserWaiver> existingWaiver = userWaiverRepository.findByUserIdAndWaiverTemplateId(userId, template.getId());
        if (existingWaiver.isPresent()) {
            return existingWaiver.get();
        }

        try {
            byte[] signatureBytes = fetchImageFromUrl(signatureImageUrl);
            byte[] templatePdfBytes = firebaseService.downloadFile(template.getFileUrl());
            byte[] signedPdfBytes = mergeSignatureIntoPdf(templatePdfBytes, user.getFirstName() + " " + user.getLastName(),
                    signatureBytes, null, signedAt != null ? signedAt : LocalDateTime.now());

            String signedPdfUrl = uploadSignedPdf(signedPdfBytes, userId, template.getId());

            UserWaiver userWaiver = new UserWaiver();
            userWaiver.setUser(user);
            userWaiver.setBusiness(business);
            userWaiver.setWaiverTemplate(template);
            userWaiver.setSignedAt(signedAt != null ? signedAt : LocalDateTime.now());
            userWaiver.setSignatureImageUrl(signatureImageUrl);
            userWaiver.setFinalPdfUrl(signedPdfUrl);

            UserWaiver savedWaiver = userWaiverRepository.save(userWaiver);
            user.setWaiverStatus(WaiverStatus.SIGNED);
            user.setWaiverSignedDate(signedAt != null ? signedAt : LocalDateTime.now());
            user.setSignatureData(signatureImageUrl);
            userRepository.save(user);

            for (com.BossLiftingClub.BossLifting.User.BusinessUser.UserBusiness ub : user.getUserBusinesses()) {
                userBusinessService.calculateAndUpdateStatus(ub);
            }
            logger.info("Waiver signed from migration for user {} (template version {})", userId, template.getVersion());
            return savedWaiver;
        } catch (Exception e) {
            logger.error("Failed to sign waiver from URL for user {}: {}", userId, e.getMessage(), e);
            throw new RuntimeException("Failed to sign waiver from migration: " + e.getMessage(), e);
        }
    }

    private byte[] fetchImageFromUrl(String url) throws IOException {
        try (InputStream in = URI.create(url).toURL().openStream()) {
            return in.readAllBytes();
        }
    }

    /**
     * Fills "Digital Signature" and "Date" on the waiver template.
     * 1) If template has AcroForm fields, fills those.
     * 2) Otherwise draws signature + date on the last page (for templates with static labels/underlines).
     */
    private void fillWaiverFormFields(PDDocument document, String userName, byte[] signatureImageBytes, LocalDateTime signedAt) {
        boolean drewInFormField = fillWaiverAcroFormFields(document, userName, signatureImageBytes, signedAt);
        if (!drewInFormField) {
            drawSignatureOnLastPage(document, userName, signatureImageBytes, signedAt);
        }
    }

    /** Returns true if we drew the signature image (in a form field widget). If false, caller should draw on last page. */
    private boolean fillWaiverAcroFormFields(PDDocument document, String userName, byte[] signatureImageBytes, LocalDateTime signedAt) {
        try {
            PDAcroForm acroForm = document.getDocumentCatalog().getAcroForm();
            if (acroForm == null || acroForm.getFields() == null || acroForm.getFields().isEmpty()) return false;

            String dateStr = signedAt.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
            PDPage signatureWidgetPage = null;
            PDRectangle signatureRect = null;
            boolean drewSignatureImage = false;

            for (PDField field : acroForm.getFields()) {
                String name = (field.getFullyQualifiedName() != null ? field.getFullyQualifiedName() : "").toLowerCase();
                if (name.contains("date")) {
                    try {
                        if (field instanceof PDTextField) {
                            ((PDTextField) field).setValue(dateStr);
                            logger.debug("Filled Date form field: {}", field.getFullyQualifiedName());
                        }
                    } catch (Exception e) {
                        logger.warn("Could not fill Date field {}: {}", field.getFullyQualifiedName(), e.getMessage());
                    }
                } else if (name.contains("signature") || name.contains("digital")) {
                    try {
                        if (field instanceof PDTextField) {
                            ((PDTextField) field).setValue(userName);
                            logger.debug("Filled Signature form field with name: {}", field.getFullyQualifiedName());
                        }
                        List<PDAnnotationWidget> widgets = field.getWidgets();
                        if (widgets != null && !widgets.isEmpty() && signatureRect == null) {
                            PDAnnotationWidget w = widgets.get(0);
                            if (w.getRectangle() != null) {
                                signatureRect = w.getRectangle();
                                signatureWidgetPage = w.getPage();
                            }
                        }
                    } catch (Exception e) {
                        logger.warn("Could not fill Signature field {}: {}", field.getFullyQualifiedName(), e.getMessage());
                    }
                }
            }

            if (signatureWidgetPage != null && signatureRect != null && signatureImageBytes != null && signatureImageBytes.length > 0) {
                try {
                    PDImageXObject pdImage = PDImageXObject.createFromByteArray(document, signatureImageBytes, "signature");
                    float w = signatureRect.getWidth();
                    float h = Math.min(signatureRect.getHeight(), w * pdImage.getHeight() / pdImage.getWidth());
                    try (PDPageContentStream cs = new PDPageContentStream(document, signatureWidgetPage, PDPageContentStream.AppendMode.APPEND, true, true)) {
                        cs.drawImage(pdImage, signatureRect.getLowerLeftX(), signatureRect.getLowerLeftY(), w, h);
                    }
                    drewSignatureImage = true;
                } catch (Exception e) {
                    logger.warn("Could not draw signature image into form field: {}", e.getMessage());
                }
            }
            return drewSignatureImage;
        } catch (Exception e) {
            logger.debug("Waiver template has no AcroForm or error filling fields: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Fallback for templates with static "Digital Signature" and "Date" labels (no form fields).
     * Draws signature image and date on the last page in a clean, professional layout.
     */
    private void drawSignatureOnLastPage(PDDocument document, String userName, byte[] signatureImageBytes, LocalDateTime signedAt) {
        org.apache.pdfbox.pdmodel.PDPageTree pages = document.getPages();
        if (pages == null || pages.getCount() == 0) return;
        PDPage lastPage = pages.get(pages.getCount() - 1);
        float margin = 72;
        float sigWidth = 200;
        float lineSpacing = 8;

        try (PDPageContentStream cs = new PDPageContentStream(document, lastPage, PDPageContentStream.AppendMode.APPEND, true, true)) {
            PDType1Font helvetica = new PDType1Font(FontName.HELVETICA);

            // Position: waiver signature block (below legal text); PDF origin is bottom-left
            float yPos = 200;


            if (signatureImageBytes != null && signatureImageBytes.length > 0) {
                try {
                    PDImageXObject pdImage = PDImageXObject.createFromByteArray(document, signatureImageBytes, "signature");
                    float h = (sigWidth / pdImage.getWidth()) * pdImage.getHeight();
                    cs.drawImage(pdImage, margin, yPos, sigWidth, h);
                    yPos -= h + lineSpacing;
                } catch (Exception e) {
                    logger.warn("Could not draw signature on last page: {}", e.getMessage());
                }
            }

            // Clean, professional sign-off line: "Signed by: Name on yyyy-MM-dd"
            String signLine = "Signed by: " + userName + " on " + signedAt.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
            cs.beginText();
            cs.setFont(helvetica, 11);
            cs.newLineAtOffset(margin, yPos);
            cs.showText(signLine);
            cs.endText();

            logger.debug("Drew signature and date on last template page (no form fields)");
        } catch (Exception e) {
            logger.warn("Could not draw signature on last page: {}", e.getMessage());
        }
    }

    private byte[] mergeSignatureIntoPdf(byte[] templatePdfBytes, String userName, String signatureBase64, String signerIp) throws IOException {
        String base64Data = signatureBase64.contains(",") ? signatureBase64.split(",")[1] : signatureBase64;
        byte[] signatureBytes = Base64.getDecoder().decode(base64Data);
        return mergeSignatureIntoPdf(templatePdfBytes, userName, signatureBytes, signerIp, LocalDateTime.now());
    }

    private byte[] mergeSignatureIntoPdf(byte[] templatePdfBytes, String userName, byte[] signatureImageBytes, String signerIp, LocalDateTime signedAt) throws IOException {
        try (PDDocument document = Loader.loadPDF(templatePdfBytes)) {
            document.setAllSecurityToBeRemoved(true);

            // Fill "Digital Signature" and "Date" on the template (no extra page)
            fillWaiverFormFields(document, userName, signatureImageBytes, signedAt);

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

