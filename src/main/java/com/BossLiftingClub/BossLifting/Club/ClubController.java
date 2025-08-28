package com.BossLiftingClub.BossLifting.Club;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/clubs")
public class ClubController {
    @Autowired
    private ClubService clubService;

    @PostMapping
    public ResponseEntity<ClubDTO> createClub(@RequestBody ClubDTO clubDTO) {
        return ResponseEntity.ok(clubService.createClub(clubDTO));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ClubDTO> getClubById(@PathVariable Integer id) {
        return ResponseEntity.ok(clubService.getClubById(id));
    }

    @GetMapping
    public ResponseEntity<List<ClubDTO>> getAllClubs() {
        return ResponseEntity.ok(clubService.getAllClubs());
    }

    @PutMapping("/{id}")
    public ResponseEntity<ClubDTO> updateClub(@PathVariable Integer id, @RequestBody ClubDTO clubDTO) {
        return ResponseEntity.ok(clubService.updateClub(id, clubDTO));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteClub(@PathVariable Integer id) {
        clubService.deleteClub(id);
        return ResponseEntity.ok().build();
    }
}