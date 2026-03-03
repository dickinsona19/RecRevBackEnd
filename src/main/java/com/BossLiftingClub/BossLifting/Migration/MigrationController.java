package com.BossLiftingClub.BossLifting.Migration;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/migration")
public class MigrationController {

    private static final Logger logger = LoggerFactory.getLogger(MigrationController.class);
    private final MigrationService migrationService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public MigrationController(MigrationService migrationService) {
        this.migrationService = migrationService;
    }

    /**
     * Dry run - preview migration without persisting.
     * POST /api/migration/dry-run
     * Body: { "members": [...], "businessTag": "CLT_0001" }
     * Or body: { "members": [...] } with businessTag in query param
     */
    @PostMapping(value = "/dry-run", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> dryRun(@RequestBody Map<String, Object> request) {
        try {
            String businessTag = getBusinessTag(request);
            List<MigrationMemberInput> members = parseMembersFromRequest(request);
            MigrationResult result = migrationService.dryRun(members, businessTag);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            logger.error("Dry run failed: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Import members from JSON body.
     * POST /api/migration/import
     * Body: { "members": [...], "businessTag": "CLT_0001" }
     * Or body can be raw array: [...]
     */
    @PostMapping(value = "/import", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> importMembers(@RequestBody Map<String, Object> request) {
        try {
            String businessTag = getBusinessTag(request);
            List<MigrationMemberInput> members = parseMembersFromRequest(request);
            MigrationResult result = migrationService.executeMigration(members, businessTag, false);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            logger.error("Import failed: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Import from raw JSON array (alternative format).
     * POST /api/migration/import/json
     * Body: [...members...] or single {...}
     */
    @PostMapping(value = "/import/json", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> importFromJson(
            @RequestBody Object body,
            @RequestParam String businessTag) {
        try {
            List<MigrationMemberInput> members = parseMembersFromRaw(body);
            MigrationResult result = migrationService.executeMigration(members, businessTag, false);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            logger.error("Import from JSON failed: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Dry run from raw JSON.
     * POST /api/migration/dry-run/json
     */
    @PostMapping(value = "/dry-run/json", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> dryRunFromJson(
            @RequestBody Object body,
            @RequestParam String businessTag) {
        try {
            List<MigrationMemberInput> members = parseMembersFromRaw(body);
            MigrationResult result = migrationService.dryRun(members, businessTag);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            logger.error("Dry run from JSON failed: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Import from CSV file.
     * POST /api/migration/import/csv
     * Expects CSV with header row. Columns: id,firstName,lastName,password,phoneNumber,isInGoodStanding,userStripeMemberId,membershipName,membershipPrice,chargeInterval,over18,parentId
     */
    @PostMapping(value = "/import/csv", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> importFromCsv(
            @RequestParam("file") MultipartFile file,
            @RequestParam String businessTag) {
        try {
            List<MigrationMemberInput> members = parseCsv(file);
            MigrationResult result = migrationService.executeMigration(members, businessTag, false);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            logger.error("Import from CSV failed: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Dry run from CSV file.
     */
    @PostMapping(value = "/dry-run/csv", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> dryRunFromCsv(
            @RequestParam("file") MultipartFile file,
            @RequestParam String businessTag) {
        try {
            List<MigrationMemberInput> members = parseCsv(file);
            MigrationResult result = migrationService.dryRun(members, businessTag);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            logger.error("Dry run from CSV failed: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    private String getBusinessTag(Map<String, Object> request) {
        Object bt = request.get("businessTag");
        if (bt == null) throw new IllegalArgumentException("businessTag is required");
        return bt.toString();
    }

    @SuppressWarnings("unchecked")
    private List<MigrationMemberInput> parseMembersFromRequest(Map<String, Object> request) {
        Object membersObj = request.get("members");
        if (membersObj == null) {
            throw new IllegalArgumentException("'members' array is required in request body");
        }
        return parseMembersFromRaw(membersObj);
    }

    @SuppressWarnings("unchecked")
    private List<MigrationMemberInput> parseMembersFromRaw(Object raw) {
        if (raw instanceof List) {
            List<?> list = (List<?>) raw;
            List<MigrationMemberInput> result = new ArrayList<>();
            for (Object item : list) {
                MigrationMemberInput m = objectMapper.convertValue(item, MigrationMemberInput.class);
                result.add(m);
            }
            return migrationService.flattenMembers(result);
        } else {
            MigrationMemberInput m = objectMapper.convertValue(raw, MigrationMemberInput.class);
            return migrationService.flattenMembers(List.of(m));
        }
    }

    private List<MigrationMemberInput> parseCsv(MultipartFile file) throws Exception {
        List<MigrationMemberInput> members = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {

            String headerLine = reader.readLine();
            if (headerLine == null) return members;

            String[] headers = parseCsvLine(headerLine);
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) continue;
                String[] values = parseCsvLine(line);
                MigrationMemberInput m = new MigrationMemberInput();
                for (int i = 0; i < headers.length && i < values.length; i++) {
                    String h = headers[i].trim().toLowerCase();
                    String v = values[i].trim();
                    if (v.isEmpty()) continue;
                    switch (h) {
                        case "id" -> m.setId(Long.parseLong(v));
                        case "firstname" -> m.setFirstName(v);
                        case "lastname" -> m.setLastName(v);
                        case "password" -> m.setPassword(v);
                        case "phonenumber" -> m.setPhoneNumber(v);
                        case "isingoodstanding" -> m.setIsInGoodStanding(Boolean.parseBoolean(v));
                        case "userstripememberid" -> m.setUserStripeMemberId(v);
                        case "over18" -> m.setOver18(Boolean.parseBoolean(v));
                        case "parentid" -> m.setParentId(Long.parseLong(v));
                        case "entryqrcodetoken" -> m.setEntryQrcodeToken(v);
                        case "referralcode" -> m.setReferralCode(v);
                        case "lockedinrate" -> m.setLockedInRate(v);
                        case "createdat" -> m.setCreatedAt(v);
                        default -> {
                            if ("membershipname".equals(h) || "membership_name".equals(h)) {
                                if (m.getMembership() == null) m.setMembership(new MigrationMemberInput.MembershipInput());
                                m.getMembership().setName(v);
                            } else if ("membershipprice".equals(h) || "membership_price".equals(h)) {
                                if (m.getMembership() == null) m.setMembership(new MigrationMemberInput.MembershipInput());
                                m.getMembership().setPrice(v);
                            } else if ("chargeinterval".equals(h) || "charge_interval".equals(h)) {
                                if (m.getMembership() == null) m.setMembership(new MigrationMemberInput.MembershipInput());
                                m.getMembership().setChargeInterval(v);
                            }
                        }
                    }
                }
                members.add(m);
            }
        }
        return members;
    }

    private String[] parseCsvLine(String line) {
        List<String> result = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                inQuotes = !inQuotes;
            } else if ((c == ',' && !inQuotes) || c == '\t') {
                result.add(current.toString().replace("\"\"", "\""));
                current = new StringBuilder();
            } else {
                current.append(c);
            }
        }
        result.add(current.toString().replace("\"\"", "\""));
        return result.toArray(new String[0]);
    }
}
