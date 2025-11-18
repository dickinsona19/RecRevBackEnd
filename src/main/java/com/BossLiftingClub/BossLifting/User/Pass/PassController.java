package com.BossLiftingClub.BossLifting.User.Pass;


import com.twilio.Twilio;
import com.twilio.rest.api.v2010.account.Message;
import com.twilio.type.PhoneNumber;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@RestController
public class PassController {
    private static final Logger logger = LoggerFactory.getLogger(PassController.class);
    private static final String TEMP_DIR = "temp_passes";

    @Value("${pass2u.download.base-url:https://pass2u.net/download}")
    private String pass2uDownloadBaseUrl;
    @Autowired
    private PassService passService;

    // Twilio configuration - optional (for SMS pass delivery if needed)
    // Using Environment to avoid circular placeholder references
    @Autowired
    private org.springframework.core.env.Environment environment;
    
    private String getTwilioAccountSid() {
        return environment.getProperty("TWILIO_ACCOUNT_SID", "");
    }
    
    private String getTwilioAuthToken() {
        return environment.getProperty("TWILIO_AUTH_TOKEN", "");
    }
    
    private String getTwilioPhoneNumber() {
        return environment.getProperty("TWILIO_PHONE_NUMBER", environment.getProperty("twilio.phone.number", ""));
    }


    @GetMapping("/pass")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<String> getPass(
            @RequestParam(value = "userId", defaultValue = "123") String userId) throws Exception {
        // Generate pass and get passId
        PassService.PassResult passResult = passService.generatePass(userId);
        String passUrl = getPassDownloadUrl(passResult.passId(), passResult.passData(), userId);

        return ResponseEntity.ok("Here is your pass download link: " + passUrl);
    }

    private String getPassDownloadUrl(String passId, byte[] passData, String userId) throws Exception {
        // Option 1: Return Pass2U's dedicated download URL (recommended)
        return pass2uDownloadBaseUrl + "/" + passId;

        // Option 2: Save locally and host yourself (uncomment if needed)
        /*
        Path tempDirPath = Paths.get(TEMP_DIR);
        if (!Files.exists(tempDirPath)) {
            Files.createDirectories(tempDirPath);
        }

        String fileName = "cltlifting-" + userId + ".pkpass";
        Path filePath = tempDirPath.resolve(fileName);
        try (var fos = Files.newOutputStream(filePath)) {
            fos.write(passData);
        }

        // Assumes your server is configured to serve files from TEMP_DIR at /passes/
        return "https://yourdomain.com/passes/" + fileName;
        */
    }
}

//    private String savePassAndGetUrl(byte[] passData, String userId) throws Exception {
//        Path tempDirPath = Paths.get(TEMP_DIR);
//        if (!Files.exists(tempDirPath)) {
//            Files.createDirectories(tempDirPath);
//        }
//
//        String fileName = "cltlifting-" + userId + ".pkpass";
//        Path filePath = tempDirPath.resolve(fileName);
//        try (FileOutputStream fos = new FileOutputStream(filePath.toFile())) {
//            fos.write(passData);
//        }
//
//        // Return the URL to download the pass
//        return "https://pass2u.net/download/" + fileName;
//    }
//
//
//    private void sendPassViaTwilio(String passUrl, String recipientPhone) {
//        String accountSid = getTwilioAccountSid();
//        String authToken = getTwilioAuthToken();
//        String phoneNumber = getTwilioPhoneNumber();
//        
//        if (accountSid.isEmpty() || authToken.isEmpty()) {
//            logger.warn("Twilio not configured, cannot send SMS");
//            return;
//        }
//        
//        Twilio.init(accountSid, authToken);
//
//        Message message = Message.creator(
//                new PhoneNumber(recipientPhone),
//                new PhoneNumber(phoneNumber),
//                "Here is your CLTlifting pass: " + passUrl + " Open on your iPhone to add it to Apple Wallet."
//        ).create();
//
//        System.out.println("SMS sent with SID: " + message.getSid());
//    }
