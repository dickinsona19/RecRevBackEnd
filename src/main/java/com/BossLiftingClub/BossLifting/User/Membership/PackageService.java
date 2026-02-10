package com.BossLiftingClub.BossLifting.User.Membership;

import java.util.List;

public interface PackageService {
    List<PackageDTO> getPackagesByBusinessTag(String businessTag);
    PackageDTO createPackage(PackageDTO packageDTO);
    PackageDTO updatePackage(Long id, PackageDTO packageDTO);
    void deletePackage(Long id);
    PackageDTO getPackageById(Long id);
}
