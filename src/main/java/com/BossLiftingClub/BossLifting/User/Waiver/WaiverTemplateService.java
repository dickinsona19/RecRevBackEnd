package com.BossLiftingClub.BossLifting.User.Waiver;

import com.BossLiftingClub.BossLifting.Business.Business;
import com.BossLiftingClub.BossLifting.Business.BusinessRepository;
import com.BossLiftingClub.BossLifting.User.FirebaseService;
import com.BossLiftingClub.BossLifting.User.User;
import com.BossLiftingClub.BossLifting.User.UserRepository;
import com.BossLiftingClub.BossLifting.User.BusinessUser.UserBusiness;
import com.BossLiftingClub.BossLifting.User.BusinessUser.UserBusinessRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Service
public class WaiverTemplateService {

    private final WaiverTemplateRepository waiverTemplateRepository;
    private final BusinessRepository businessRepository;
    private final FirebaseService firebaseService;
    private final UserBusinessRepository userBusinessRepository;
    private final UserRepository userRepository;

    public WaiverTemplateService(WaiverTemplateRepository waiverTemplateRepository,
                                 BusinessRepository businessRepository,
                                 FirebaseService firebaseService,
                                 UserBusinessRepository userBusinessRepository,
                                 UserRepository userRepository) {
        this.waiverTemplateRepository = waiverTemplateRepository;
        this.businessRepository = businessRepository;
        this.firebaseService = firebaseService;
        this.userBusinessRepository = userBusinessRepository;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public Optional<WaiverTemplateResponse> getActiveTemplate(Long businessId) {
        return waiverTemplateRepository
                .findFirstByBusinessIdAndActiveTrueOrderByVersionDesc(businessId)
                .map(WaiverTemplateResponse::fromEntity);
    }

    @Transactional
    public WaiverTemplateResponse uploadTemplate(Long businessId, MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("A PDF file is required.");
        }

        validatePdf(file);

        Business business = businessRepository.findById(businessId)
                .orElseThrow(() -> new IllegalArgumentException("Business not found."));

        String fileUrl = firebaseService.uploadFile(file, "waivers/" + businessId);

        int nextVersion = waiverTemplateRepository
                .findTopByBusinessIdOrderByVersionDesc(businessId)
                .map(template -> template.getVersion() + 1)
                .orElse(1);

        waiverTemplateRepository.findFirstByBusinessIdAndActiveTrueOrderByVersionDesc(businessId)
                .ifPresent(template -> {
                    template.setActive(false);
                    waiverTemplateRepository.save(template);
                });

        WaiverTemplate template = new WaiverTemplate();
        template.setBusiness(business);
        template.setFileUrl(fileUrl);
        template.setVersion(nextVersion);
        template.setActive(true);

        WaiverTemplate savedTemplate = waiverTemplateRepository.save(template);

        resetWaiverStatusForBusinessMembers(businessId);

        return WaiverTemplateResponse.fromEntity(savedTemplate);
    }

    private void validatePdf(MultipartFile file) {
        String contentType = file.getContentType();
        String fileName = file.getOriginalFilename() != null ? file.getOriginalFilename().toLowerCase() : "";

        boolean isPdfMime = contentType != null && contentType.equalsIgnoreCase("application/pdf");
        boolean isPdfExtension = fileName.endsWith(".pdf");

        if (!isPdfMime && !isPdfExtension) {
            throw new IllegalArgumentException("Only PDF files are supported for waiver templates.");
        }
    }

    private void resetWaiverStatusForBusinessMembers(Long businessId) {
        List<UserBusiness> memberships = userBusinessRepository.findByBusinessId(businessId);
        if (memberships.isEmpty()) {
            return;
        }

        Set<User> users = new HashSet<>();
        for (UserBusiness membership : memberships) {
            if (membership.getUser() != null) {
                membership.getUser().setWaiverStatus(WaiverStatus.NOT_SIGNED);
                users.add(membership.getUser());
            }
        }

        if (!users.isEmpty()) {
            userRepository.saveAll(users);
        }
    }
}

