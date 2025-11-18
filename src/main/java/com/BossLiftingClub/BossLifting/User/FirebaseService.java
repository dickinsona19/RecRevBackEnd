package com.BossLiftingClub.BossLifting.User;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.storage.Acl;
import com.google.cloud.storage.Blob;
import com.google.cloud.storage.Bucket;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.cloud.StorageClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.PostConstruct;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.UUID;

@Service
public class FirebaseService {
    private static final Logger logger = LoggerFactory.getLogger(FirebaseService.class);
    
    private final String bucketName = "clt-liftingclub-llc.firebasestorage.app";

    // Fetch the service account JSON from the environment variable
    // Optional - Firebase will only initialize if provided
    @Value("${FIREBASE_SERVICE_ACCOUNT_JSON:}")
    private String serviceAccountJson;

    @PostConstruct
    public void initializeFirebase() throws IOException {
        // Only initialize Firebase if service account JSON is provided
        if (serviceAccountJson == null || serviceAccountJson.isEmpty() || serviceAccountJson.trim().isEmpty()) {
            logger.warn("Firebase service account JSON not provided. Firebase features will be disabled.");
            return;
        }
        
        if (FirebaseApp.getApps().isEmpty()) {
            // Load the service account JSON directly from the environment variable
            InputStream serviceAccount = new ByteArrayInputStream(serviceAccountJson.getBytes());

            FirebaseOptions options = FirebaseOptions.builder()
                    .setCredentials(GoogleCredentials.fromStream(serviceAccount))
                    .setStorageBucket(bucketName)
                    .build();

            FirebaseApp.initializeApp(options);
        }
    }

    public String uploadImage(MultipartFile file) throws IOException {
        String fileName = "profile_pictures/" + UUID.randomUUID() + "-" + file.getOriginalFilename();

        // Explicitly specify the bucket name
        Bucket bucket = StorageClient.getInstance().bucket(bucketName);
        Blob blob = bucket.create(fileName, file.getBytes(), file.getContentType());

        // Make the file public
        blob.createAcl(Acl.of(Acl.User.ofAllUsers(), Acl.Role.READER));

        return String.format("https://storage.googleapis.com/%s/%s", bucket.getName(), fileName);
    }
}