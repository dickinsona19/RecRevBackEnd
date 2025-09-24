package com.BossLiftingClub.BossLifting.Club;

import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/clubs")
@Validated
public class ClubController {
    @Autowired
    private ClubService clubService;

    @PostMapping
    public ResponseEntity<?> createClub(@Valid @RequestBody ClubDTO clubDTO) {
        try {
            ClubDTO createdClub = clubService.createClub(clubDTO);
            return ResponseEntity.ok(createdClub);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", e.getMessage()));
        } catch (EntityNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", e.getMessage()));
        } catch (DataIntegrityViolationException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", "Club tag '" + clubDTO.getClubTag() + "' already exists"));
        }
    }

//    @GetMapping("/{id}")
//    public ResponseEntity<?> getClubById(@PathVariable Integer id) {
//        try {
//            ClubDTO clubDTO = clubService.getClubById(id);
//            return ResponseEntity.ok(clubDTO);
//        } catch (EntityNotFoundException e) {
//            return ResponseEntity.status(HttpStatus.NOT_FOUND)
//                    .body(Map.of("error", e.getMessage()));
//        }
//    }
//
//    @GetMapping
//    public ResponseEntity<List<ClubDTO>> getAllClubs() {
//        return ResponseEntity.ok(clubService.getAllClubs());
//    }

//    @PutMapping("/{id}")
//    public ResponseEntity<?> updateClub(@PathVariable long id, @Valid @RequestBody ClubDTO clubDTO) {
//        try {
//            ClubDTO updatedClub = clubService.updateClub(id, clubDTO);
//            return ResponseEntity.ok(updatedClub);
//        } catch (IllegalArgumentException e) {
//            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
//                    .body(Map.of("error", e.getMessage()));
//        } catch (EntityNotFoundException e) {
//            return ResponseEntity.status(HttpStatus.NOT_FOUND)
//                    .body(Map.of("error", e.getMessage()));
//        } catch (DataIntegrityViolationException e) {
//            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
//                    .body(Map.of("error", "Club tag '" + clubDTO.getClubTag() + "' already exists"));
//        }
//    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteClub(@PathVariable Integer id) {
        try {
            clubService.deleteClub(id);
            return ResponseEntity.ok().build();
        } catch (EntityNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", e.getMessage()));
        }
    }
}