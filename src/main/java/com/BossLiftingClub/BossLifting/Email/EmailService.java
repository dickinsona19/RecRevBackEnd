package com.BossLiftingClub.BossLifting.Email;

import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.io.UnsupportedEncodingException;

@Service
public class EmailService {

    private static final Logger logger = LoggerFactory.getLogger(EmailService.class);

    @Autowired
    private JavaMailSender mailSender;

    @Value("${spring.mail.username}")
    private String systemEmail;

    public String sendEmail(String to, String subject, String text) {
        return sendEmail(to, subject, text, null);
    }

    public String sendEmail(String to, String subject, String text, String contactEmail) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(systemEmail);
            message.setTo(to);
            message.setSubject(subject);
            
            // Append contact email footer if provided
            String emailText = text;
            if (contactEmail != null && !contactEmail.trim().isEmpty()) {
                emailText = text + "\n\n---\nIf you have any questions, please contact " + contactEmail;
            }
            
            message.setText(emailText);
            mailSender.send(message);
            return "Email sent successfully";
        } catch (Exception e) {
            throw new RuntimeException("Failed to send email: " + e.getMessage(), e);
        }
    }

    /**
     * Sends a white-labeled blast email.
     *
     * @param to Recipient email address
     * @param subject Email subject
     * @param body Email body (supports HTML)
     * @param businessName The name of the business to appear as the sender name
     * @param businessReplyTo The email address for replies
     */
    public void sendBlastEmail(String to, String subject, String body, String businessName, String businessReplyTo) {
        sendBlastEmail(to, subject, body, businessName, businessReplyTo, null);
    }

    /**
     * Sends a white-labeled blast email with contact email footer.
     *
     * @param to Recipient email address
     * @param subject Email subject
     * @param body Email body (supports HTML)
     * @param businessName The name of the business to appear as the sender name
     * @param businessReplyTo The email address for replies
     * @param contactEmail The contact email to include in footer (optional)
     */
    public void sendBlastEmail(String to, String subject, String body, String businessName, String businessReplyTo, String contactEmail) {
        try {
            MimeMessage mimeMessage = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, "UTF-8");

            helper.setTo(to);
            helper.setSubject(subject);
            
            // Append contact email footer if provided
            String emailBody = body;
            if (contactEmail != null && !contactEmail.trim().isEmpty()) {
                String footer = "<br><br><hr style='border: none; border-top: 1px solid #e0e0e0; margin: 20px 0;'><p style='color: #666; font-size: 12px;'>If you have any questions, please contact <a href='mailto:" + contactEmail + "'>" + contactEmail + "</a></p>";
                emailBody = body + footer;
            }
            
            helper.setText(emailBody, true); // Enable HTML

            // Set From: "Business Name <system@email.com>"
            try {
                helper.setFrom(systemEmail, businessName);
            } catch (UnsupportedEncodingException e) {
                logger.warn("Failed to set custom sender name '{}', falling back to system email only.", businessName);
                helper.setFrom(systemEmail);
            }

            // Set Reply-To: "business@email.com"
            if (businessReplyTo != null && !businessReplyTo.isEmpty()) {
                helper.setReplyTo(businessReplyTo);
            }

            mailSender.send(mimeMessage);
            logger.info("Blast email sent to {} for business '{}'", to, businessName);

        } catch (Exception e) {
            logger.error("Failed to send blast email to {} for business '{}': {}", to, businessName, e.getMessage());
            throw new RuntimeException("Failed to send blast email: " + e.getMessage(), e);
        }
    }
}