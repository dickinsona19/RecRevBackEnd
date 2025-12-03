package com.BossLiftingClub.BossLifting.Security;

import com.BossLiftingClub.BossLifting.Client.Client;
import com.BossLiftingClub.BossLifting.Client.ClientRepository;
import com.BossLiftingClub.BossLifting.Business.Staff.Staff;
import com.BossLiftingClub.BossLifting.Business.Staff.StaffRepository;
import com.BossLiftingClub.BossLifting.User.PasswordAuth.JwtUtil;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    
    private static final Logger logger = LoggerFactory.getLogger(JwtAuthenticationFilter.class);

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private ClientRepository clientRepository;
    
    @Autowired
    private StaffRepository staffRepository;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        final String authorizationHeader = request.getHeader("Authorization");

        String email = null;
        String jwt = null;

        // Extract JWT from Authorization header
        if (authorizationHeader != null && authorizationHeader.startsWith("Bearer ")) {
            jwt = authorizationHeader.substring(7);
            try {
                email = jwtUtil.extractEmail(jwt);
            } catch (Exception e) {
                // Invalid token, continue without authentication
                logger.warn("Invalid JWT token: " + e.getMessage());
            }
        }

        // Validate token and set authentication
        if (email != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            try {
                // Validate token first
                if (!jwtUtil.validateToken(jwt, email)) {
                    logger.warn("Invalid JWT token for email: {}", email);
                    filterChain.doFilter(request, response);
                    return;
                }
                
                // Check user type from token (may be null for old tokens)
                String userType = jwtUtil.extractUserType(jwt);
                logger.info("Processing authentication for email: {}, userType: {}", email, userType);
                
                if ("STAFF".equals(userType)) {
                    // Handle staff authentication
                    Integer staffId = jwtUtil.extractStaffId(jwt);
                    logger.debug("Extracted staffId from token: {}", staffId);
                    
                    if (staffId != null) {
                        // Use findByIdWithBusiness to eagerly load business relationship
                        Optional<Staff> staffOpt = staffRepository.findByIdWithBusiness(staffId);
                        
                        if (staffOpt.isPresent()) {
                            Staff staff = staffOpt.get();
                            
                            // Verify staff is active
                            if (staff.getIsActive() != null && staff.getIsActive()) {
                                // Create StaffPrincipal
                                StaffPrincipal staffPrincipal = new StaffPrincipal(staff);
                                
                                // Create authentication token with authorities
                                UsernamePasswordAuthenticationToken authToken =
                                    new UsernamePasswordAuthenticationToken(
                                        staffPrincipal, 
                                        null, 
                                        staffPrincipal.getAuthorities()
                                    );

                                authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

                                // Set authentication in security context
                                SecurityContextHolder.getContext().setAuthentication(authToken);
                                logger.debug("Staff authentication set for email: {}, staffId: {}", email, staffId);
                            } else {
                                logger.warn("Staff {} is not active", staffId);
                            }
                        } else {
                            logger.warn("Staff not found with ID: {}", staffId);
                        }
                    } else {
                        logger.warn("Staff ID is null in token for email: {}", email);
                    }
                } else {
                    // Handle client authentication (default or if userType is null/CLIENT)
                    // If userType is null, treat as CLIENT (backward compatibility)
                    Optional<Client> clientOpt = clientRepository.findByEmail(email);

                    if (clientOpt.isPresent()) {
                        Client client = clientOpt.get();

                        // Create UserPrincipal with CLIENT role
                        UserPrincipal userPrincipal = new UserPrincipal(client, "CLIENT");
                        
                        // Create authentication token with authorities
                        UsernamePasswordAuthenticationToken authToken =
                            new UsernamePasswordAuthenticationToken(
                                userPrincipal, 
                                null, 
                                userPrincipal.getAuthorities()
                            );

                        authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

                        // Set authentication in security context
                        SecurityContextHolder.getContext().setAuthentication(authToken);
                        logger.debug("Client authentication set for email: {}", email);
                    } else {
                        logger.warn("Client not found with email: {}", email);
                    }
                }
                
                // Log if authentication was not set
                if (SecurityContextHolder.getContext().getAuthentication() == null) {
                    logger.warn("Authentication was not set for email: {}, userType: {}", email, userType);
                }
            } catch (Exception e) {
                // Invalid token or missing claims, log error but continue
                logger.error("Error processing JWT token for email {}: {}", email, e.getMessage(), e);
            }
        } else if (email == null) {
            logger.debug("No email extracted from JWT token");
        } else {
            logger.debug("Authentication already set in security context");
        }

        filterChain.doFilter(request, response);
    }
}
