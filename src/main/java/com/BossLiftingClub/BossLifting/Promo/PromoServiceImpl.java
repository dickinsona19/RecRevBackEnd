package com.BossLiftingClub.BossLifting.Promo;

import com.BossLiftingClub.BossLifting.Business.Business;
import com.BossLiftingClub.BossLifting.Business.BusinessRepository;
import com.BossLiftingClub.BossLifting.Stripe.StripeService;
import com.BossLiftingClub.BossLifting.User.User;
import com.BossLiftingClub.BossLifting.User.UserDTO;
import com.BossLiftingClub.BossLifting.User.UserRepository;
import com.stripe.exception.StripeException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class PromoServiceImpl implements PromoService {

    @Autowired
    private PromoRepository promoRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private BusinessRepository businessRepository;

    @Autowired
    private StripeService stripeService;

    private PromoDTO mapToDTO(Promo promo) {
        return new PromoDTO(
                promo.getId(),
                promo.getName(),
                promo.getCodeToken(),
                promo.getUsers() != null ?
                        promo.getUsers().stream().map(UserDTO::new).collect(Collectors.toList()) :
                        List.of(),
                promo.getFreePassCount(),
                promo.getUrlVisitCount(),
                promo.getBusiness() != null ? promo.getBusiness().getBusinessTag() : null,
                promo.getDiscountValue(),
                promo.getDiscountType(),
                promo.getDuration(),
                promo.getDurationInMonths()
        );
    }

    @Transactional(readOnly = true)
    @Override
    public List<PromoDTO> findAll() {
        return promoRepository.findAllWithUsers().stream()
                .map(this::mapToDTO)
                .toList();
    }

    @Transactional(readOnly = true)
    @Override
    public List<PromoDTO> findAllByBusinessTag(String businessTag) {
        return promoRepository.findAllByBusinessTag(businessTag).stream()
                .map(this::mapToDTO)
                .toList();
    }

    @Override
    public Optional<PromoDTO> findById(Long id) {
        return promoRepository.findByIdWithUsers(id).map(this::mapToDTO);
    }

    @Override
    public Optional<PromoDTO> findByCodeToken(String codeToken) {
        return promoRepository.findByCodeTokenWithUsers(codeToken).map(this::mapToDTO);
    }

    @Override
    public Optional<PromoDTO> incrementUrlVisitCountByCodeToken(String codeToken) {
        return promoRepository.findByCodeTokenWithUsers(codeToken).map(promo -> {
            promo.setUrlVisitCount(promo.getUrlVisitCount() + 1);
            Promo updatedPromo = promoRepository.save(promo);
            return mapToDTO(updatedPromo);
        });
    }

    @Override
    public Promo save(Promo promo) {
        return promoRepository.save(promo);
    }

    @Transactional
    @Override
    public PromoDTO createPromo(PromoCreateDTO createDTO) {
        Business business = businessRepository.findByBusinessTag(createDTO.getBusinessTag())
                .orElseThrow(() -> new RuntimeException("Business not found with tag: " + createDTO.getBusinessTag()));

        // Check if code exists (globally unique for simplicity, or scoped? Promo code in Stripe is unique per account)
        // Assuming unique globally in our DB to avoid confusion
        if (promoRepository.findByCodeToken(createDTO.getCodeToken()).isPresent()) {
             throw new RuntimeException("Promo code already exists: " + createDTO.getCodeToken());
        }

        // Create in Stripe
        String stripeAccountId = business.getStripeAccountId();
        String stripeCouponId = null;
        String stripePromoCodeId = null;

        if (stripeAccountId != null) {
            try {
                Map<String, String> stripeResult = stripeService.createPromoCode(
                        createDTO.getCodeToken(),
                        createDTO.getDiscountType(),
                        createDTO.getDiscountValue(),
                        createDTO.getDuration(),
                        createDTO.getDurationInMonths(),
                        stripeAccountId
                );
                stripeCouponId = stripeResult.get("couponId");
                stripePromoCodeId = stripeResult.get("promoCodeId");
            } catch (StripeException e) {
                throw new RuntimeException("Failed to create promo code in Stripe: " + e.getMessage());
            }
        }

        Promo promo = new Promo();
        promo.setName(createDTO.getName());
        promo.setCodeToken(createDTO.getCodeToken());
        promo.setBusiness(business);
        promo.setDiscountType(createDTO.getDiscountType());
        promo.setDiscountValue(createDTO.getDiscountValue());
        promo.setDuration(createDTO.getDuration());
        promo.setDurationInMonths(createDTO.getDurationInMonths());
        promo.setStripeCouponId(stripeCouponId);
        promo.setStripePromoCodeId(stripePromoCodeId);
        
        return mapToDTO(promoRepository.save(promo));
    }

    @Override
    public void deleteById(Long id) {
        promoRepository.deleteById(id);
    }

    @Override
    public void addUserToPromo(String codeToken, Long userId) {
        Optional<Promo> promoOptional = promoRepository.findByCodeTokenWithUsers(codeToken);
        if (promoOptional.isEmpty()) {
            throw new RuntimeException("Promo not found with codeToken: " + codeToken);
        }
        Promo promo = promoOptional.get();

        Optional<User> userOptional = userRepository.findById(userId);
        if (userOptional.isEmpty()) {
            throw new RuntimeException("User not found with id: " + userId);
        }
        User user = userOptional.get();

        if (promo.getUsers() == null) {
            promo.setUsers(new ArrayList<>());
        }
        // Prevent duplicates
        if (!promo.getUsers().contains(user)) {
            promo.getUsers().add(user);
            save(promo);
        }
    }
}
