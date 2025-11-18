package com.BossLiftingClub.BossLifting.Config;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

import java.util.ArrayList;
import java.util.List;

/**
 * Validates required environment variables on application startup
 */
@Configuration
public class EnvironmentValidationConfig {
    
    private static final Logger logger = LoggerFactory.getLogger(EnvironmentValidationConfig.class);
    
    @Value("${spring.profiles.active:local}")
    private String activeProfile;
    
    private final Environment environment;
    
    public EnvironmentValidationConfig(Environment environment) {
        this.environment = environment;
    }
    
    @PostConstruct
    public void validateEnvironmentVariables() {
        List<String> missingVariables = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        
        // Critical variables (required in all environments except local)
        if (!"local".equals(activeProfile)) {
            validateRequired("STRIPE_SECRET_KEY", missingVariables);
            validateRequired("STRIPE_WEBHOOK_SECRET", missingVariables);
            validateRequired("JWT_SECRET", missingVariables);
        }
        
        // Important variables (warn if missing but don't fail)
        validateOptional("STRIPE_SECRET_KEY", warnings);
        validateOptional("STRIPE_WEBHOOK_SECRET", warnings);
        validateOptional("JWT_SECRET", warnings);
        
        // Optional but recommended
        validateOptional("TWILIO_ACCOUNT_SID", warnings);
        validateOptional("TWILIO_AUTH_TOKEN", warnings);
        validateOptional("PASS2U_API_KEY", warnings);
        validateOptional("FIREBASE_SERVICE_ACCOUNT_PATH", warnings);
        
        // Log results
        if (!missingVariables.isEmpty()) {
            logger.error("==========================================");
            logger.error("MISSING REQUIRED ENVIRONMENT VARIABLES:");
            logger.error("==========================================");
            missingVariables.forEach(var -> logger.error("  - {}", var));
            logger.error("==========================================");
            throw new IllegalStateException(
                "Application cannot start. Missing required environment variables: " + 
                String.join(", ", missingVariables)
            );
        }
        
        if (!warnings.isEmpty()) {
            logger.warn("==========================================");
            logger.warn("MISSING OPTIONAL ENVIRONMENT VARIABLES:");
            logger.warn("==========================================");
            warnings.forEach(var -> logger.warn("  - {} (some features may not work)", var));
            logger.warn("==========================================");
        } else {
            logger.info("✅ All environment variables validated successfully");
        }
    }
    
    private void validateRequired(String varName, List<String> missing) {
        String value = environment.getProperty(varName);
        if (value == null || value.isEmpty() || "NOT_SET".equals(value)) {
            missing.add(varName);
        }
    }
    
    private void validateOptional(String varName, List<String> warnings) {
        String value = environment.getProperty(varName);
        if (value == null || value.isEmpty() || "NOT_SET".equals(value)) {
            warnings.add(varName);
        }
    }
}

