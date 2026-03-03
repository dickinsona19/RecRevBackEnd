package com.BossLiftingClub.BossLifting.User;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import org.springframework.context.annotation.Configuration;

import jakarta.annotation.PostConstruct;
import java.io.ByteArrayInputStream;
import java.io.InputStream;

@Configuration
public class FirebaseConfig {

    @PostConstruct
    public void initialize() {
        try {
            String firebaseJson = System.getenv("FIREBASE_SERVICE_ACCOUNT_JSON");

            if (firebaseJson == null) {
                throw new IllegalStateException("FIREBASE_SERVICE_ACCOUNT_JSON is not set");
            }

            InputStream serviceAccount = new ByteArrayInputStream(firebaseJson.getBytes());

            FirebaseOptions options = FirebaseOptions.builder()
                    .setCredentials(GoogleCredentials.fromStream(serviceAccount))
                    .build();

            FirebaseApp.initializeApp(options);

            System.out.println("Firebase has been initialized.");

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}