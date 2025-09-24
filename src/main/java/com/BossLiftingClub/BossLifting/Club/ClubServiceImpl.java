package com.BossLiftingClub.BossLifting.Club;

import com.BossLiftingClub.BossLifting.Client.Client;
import com.BossLiftingClub.BossLifting.Client.ClientRepository;
import com.BossLiftingClub.BossLifting.Club.Staff.Staff;
import com.BossLiftingClub.BossLifting.Club.Staff.StaffRepository;
import com.BossLiftingClub.BossLifting.User.Membership.Membership;
import com.BossLiftingClub.BossLifting.User.Membership.MembershipDTO;
import jakarta.persistence.EntityNotFoundException;
import jakarta.persistence.LockModeType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class ClubServiceImpl implements ClubService {
    private static final Logger logger = LoggerFactory.getLogger(ClubServiceImpl.class);

    @Autowired
    private ClubRepository clubRepository;

    @Autowired
    private ClientRepository clientRepository;

    @Autowired
    private StaffRepository staffRepository;

    @Override
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public ClubDTO createClub(ClubDTO clubDTO) {
        logger.info("Creating club with clubTag: {}", clubDTO.getClubTag());

        // Validate and generate club_tag if necessary
        String clubTag = clubDTO.getClubTag();
        if (clubTag == null || clubTag.isBlank()) {
            logger.info("No clubTag provided, generating a unique one");
            clubTag = generateUniqueClubTag();
            clubDTO.setClubTag(clubTag);
        } else {
            logger.debug("Checking if clubTag '{}' exists", clubTag);
            if (clubRepository.findByClubTagWithLock(clubTag).isPresent()) {
                logger.warn("Duplicate clubTag detected: {}", clubTag);
                throw new IllegalArgumentException("Club tag '" + clubTag + "' already exists");
            }
        }

        Club club = new Club();
        club.setTitle(clubDTO.getTitle());
        club.setLogoUrl(clubDTO.getLogoUrl());
        club.setStatus(clubDTO.getStatus());
        club.setCreatedAt(LocalDateTime.now());
        club.setClubTag(clubTag);

        if (clubDTO.getClientId() != null) {
            Client client = clientRepository.findById(clubDTO.getClientId())
                    .orElseThrow(() -> {
                        logger.error("Client not found with ID: {}", clubDTO.getClientId());
                        return new EntityNotFoundException("Client not found with ID: " + clubDTO.getClientId());
                    });
            club.setClient(client);
            // Add the club to the client's clubs list to maintain bidirectional relationship
            if (client.getClubs() == null) {
                client.setClubs(new ArrayList<>());
            }
            client.getClubs().add(club);
            logger.info("Associating club with client ID: {}", clubDTO.getClientId());

        }

        if (clubDTO.getStaffId() != null) {
            Staff staff = staffRepository.findById(clubDTO.getStaffId())
                    .orElseThrow(() -> {
                        logger.error("Staff not found with ID: {}", clubDTO.getStaffId());
                        return new EntityNotFoundException("Staff not found with ID: " + clubDTO.getStaffId());
                    });
            club.setStaff(staff);
        }

        logger.info("Saving club with clubTag: {}", clubTag);
        try {
            Club savedClub = clubRepository.save(club);
            logger.info("Club saved successfully with ID: {}", savedClub.getId());
            return mapToDTO(savedClub);
        } catch (Exception e) {
            logger.error("Failed to save club with clubTag: {}. Error: {}", clubTag, e.getMessage());
            throw e;
        }
    }

//    @Override
//    @Transactional(readOnly = true)
//    public ClubDTO getClubById(Integer id) {
//        logger.debug("Fetching club with ID: {}", id);
//        Club club = clubRepository.findByIdWithMemberships(id)
//                .orElseThrow(() -> {
//                    logger.error("Club not found with ID: {}", id);
//                    return new EntityNotFoundException("Club not found with ID: " + id);
//                });
//        return mapToDTO(club);
//    }
//
//    @Override
//    @Transactional(readOnly = true)
//    public List<ClubDTO> getAllClubs() {
//        logger.debug("Fetching all clubs");
//        return clubRepository.findAllWithMemberships().stream()
//                .map(ClubDTO::mapToClubDTO)
//                .collect(Collectors.toList());
//    }

//    @Override
//    @Transactional(isolation = Isolation.SERIALIZABLE)
//    public ClubDTO updateClub(Long id, ClubDTO clubDTO) {
//        logger.info("Updating club with ID: {}", id);
//        Club club = clubRepository.findById(id)
//                .orElseThrow(() -> {
//                    logger.error("Club not found with ID: {}", id);
//                    return new EntityNotFoundException("Club not found with ID: " + id);
//                });
//        // Check if club_tag is being updated and ensure it's unique
//        if (clubDTO.getClubTag() != null && !clubDTO.getClubTag().equals(club.getClubTag()) &&
//                clubRepository.findByClubTagWithLock(clubDTO.getClubTag()).isPresent()) {
//            logger.warn("Duplicate clubTag detected on update: {}", clubDTO.getClubTag());
//            throw new IllegalArgumentException("Club tag '" + clubDTO.getClubTag() + "' already exists");
//        }
//
//        club.setTitle(clubDTO.getTitle());
//        club.setLogoUrl(clubDTO.getLogoUrl());
//        club.setStatus(clubDTO.getStatus());
//        club.setCreatedAt(clubDTO.getCreatedAt() != null ? clubDTO.getCreatedAt() : club.getCreatedAt());
//        club.setClubTag(clubDTO.getClubTag());
//
//
//        if (clubDTO.getStaffId() != null) {
//            Staff staff = staffRepository.findById(clubDTO.getStaffId())
//                    .orElseThrow(() -> {
//                        logger.error("Staff not found with ID: {}", clubDTO.getStaffId());
//                        return new EntityNotFoundException("Staff not found with ID: " + clubDTO.getStaffId());
//                    });
//            club.setStaff(staff);
//        } else {
//            club.setStaff(null);
//        }
//
//        logger.info("Saving updated club with ID: {}", id);
//        try {
//            Club updatedClub = clubRepository.save(club);
//            return mapToDTO(updatedClub);
//        } catch (Exception e) {
//            logger.error("Failed to update club with ID: {}. Error: {}", id, e.getMessage());
//            throw e;
//        }
//    }

    @Override
    @Transactional
    public void deleteClub(long id) {
        logger.info("Deleting club with ID: {}", id);
        Club club = clubRepository.findById(id)
                .orElseThrow(() -> {
                    logger.error("Club not found with ID: {}", id);
                    return new EntityNotFoundException("Club not found with ID: " + id);
                });
        if (club.getClient() != null) {
            // Remove the club from the client's clubs list
            Client client = club.getClient();
            client.getClubs().remove(club);
            clientRepository.save(client);
        }
        clubRepository.deleteById(id);
        logger.info("Club deleted successfully with ID: {}", id);
    }

    private ClubDTO mapToDTO(Club club) {
        ClubDTO dto = new ClubDTO();
        dto.setId(club.getId());
        dto.setTitle(club.getTitle());
        dto.setLogoUrl(club.getLogoUrl());
        dto.setStatus(club.getStatus());
        dto.setCreatedAt(club.getCreatedAt());
        dto.setClubTag(club.getClubTag());
        dto.setClientId(club.getClient() != null ? club.getClient().getId() : null);
        dto.setStaffId(club.getStaff() != null ? club.getStaff().getId() : null);
        return dto;
    }


    private String generateUniqueClubTag() {
        String prefix = "CLUB_";
        String randomId;
        String potentialTag;
        int attempts = 0;
        final int maxAttempts = 50; // Increased to allow more attempts

        do {
            randomId = UUID.randomUUID().toString().substring(0, 8);
            potentialTag = prefix + randomId;
            attempts++;
            logger.debug("Attempt {}: Checking generated clubTag: {}", attempts, potentialTag);
            if (attempts >= maxAttempts) {
                logger.error("Failed to generate unique clubTag after {} attempts", maxAttempts);
                throw new IllegalStateException("Unable to generate a unique club tag after " + maxAttempts + " attempts");
            }
        } while (clubRepository.findByClubTagWithLock(potentialTag).isPresent());

        logger.info("Generated unique clubTag: {}", potentialTag);
        return potentialTag;
    }
}