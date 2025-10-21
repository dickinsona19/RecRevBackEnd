package com.BossLiftingClub.BossLifting.User.PasswordAuth;


import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

@Component
public class JwtUtil {

    @Value("${jwt.secret:4l6ufaKeyrA7QXgkgFB/PRqK/UKG2iGRHFjB5Mx9Cv2A7Tcp7LwbkplNtnk4ccdqr+EbzU2C/JBnUL9fQmTk4A==}")
    private String secret;

    @Value("${jwt.expiration:604800000}") // 7 days in milliseconds
    private Long expiration;

    @Value("${jwt.refresh.expiration:2592000000}") // 30 days in milliseconds
    private Long refreshExpiration;

    // Legacy user expiration (5 years)
    private long userExpiration = 1000L * 60 * 60 * 24 * 365 * 5;

    private SecretKey getSigningKey() {
        byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8);
        return Keys.hmacShaKeyFor(keyBytes);
    }

    // ========== USER/USERNAME METHODS (Legacy) ==========

    // Generate token for user (legacy method with username)
    public String generateToken(String username) {
        return Jwts.builder()
                .subject(username)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + userExpiration))
                .signWith(getSigningKey())
                .compact();
    }

    // Validate token (legacy method)
    public boolean validateToken(String token) {
        try {
            Jwts.parser()
                    .verifyWith(getSigningKey())
                    .build()
                    .parseSignedClaims(token);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    // Extract username from token (legacy method)
    public String getUsernameFromToken(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
        return claims.getSubject();
    }

    // ========== CLIENT AUTHENTICATION METHODS ==========

    // Extract email from token
    public String extractEmail(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    // Extract expiration date from token
    public Date extractExpiration(String token) {
        return extractClaim(token, Claims::getExpiration);
    }

    // Extract a specific claim from token
    public <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        final Claims claims = extractAllClaims(token);
        return claimsResolver.apply(claims);
    }

    // Extract all claims from token
    private Claims extractAllClaims(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    // Check if token is expired
    private Boolean isTokenExpired(String token) {
        return extractExpiration(token).before(new Date());
    }

    // Generate access token for client
    public String generateToken(String email, Integer clientId) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("clientId", clientId);
        claims.put("type", "access");
        return createToken(claims, email, expiration);
    }

    // Generate refresh token for client
    public String generateRefreshToken(String email, Integer clientId) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("clientId", clientId);
        claims.put("type", "refresh");
        return createToken(claims, email, refreshExpiration);
    }

    // Create token with claims, subject, and expiration
    private String createToken(Map<String, Object> claims, String subject, Long expirationTime) {
        return Jwts.builder()
                .claims(claims)
                .subject(subject)
                .issuedAt(new Date(System.currentTimeMillis()))
                .expiration(new Date(System.currentTimeMillis() + expirationTime))
                .signWith(getSigningKey())
                .compact();
    }

    // Validate token with email
    public Boolean validateToken(String token, String email) {
        final String extractedEmail = extractEmail(token);
        return (extractedEmail.equals(email) && !isTokenExpired(token));
    }

    // Extract client ID from token
    public Integer extractClientId(String token) {
        Claims claims = extractAllClaims(token);
        return claims.get("clientId", Integer.class);
    }
}