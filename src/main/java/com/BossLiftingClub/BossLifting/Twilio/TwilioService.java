package com.BossLiftingClub.BossLifting.Twilio;

import com.twilio.Twilio;
import com.twilio.rest.api.v2010.account.Message;
import com.twilio.rest.verify.v2.service.Verification;
import com.twilio.rest.verify.v2.service.VerificationCheck;
import com.twilio.type.PhoneNumber;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

@Service
public class TwilioService {
    private final String ACCOUNT_SID;
    private final String AUTH_TOKEN;
    private final String VERIFY_SERVICE_SID;
    private final String MESSAGING_SERVICE_SID;
    private final boolean isEnabled;

    public TwilioService(Environment environment) {
        // Read directly from environment variables (Spring Boot maps them automatically)
        // Use getProperty with default empty string to avoid circular references
        this.ACCOUNT_SID = environment.getProperty("TWILIO_ACCOUNT_SID", "");
        this.AUTH_TOKEN = environment.getProperty("TWILIO_AUTH_TOKEN", "");
        this.VERIFY_SERVICE_SID = environment.getProperty("TWILIO_VERIFY_SERVICE_SID", "");
        this.MESSAGING_SERVICE_SID = environment.getProperty("MESSAGING_SERVICE_SID", "");
        
        // Only initialize Twilio if credentials are provided (not empty/null)
        this.isEnabled = ACCOUNT_SID != null && !ACCOUNT_SID.isEmpty() 
                        && AUTH_TOKEN != null && !AUTH_TOKEN.isEmpty()
                        && !ACCOUNT_SID.trim().isEmpty() 
                        && !AUTH_TOKEN.trim().isEmpty();
        
        if (isEnabled) {
            Twilio.init(ACCOUNT_SID, AUTH_TOKEN); // Initialize Twilio client
        }
    }

    public String sendOTP(String phoneNumber) {
        if (!isEnabled) {
            throw new IllegalStateException("Twilio service is not configured. Please set TWILIO_ACCOUNT_SID and TWILIO_AUTH_TOKEN environment variables.");
        }
        Verification verification = Verification.creator(
                VERIFY_SERVICE_SID,
                phoneNumber,
                "sms"
        ).create();
        return verification.getSid();
    }

    public boolean verifyOTP(String phoneNumber, String otp) {
        if (!isEnabled) {
            throw new IllegalStateException("Twilio service is not configured. Please set TWILIO_ACCOUNT_SID and TWILIO_AUTH_TOKEN environment variables.");
        }
        VerificationCheck verificationCheck = VerificationCheck.creator(VERIFY_SERVICE_SID)
                .setCode(otp)
                .setTo(phoneNumber)
                .create();
        return "approved".equals(verificationCheck.getStatus());
    }

    public String sendSMS(String phoneNumber, String message) {
        if (!isEnabled) {
            throw new IllegalStateException("Twilio service is not configured. Please set TWILIO_ACCOUNT_SID and TWILIO_AUTH_TOKEN environment variables.");
        }
        Message twilioMessage = Message.creator(
                        new PhoneNumber(phoneNumber), // To
                        new PhoneNumber("+18447306626"), // From: Your Twilio phone number
                        message)
                .create();
        return twilioMessage.getSid();
    }
}