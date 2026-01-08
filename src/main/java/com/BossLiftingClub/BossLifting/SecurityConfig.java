package com.BossLiftingClub.BossLifting;

import com.BossLiftingClub.BossLifting.Security.JwtAuthenticationFilter;
import com.BossLiftingClub.BossLifting.Security.RateLimitingFilter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Autowired
    private JwtAuthenticationFilter jwtAuthenticationFilter;
    
    @Autowired
    private RateLimitingFilter rateLimitingFilter;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource())) // Enable CORS
                .csrf(csrf -> csrf.disable()) // Disable CSRF for stateless API
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)) // Stateless sessions
                .authorizeHttpRequests(auth -> auth
                        // Public endpoints (authentication endpoints)
                        .requestMatchers("/api/clients/login").permitAll()
                        .requestMatchers("/api/clients/signup").permitAll()
                        .requestMatchers("/api/clients/refresh").permitAll()
                        
                        // Stripe webhooks (no authentication, verified by signature)
                        .requestMatchers("/api/stripe/webhook").permitAll()
                        .requestMatchers("/api/stripe/webhook/subscription").permitAll()
                        
                        // Health check endpoints (if needed)
                        .requestMatchers("/actuator/health").permitAll()
                        
                        // Test token endpoint (for development/testing)
                        .requestMatchers("/api/clients/test-token").permitAll()
                        
                        // All other endpoints require authentication
                        .requestMatchers("/api/**").authenticated()
                        .requestMatchers("/users/**").authenticated()
                        .requestMatchers("/products/**").authenticated()
                        .requestMatchers("/clients/**").authenticated() // Allow authenticated access to /clients endpoints
                        
                        // Default: require authentication
                        .anyRequest().authenticated()
                )
                .addFilterBefore(rateLimitingFilter, UsernamePasswordAuthenticationFilter.class) // Add rate limiting filter first
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class); // Add JWT filter
        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();

        // Allow specific origins for development (use specific origins in production)
        // Note: Cannot use wildcard (*) with allowCredentials(true)
        config.addAllowedOrigin("http://localhost:5173");
        config.addAllowedOrigin("http://localhost:3000");
        config.addAllowedOrigin("http://127.0.0.1:5173");
        config.addAllowedOrigin("http://127.0.0.1:3000");
        config.addAllowedOrigin("https://recrevfrontend.onrender.com");

        // Allowed HTTP methods
        config.setAllowedMethods(List.of("GET", "POST", "OPTIONS", "DELETE", "PUT", "PATCH"));

        // Allowed headers
        config.setAllowedHeaders(List.of("*"));

        // Expose headers (needed for JWT tokens in Authorization header)
        config.setExposedHeaders(List.of("Authorization"));

        // Allow credentials (cookies, auth headers)
        config.setAllowCredentials(true);

        // Apply this config to all paths
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}