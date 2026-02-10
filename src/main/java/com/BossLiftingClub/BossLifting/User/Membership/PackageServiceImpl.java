package com.BossLiftingClub.BossLifting.User.Membership;

import com.BossLiftingClub.BossLifting.Business.Business;
import com.BossLiftingClub.BossLifting.Business.BusinessRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class PackageServiceImpl implements PackageService {
    @Autowired
    private PackageRepository packageRepository;

    @Autowired
    private PackageMembershipRepository packageMembershipRepository;

    @Autowired
    private MembershipRepository membershipRepository;

    @Autowired
    private BusinessRepository businessRepository;

    @Override
    public List<PackageDTO> getPackagesByBusinessTag(String businessTag) {
        List<MembershipPackage> packages = packageRepository.findByBusinessTagAndArchivedFalse(businessTag);
        return packages.stream().map(this::toDTO).collect(Collectors.toList());
    }

    @Override
    @Transactional
    public PackageDTO createPackage(PackageDTO packageDTO) {
        Business business = businessRepository.findByBusinessTag(packageDTO.getBusinessTag())
                .orElseThrow(() -> new RuntimeException("Business not found"));

        MembershipPackage packageEntity = new MembershipPackage();
        packageEntity.setName(packageDTO.getName());
        packageEntity.setBusiness(business);
        packageEntity.setBusinessTag(packageDTO.getBusinessTag());
        packageEntity.setProcessingFee(packageDTO.getProcessingFee());
        
        // Calculate monthly price from memberships (normalize all to monthly)
        BigDecimal totalMonthlyPrice = BigDecimal.ZERO;
        if (packageDTO.getMembershipIds() != null && !packageDTO.getMembershipIds().isEmpty()) {
            for (Long membershipId : packageDTO.getMembershipIds()) {
                Membership membership = membershipRepository.findById(membershipId)
                        .orElseThrow(() -> new RuntimeException("Membership not found: " + membershipId));
                BigDecimal basePrice = new BigDecimal(membership.getPrice());
                String chargeInterval = membership.getChargeInterval();
                
                // Normalize to monthly: annual / 12, bi-weekly * 2, monthly stays the same
                BigDecimal monthlyPrice = basePrice;
                if (chargeInterval != null) {
                    String interval = chargeInterval.toLowerCase();
                    if (interval.equals("annual") || interval.equals("yearly")) {
                        monthlyPrice = basePrice.divide(new BigDecimal("12"), 2, java.math.RoundingMode.HALF_UP);
                    } else if (interval.equals("bi-weekly") || interval.equals("biweekly")) {
                        monthlyPrice = basePrice.multiply(new BigDecimal("2"));
                    }
                }
                totalMonthlyPrice = totalMonthlyPrice.add(monthlyPrice);
            }
        }
        packageEntity.setPrice(totalMonthlyPrice);
        packageEntity.setCreatedAt(LocalDateTime.now());

        MembershipPackage saved = packageRepository.save(packageEntity);

        // Create package memberships
        if (packageDTO.getMembershipIds() != null) {
            for (Long membershipId : packageDTO.getMembershipIds()) {
                Membership membership = membershipRepository.findById(membershipId)
                        .orElseThrow(() -> new RuntimeException("Membership not found: " + membershipId));
                
                PackageMembership pm = new PackageMembership();
                pm.setPackageEntity(saved);
                pm.setMembership(membership);
                packageMembershipRepository.save(pm);
            }
        }

        return toDTO(saved);
    }

    @Override
    @Transactional
    public PackageDTO updatePackage(Long id, PackageDTO packageDTO) {
        MembershipPackage packageEntity = packageRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Package not found"));

        packageEntity.setName(packageDTO.getName());
        packageEntity.setUpdatedAt(LocalDateTime.now());
        packageEntity.setProcessingFee(packageDTO.getProcessingFee());

        // Update memberships
        packageMembershipRepository.deleteByPackageEntityId(id);
        
        // Calculate monthly price from memberships (normalize all to monthly)
        BigDecimal totalMonthlyPrice = BigDecimal.ZERO;
        if (packageDTO.getMembershipIds() != null) {
            for (Long membershipId : packageDTO.getMembershipIds()) {
                Membership membership = membershipRepository.findById(membershipId)
                        .orElseThrow(() -> new RuntimeException("Membership not found: " + membershipId));
                
                PackageMembership pm = new PackageMembership();
                pm.setPackageEntity(packageEntity);
                pm.setMembership(membership);
                packageMembershipRepository.save(pm);
                
                BigDecimal basePrice = new BigDecimal(membership.getPrice());
                String chargeInterval = membership.getChargeInterval();
                
                // Normalize to monthly: annual / 12, bi-weekly * 2, monthly stays the same
                BigDecimal monthlyPrice = basePrice;
                if (chargeInterval != null) {
                    String interval = chargeInterval.toLowerCase();
                    if (interval.equals("annual") || interval.equals("yearly")) {
                        monthlyPrice = basePrice.divide(new BigDecimal("12"), 2, java.math.RoundingMode.HALF_UP);
                    } else if (interval.equals("bi-weekly") || interval.equals("biweekly")) {
                        monthlyPrice = basePrice.multiply(new BigDecimal("2"));
                    }
                }
                totalMonthlyPrice = totalMonthlyPrice.add(monthlyPrice);
            }
        }
        packageEntity.setPrice(totalMonthlyPrice);

        return toDTO(packageRepository.save(packageEntity));
    }

    @Override
    @Transactional
    public void deletePackage(Long id) {
        MembershipPackage packageEntity = packageRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Package not found"));
        packageEntity.setArchived(true);
        packageRepository.save(packageEntity);
    }

    @Override
    public PackageDTO getPackageById(Long id) {
        MembershipPackage packageEntity = packageRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Package not found"));
        return toDTO(packageEntity);
    }

    private PackageDTO toDTO(MembershipPackage packageEntity) {
        PackageDTO dto = new PackageDTO();
        dto.setId(packageEntity.getId());
        dto.setName(packageEntity.getName());
        dto.setBusinessId(packageEntity.getBusiness().getId());
        dto.setBusinessTag(packageEntity.getBusinessTag());
        dto.setPrice(packageEntity.getPrice());
        dto.setProcessingFee(packageEntity.getProcessingFee());
        dto.setStripeProductId(packageEntity.getStripeProductId());
        dto.setArchived(packageEntity.isArchived());
        
        List<PackageMembership> packageMemberships = packageMembershipRepository.findByPackageEntityId(packageEntity.getId());
        List<Long> membershipIds = packageMemberships.stream()
                .map(pm -> pm.getMembership().getId())
                .collect(Collectors.toList());
        dto.setMembershipIds(membershipIds);
        
        return dto;
    }
}
