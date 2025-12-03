package com.BossLiftingClub.BossLifting.Business.Staff;

import com.BossLiftingClub.BossLifting.Security.SecurityUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/staff")
public class StaffController {
    @Autowired
    private StaffService staffService;

    @PostMapping
    public ResponseEntity<StaffDTO> createStaff(@RequestBody StaffDTO staffDTO) {
        return ResponseEntity.ok(staffService.createStaff(staffDTO));
    }

    @GetMapping("/{id}")
    public ResponseEntity<StaffDTO> getStaffById(@PathVariable Integer id) {
        return ResponseEntity.ok(staffService.getStaffById(id));
    }

    @GetMapping
    public ResponseEntity<List<StaffDTO>> getAllStaff() {
        return ResponseEntity.ok(staffService.getAllStaff());
    }

    @PutMapping("/{id}")
    public ResponseEntity<StaffDTO> updateStaff(@PathVariable Integer id, @RequestBody StaffDTO staffDTO) {
        return ResponseEntity.ok(staffService.updateStaff(id, staffDTO));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteStaff(@PathVariable Integer id) {
        // Check permission
        if (!SecurityUtils.hasPermission("staff:delete")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        staffService.deleteStaff(id);
        return ResponseEntity.ok().build();
    }
    
    @PostMapping("/invite")
    public ResponseEntity<?> inviteStaff(@RequestBody Map<String, Object> request) {
        // Check permission
        if (!SecurityUtils.hasPermission("staff:manage")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "You don't have permission to invite staff"));
        }
        
        try {
            String email = (String) request.get("email");
            String role = (String) request.get("role");
            Long businessId = request.get("businessId") instanceof Integer 
                    ? ((Integer) request.get("businessId")).longValue()
                    : (Long) request.get("businessId");
            Integer invitedBy = SecurityUtils.getCurrentClientId();
            
            if (invitedBy == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of("error", "User not authenticated"));
            }
            
            StaffDTO staff = staffService.inviteStaff(email, role, businessId, invitedBy);
            return ResponseEntity.ok(staff);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", e.getMessage()));
        }
    }
    
    @PostMapping("/accept-invite")
    public ResponseEntity<?> acceptInvite(@RequestBody Map<String, String> request) {
        try {
            String inviteToken = request.get("inviteToken");
            String password = request.get("password");
            
            if (inviteToken == null || password == null) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(Map.of("error", "inviteToken and password are required"));
            }
            
            StaffDTO staff = staffService.acceptInvite(inviteToken, password);
            return ResponseEntity.ok(staff);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", e.getMessage()));
        }
    }
    
    @GetMapping("/business/{businessId}")
    public ResponseEntity<?> getStaffByBusiness(@PathVariable Long businessId) {
        // Check permission
        if (!SecurityUtils.hasPermission("staff:view")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "You don't have permission to view staff"));
        }
        
        List<StaffDTO> staff = staffService.getStaffByBusiness(businessId);
        return ResponseEntity.ok(staff);
    }
    
    @GetMapping("/business-tag/{businessTag}")
    public ResponseEntity<?> getStaffByBusinessTag(@PathVariable String businessTag) {
        // Check permission
        if (!SecurityUtils.hasPermission("staff:view")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "You don't have permission to view staff"));
        }
        
        try {
            List<StaffDTO> staff = staffService.getStaffByBusinessTag(businessTag);
            return ResponseEntity.ok(staff);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", e.getMessage()));
        }
    }
    
    @PutMapping("/{id}/role")
    public ResponseEntity<?> updateStaffRole(@PathVariable Integer id, @RequestBody Map<String, String> request) {
        // Check permission
        if (!SecurityUtils.hasPermission("staff:manage")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "You don't have permission to manage staff roles"));
        }
        
        try {
            String role = request.get("role");
            if (role == null) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(Map.of("error", "role is required"));
            }
            
            StaffDTO staff = staffService.updateStaffRole(id, role);
            return ResponseEntity.ok(staff);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", e.getMessage()));
        }
    }
    
    @PutMapping("/{id}/activate")
    public ResponseEntity<?> activateStaff(@PathVariable Integer id) {
        // Check permission
        if (!SecurityUtils.hasPermission("staff:manage")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "You don't have permission to edit staff"));
        }
        
        try {
            staffService.activateStaff(id);
            return ResponseEntity.ok(Map.of("message", "Staff activated successfully"));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", e.getMessage()));
        }
    }
    
    @PutMapping("/{id}/deactivate")
    public ResponseEntity<?> deactivateStaff(@PathVariable Integer id) {
        // Check permission
        if (!SecurityUtils.hasPermission("staff:manage")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "You don't have permission to edit staff"));
        }
        
        try {
            staffService.deactivateStaff(id);
            return ResponseEntity.ok(Map.of("message", "Staff deactivated successfully"));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", e.getMessage()));
        }
    }
}
