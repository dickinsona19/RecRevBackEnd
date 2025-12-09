package com.BossLiftingClub.BossLifting.User.Waiver;


import com.BossLiftingClub.BossLifting.User.User;
import com.BossLiftingClub.BossLifting.User.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;

@RestController
@RequestMapping("/api/waiver")
public class WaiverController {


        @Autowired
        private UserRepository userRepository; // Assuming this is your repository interface

        @GetMapping("/status")
        public ResponseEntity<Boolean> checkWaiverStatus(@RequestParam String phoneNumber) {
            Optional<User> userOptional = userRepository.findByPhoneNumber(phoneNumber);
            if (userOptional.isEmpty()) {
                return ResponseEntity.badRequest().body(false); // User not found
            }
            User user = userOptional.get();
            return ResponseEntity.ok(user.getSignatureData() != null);
        }

//        @PostMapping(value = "/sign", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
//        public ResponseEntity<String> signWaiver(
//                @RequestParam String phoneNumber,
//                @RequestParam("signature") MultipartFile signatureFile) {
//            try {
//                Optional<User> userOptional = userRepository.findByPhoneNumber(phoneNumber);
//                if (userOptional.isEmpty()) {
//                    return ResponseEntity.badRequest().body("Member not found");
//                }
//
//                User user = userOptional.get();
//                // Convert MultipartFile to byte[]
//                byte[] signatureBytes = signatureFile.getBytes();
//
//                // Update member
//                user.setSignatureData(signatureBytes);
//                user.setWaiverSignedDate(LocalDateTime.now());
//                userRepository.save(user); // Save via repository, not user object
//
//                return ResponseEntity.ok("Waiver signed successfully");
//            } catch (IOException e) {
//                return ResponseEntity.status(500).body("Failed to save signature: " + e.getMessage());
//            }
//        }

}
