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
        return uploadFile(file, "profile_pictures");
    }

    public String uploadFile(MultipartFile file, String directory) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("File cannot be empty");
        }

        String resolvedDirectory = (directory == null || directory.isBlank())
                ? ""
                : directory.replaceAll("^/+", "").replaceAll("/+$", "") + "/";

        String fileName = resolvedDirectory + UUID.randomUUID() + "-" + file.getOriginalFilename();

        Bucket bucket = resolveBucket();
        Blob blob = bucket.create(fileName, file.getBytes(), determineContentType(file));
        blob.createAcl(Acl.of(Acl.User.ofAllUsers(), Acl.Role.READER));

        return String.format("https://storage.googleapis.com/%s/%s", bucket.getName(), fileName);
    }

    private Bucket resolveBucket() {
        if (FirebaseApp.getApps().isEmpty()) {
            throw new IllegalStateException("Firebase is not configured. Provide FIREBASE_SERVICE_ACCOUNT_JSON.");
        }
        return StorageClient.getInstance().bucket(bucketName);
    }

    private String determineContentType(MultipartFile file) {
        return file.getContentType() != null ? file.getContentType() : "application/octet-stream";
    }

    /**
     * Downloads a file from Firebase Storage by its public URL.
     * @param fileUrl The public URL of the file (e.g., https://storage.googleapis.com/bucket-name/path/to/file.pdf)
     * @return The file content as bytes
     * @throws IOException if the file cannot be downloaded
     */
    public byte[] downloadFile(String fileUrl) throws IOException {
        if (FirebaseApp.getApps().isEmpty()) {
            throw new IllegalStateException("Firebase is not configured. Provide FIREBASE_SERVICE_ACCOUNT_JSON.");
        }

        // Extract the file path from the URL
        // URL format: https://storage.googleapis.com/BUCKET_NAME/path/to/file.pdf
        String path = extractPathFromUrl(fileUrl);
        if (path == null || path.isEmpty()) {
            throw new IllegalArgumentException("Invalid Firebase Storage URL: " + fileUrl);
        }

        Bucket bucket = resolveBucket();
        Blob blob = bucket.get(path);
        
        if (blob == null || !blob.exists()) {
            throw new IOException("File not found in Firebase Storage: " + path);
        }

        return blob.getContent();
    }

    /**
     * Extracts the file path from a Firebase Storage public URL.
     * @param fileUrl The full URL (e.g., https://storage.googleapis.com/bucket-name/path/to/file.pdf)
     * @return The file path (e.g., path/to/file.pdf)
     */
    private String extractPathFromUrl(String fileUrl) {
        try {
            // Parse the URL to extract the path
            // URL format: https://storage.googleapis.com/bucket-name/path/to/file.pdf
            java.net.URL url = new java.net.URL(fileUrl);
            String path = url.getPath();
            
            // Remove leading slash if present
            if (path.startsWith("/")) {
                path = path.substring(1);
            }
            
            // The path includes the bucket name, so we need to remove it
            // getPath() returns: /bucket-name/path/to/file.pdf
            // After removing leading slash: bucket-name/path/to/file.pdf
            // We need: path/to/file.pdf
            if (path.startsWith(bucketName + "/")) {
                path = path.substring(bucketName.length() + 1);
            } else if (path.startsWith(bucketName)) {
                // Handle case where path equals bucket name
                path = path.substring(bucketName.length());
            }
            
            // URL decode the path in case it's already encoded
            try {
                path = java.net.URLDecoder.decode(path, java.nio.charset.StandardCharsets.UTF_8.name());
            } catch (java.io.UnsupportedEncodingException e) {
                // Should never happen with UTF-8, but continue with original path if it does
                logger.warn("Failed to decode URL path, using original: " + path);
            }
            
            return path;
        } catch (java.net.MalformedURLException e) {
            logger.error("Failed to parse URL: " + fileUrl, e);
            return null;
        }
    }
}