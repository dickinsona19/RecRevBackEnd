package com.BossLiftingClub.BossLifting.Security;

import com.BossLiftingClub.BossLifting.Client.Client;
import com.BossLiftingClub.BossLifting.Client.ClientRepository;
import com.BossLiftingClub.BossLifting.Club.Staff.Staff;
import com.BossLiftingClub.BossLifting.Club.Staff.StaffRepository;
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

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

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
                // Check user type from token
                String userType = jwtUtil.extractUserType(jwt);
                
                if ("STAFF".equals(userType)) {
                    // Handle staff authentication
                    Integer staffId = jwtUtil.extractStaffId(jwt);
                    if (staffId != null && jwtUtil.validateToken(jwt, email)) {
                        Optional<Staff> staffOpt = staffRepository.findById(staffId);
                        
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
                            }
                        }
                    }
                } else {
                    // Handle client authentication (default)
                    Optional<Client> clientOpt = clientRepository.findByEmail(email);

                    if (clientOpt.isPresent()) {
                        Client client = clientOpt.get();

                        if (jwtUtil.validateToken(jwt, email)) {
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
                        }
                    }
                }
            } catch (Exception e) {
                // Invalid token or missing claims, continue without authentication
                logger.warn("Error processing JWT token: " + e.getMessage());
            }
        }

        filterChain.doFilter(request, response);
    }
}
