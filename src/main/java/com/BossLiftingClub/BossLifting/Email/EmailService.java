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
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(systemEmail);
            message.setTo(to);
            message.setSubject(subject);
            message.setText(text);
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
        try {
            MimeMessage mimeMessage = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, "UTF-8");

            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(body, true); // Enable HTML

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