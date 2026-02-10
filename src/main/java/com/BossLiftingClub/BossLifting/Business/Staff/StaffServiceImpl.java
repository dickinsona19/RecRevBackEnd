package com.BossLiftingClub.BossLifting.Business.Staff;

import com.BossLiftingClub.BossLifting.Business.Business;
import com.BossLiftingClub.BossLifting.Business.BusinessRepository;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class StaffServiceImpl implements StaffService {
    @Autowired
    private StaffRepository staffRepository;
    
    @Autowired
    private BusinessRepository businessRepository;
    
    @Autowired
    private StaffInvitationRepository staffInvitationRepository;
    
    @Autowired
    private JavaMailSender mailSender;
    
    @Autowired
    private PasswordEncoder passwordEncoder;
    
    @Value("${app.frontend.url:http://localhost:5173}")
    private String frontendUrl;
    
    private static final SecureRandom secureRandom = new SecureRandom();
    private static final int INVITE_TOKEN_LENGTH = 32;
    private static final int INVITE_TOKEN_EXPIRY_DAYS = 7;

    @Override
    public StaffDTO createStaff(StaffDTO staffDTO) {
        Staff staff = new Staff();
        staff.setEmail(staffDTO.getEmail());
        staff.setPassword(staffDTO.getPassword());
        staff.setType(staffDTO.getType());
        Staff savedStaff = staffRepository.save(staff);
        return mapToDTO(savedStaff);
    }

    @Override
    public StaffDTO getStaffById(Integer id) {
        Staff staff = staffRepository.findByIdWithBusiness(id)
                .orElseThrow(() -> new RuntimeException("Staff not found"));
        return mapToDTO(staff);
    }

    @Override
    public List<StaffDTO> getAllStaff() {
        return staffRepository.findAll().stream()
                .map(this::mapToDTO)
                .collect(Collectors.toList());
    }

    @Override
    public StaffDTO updateStaff(Integer id, StaffDTO staffDTO) {
        Staff staff = staffRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Staff not found"));
        staff.setEmail(staffDTO.getEmail());
        staff.setPassword(staffDTO.getPassword());
        staff.setType(staffDTO.getType());
        Staff updatedStaff = staffRepository.save(staff);
        return mapToDTO(updatedStaff);
    }

    @Override
    public void deleteStaff(Integer id) {
        staffRepository.deleteById(id);
    }

    @Override
    @Transactional
    public StaffDTO inviteStaff(String email, String role, Long businessId, Integer invitedBy) {
        // Validate business exists
        Business business = businessRepository.findById(businessId)
                .orElseThrow(() -> new RuntimeException("Business not found with ID: " + businessId));
        
        // Check if staff with this email already exists for this business
        List<Staff> existingStaff = staffRepository.findByEmailAndIsActiveTrue(email);
        Optional<Staff> existingForBusiness = existingStaff.stream()
                .filter(s -> s.getBusiness() != null && s.getBusiness().getId().equals(businessId))
                .findFirst();
        
        if (existingForBusiness.isPresent()) {
            throw new RuntimeException("Staff member with email " + email + " already exists for this business");
        }
        
        // Check if there's already a pending invitation for this email and business
        Optional<StaffInvitation> existingInvitation = staffInvitationRepository
                .findByInvitedEmailAndBusinessIdAndStatus(email, businessId, StaffInvitation.InvitationStatus.PENDING);
        
        if (existingInvitation.isPresent()) {
            throw new RuntimeException("A pending invitation already exists for " + email);
        }
        
        // Generate invite token
        String inviteToken = generateInviteToken();
        LocalDateTime expiryDate = LocalDateTime.now().plusDays(INVITE_TOKEN_EXPIRY_DAYS);
        
        // Create staff invitation record
        StaffInvitation invitation = new StaffInvitation();
        invitation.setBusiness(business);
        invitation.setInvitedEmail(email);
        invitation.setRole(role != null ? role : "TEAM_MEMBER");
        invitation.setStatus(StaffInvitation.InvitationStatus.PENDING);
        invitation.setInviteToken(inviteToken);
        invitation.setInviteTokenExpiry(expiryDate);
        invitation.setInvitedBy(invitedBy);
        invitation.setCreatedAt(LocalDateTime.now());
        
        StaffInvitation savedInvitation = staffInvitationRepository.save(invitation);
        
        // Send invitation email
        try {
            sendInvitationEmail(email, inviteToken, business.getTitle(), role);
        } catch (Exception e) {
            // Log error but don't fail - invitation is saved
            System.err.println("Failed to send invitation email: " + e.getMessage());
        }
        
        // Return a DTO representing the pending invitation
        StaffDTO dto = new StaffDTO();
        dto.setEmail(email);
        dto.setRole(role != null ? role : "TEAM_MEMBER");
        dto.setType(role != null ? role : "TEAM_MEMBER");
        dto.setBusinessId(businessId);
        dto.setIsActive(false);
        dto.setInvitedBy(invitedBy);
        dto.setCreatedAt(LocalDateTime.now());
        return dto;
    }
    
    private String generateInviteToken() {
        byte[] tokenBytes = new byte[INVITE_TOKEN_LENGTH];
        secureRandom.nextBytes(tokenBytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(tokenBytes);
    }
    
    private void sendInvitationEmail(String email, String inviteToken, String businessName, String role) throws MessagingException {
        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
        
        String inviteLink = frontendUrl + "/staff-signup?token=" + inviteToken;
        
        // Format role for display
        String roleDisplay = role != null ? role.replace("_", " ") : "Team Member";
        if (roleDisplay.equalsIgnoreCase("ADMIN")) {
            roleDisplay = "Admin";
        } else if (roleDisplay.equalsIgnoreCase("MANAGER")) {
            roleDisplay = "Manager";
        } else if (roleDisplay.equalsIgnoreCase("TEAM_MEMBER")) {
            roleDisplay = "Team Member";
        }
        
        String subject = "Staff Invitation from " + businessName;
        String htmlContent = String.format("""
            <!DOCTYPE html>
            <html>
            <head>
                <style>
                    body { font-family: Arial, sans-serif; line-height: 1.6; color: #333; }
                    .container { max-width: 600px; margin: 0 auto; padding: 20px; }
                    .header { background: linear-gradient(135deg, #1e293b, #334155); color: white; padding: 20px; text-align: center; border-radius: 8px 8px 0 0; }
                    .content { background: #f8f9fa; padding: 30px; border-radius: 0 0 8px 8px; }
                    .button { display: inline-block; padding: 12px 24px; background: linear-gradient(135deg, #10b981, #059669); color: white; text-decoration: none; border-radius: 6px; font-weight: 600; margin: 20px 0; }
                    .footer { text-align: center; color: #777; font-size: 12px; margin-top: 20px; }
                </style>
            </head>
            <body>
                <div class="container">
                    <div class="header">
                        <h2>Staff Invitation</h2>
                    </div>
                    <div class="content">
                        <p>Hello,</p>
                        <p>This gym is inviting you to join as a Staff Member (<strong>%s</strong>).</p>
                        <p>Click the button below to complete your onboarding and set up your account:</p>
                        <div style="text-align: center;">
                            <a href="%s" class="button">Complete Onboarding</a>
                        </div>
                        <p>This invitation link will expire in 7 days.</p>
                        <p>If you didn't expect this invitation, you can safely ignore this email.</p>
                    </div>
                    <div class="footer">
                        <p>This is an automated message from RecRev</p>
                    </div>
                </div>
            </body>
            </html>
            """, roleDisplay, inviteLink);
        
        helper.setTo(email);
        helper.setSubject(subject);
        helper.setText(htmlContent, true);
        helper.setFrom("RecRev <contact@cltliftingclub.com>");
        
        mailSender.send(message);
    }

    @Override
    @Transactional
    public StaffDTO acceptInvite(String inviteToken, String password) {
        return acceptInvite(inviteToken, password, null, null);
    }
    
    @Transactional
    public StaffDTO acceptInvite(String inviteToken, String password, String firstName, String lastName) {
        // Find invitation by token
        Optional<StaffInvitation> invitationOpt = staffInvitationRepository
                .findByInviteTokenAndStatus(inviteToken, StaffInvitation.InvitationStatus.PENDING);
        
        if (invitationOpt.isEmpty()) {
            throw new RuntimeException("Invalid invitation token");
        }
        
        StaffInvitation invitation = invitationOpt.get();
        
        // Check if token is expired
        if (invitation.getInviteTokenExpiry() != null && invitation.getInviteTokenExpiry().isBefore(LocalDateTime.now())) {
            invitation.setStatus(StaffInvitation.InvitationStatus.EXPIRED);
            staffInvitationRepository.save(invitation);
            throw new RuntimeException("Invitation token has expired");
        }
        
        // Check if email already has an active staff account for this business
        List<Staff> existingStaff = staffRepository.findByEmailAndIsActiveTrue(invitation.getInvitedEmail());
        Optional<Staff> existingForBusiness = existingStaff.stream()
                .filter(s -> s.getBusiness() != null && s.getBusiness().getId().equals(invitation.getBusiness().getId()))
                .findFirst();
        
        if (existingForBusiness.isPresent()) {
            throw new RuntimeException("Staff member with this email already exists for this business");
        }
        
        // Create staff record
        Staff staff = new Staff();
        staff.setEmail(invitation.getInvitedEmail());
        staff.setFirstName(firstName);
        staff.setLastName(lastName);
        staff.setRole(invitation.getRole());
        staff.setType(invitation.getRole()); // Backward compatibility
        staff.setBusiness(invitation.getBusiness());
        staff.setPassword(passwordEncoder.encode(password));
        staff.setIsActive(true);
        staff.setInvitedBy(invitation.getInvitedBy());
        staff.setCreatedAt(LocalDateTime.now());
        
        Staff savedStaff = staffRepository.save(staff);
        
        // Update invitation status
        invitation.setStatus(StaffInvitation.InvitationStatus.ACCEPTED);
        invitation.setAcceptedAt(LocalDateTime.now());
        invitation.setStaffId(savedStaff.getId());
        staffInvitationRepository.save(invitation);
        
        return mapToDTO(savedStaff);
    }

    @Override
    public StaffDTO login(String email, String password) {
        // TODO: Implement staff login logic
        throw new UnsupportedOperationException("Staff login not yet implemented");
    }

    @Override
    public List<StaffDTO> getStaffByBusiness(Long businessId) {
        List<Staff> staffList = staffRepository.findByBusinessId(businessId);
        return staffList.stream()
                .map(this::mapToDTO)
                .collect(Collectors.toList());
    }

    @Override
    public List<StaffDTO> getStaffByBusinessTag(String businessTag) {
        Optional<Business> businessOpt = businessRepository.findByBusinessTag(businessTag);
        if (businessOpt.isEmpty()) {
            throw new RuntimeException("Business not found with tag: " + businessTag);
        }
        return getStaffByBusiness(businessOpt.get().getId());
    }

    @Override
    @Transactional
    public StaffDTO updateStaffRole(Integer staffId, String role) {
        Staff staff = staffRepository.findById(staffId)
                .orElseThrow(() -> new RuntimeException("Staff not found"));
        
        // Validate role
        if (role == null || (!role.equals("ADMIN") && !role.equals("MANAGER") && !role.equals("TEAM_MEMBER"))) {
            throw new RuntimeException("Invalid role. Must be ADMIN, MANAGER, or TEAM_MEMBER");
        }
        
        staff.setRole(role);
        staff.setType(role); // Backward compatibility
        
        Staff updatedStaff = staffRepository.save(staff);
        return mapToDTO(updatedStaff);
    }

    @Override
    public void activateStaff(Integer staffId) {
        Staff staff = staffRepository.findById(staffId)
                .orElseThrow(() -> new RuntimeException("Staff not found"));
        staff.setIsActive(true);
        staffRepository.save(staff);
    }

    @Override
    public void deactivateStaff(Integer staffId) {
        Staff staff = staffRepository.findById(staffId)
                .orElseThrow(() -> new RuntimeException("Staff not found"));
        staff.setIsActive(false);
        staffRepository.save(staff);
    }

    @Override
    public List<StaffInvitationDTO> getPendingInvitationsByBusiness(Long businessId) {
        List<StaffInvitation> invitations = staffInvitationRepository
                .findByBusinessIdAndStatus(businessId, StaffInvitation.InvitationStatus.PENDING);
        
        return invitations.stream().map(invitation -> {
            StaffInvitationDTO dto = new StaffInvitationDTO();
            dto.setId(invitation.getId());
            dto.setBusinessId(invitation.getBusiness().getId());
            dto.setBusinessName(invitation.getBusiness().getTitle());
            dto.setInvitedEmail(invitation.getInvitedEmail());
            dto.setRole(invitation.getRole());
            dto.setStatus(invitation.getStatus().name());
            dto.setCreatedAt(invitation.getCreatedAt());
            dto.setInviteTokenExpiry(invitation.getInviteTokenExpiry());
            dto.setInvitedBy(invitation.getInvitedBy());
            return dto;
        }).collect(Collectors.toList());
    }
    
    @Override
    @Transactional
    public void resendInvitationEmail(Long invitationId) {
        StaffInvitation invitation = staffInvitationRepository.findById(invitationId)
                .orElseThrow(() -> new RuntimeException("Invitation not found"));
        
        if (invitation.getStatus() != StaffInvitation.InvitationStatus.PENDING) {
            throw new RuntimeException("Can only resend pending invitations");
        }
        
        // Check if token is expired, regenerate if needed
        if (invitation.getInviteTokenExpiry() != null && invitation.getInviteTokenExpiry().isBefore(LocalDateTime.now())) {
            invitation.setInviteToken(generateInviteToken());
            invitation.setInviteTokenExpiry(LocalDateTime.now().plusDays(INVITE_TOKEN_EXPIRY_DAYS));
            staffInvitationRepository.save(invitation);
        }
        
        // Resend email
        try {
            sendInvitationEmail(
                invitation.getInvitedEmail(),
                invitation.getInviteToken(),
                invitation.getBusiness().getTitle(),
                invitation.getRole()
            );
        } catch (MessagingException e) {
            throw new RuntimeException("Failed to resend invitation email: " + e.getMessage(), e);
        }
    }

    private StaffDTO mapToDTO(Staff staff) {
        StaffDTO dto = new StaffDTO();
        dto.setId(staff.getId());
        dto.setFirstName(staff.getFirstName());
        dto.setLastName(staff.getLastName());
        dto.setEmail(staff.getEmail());
        dto.setPassword(staff.getPassword());
        dto.setRole(staff.getRole());
        dto.setType(staff.getType()); // Backward compatibility
        dto.setBusinessId(staff.getBusiness() != null ? staff.getBusiness().getId() : null);
        dto.setClubId(staff.getClub() != null ? staff.getClub().getId() : null); // Backward compatibility
        dto.setInviteToken(staff.getInviteToken());
        dto.setInviteTokenExpiry(staff.getInviteTokenExpiry());
        dto.setIsActive(staff.getIsActive());
        dto.setInvitedBy(staff.getInvitedBy());
        dto.setCreatedAt(staff.getCreatedAt());
        dto.setLastLoginAt(staff.getLastLoginAt());
        return dto;
    }
}
