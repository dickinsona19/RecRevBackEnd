package com.BossLiftingClub.BossLifting.User.Membership;

import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/packages")
public class PackageController {
    @Autowired
    private PackageService packageService;

    @GetMapping("/business/{businessTag}")
    public ResponseEntity<?> getPackagesByBusinessTag(@PathVariable String businessTag) {
        try {
            List<PackageDTO> packages = packageService.getPackagesByBusinessTag(businessTag);
            return ResponseEntity.ok(packages);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to retrieve packages: " + e.getMessage()));
        }
    }

    @PostMapping
    public ResponseEntity<?> createPackage(@Valid @RequestBody PackageDTO packageDTO) {
        try {
            PackageDTO created = packageService.createPackage(packageDTO);
            return ResponseEntity.status(HttpStatus.CREATED).body(created);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to create package: " + e.getMessage()));
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> updatePackage(@PathVariable Long id, @Valid @RequestBody PackageDTO packageDTO) {
        try {
            PackageDTO updated = packageService.updatePackage(id, packageDTO);
            return ResponseEntity.ok(updated);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to update package: " + e.getMessage()));
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deletePackage(@PathVariable Long id) {
        try {
            packageService.deletePackage(id);
            return ResponseEntity.ok(Map.of("message", "Package archived successfully"));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to delete package: " + e.getMessage()));
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getPackageById(@PathVariable Long id) {
        try {
            PackageDTO packageDTO = packageService.getPackageById(id);
            return ResponseEntity.ok(packageDTO);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "Package not found: " + e.getMessage()));
        }
    }
}
