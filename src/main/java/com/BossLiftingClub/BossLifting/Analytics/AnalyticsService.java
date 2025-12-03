package com.BossLiftingClub.BossLifting.Analytics;

import com.BossLiftingClub.BossLifting.Business.Business;
import com.BossLiftingClub.BossLifting.Business.BusinessRepository;
import com.BossLiftingClub.BossLifting.Stripe.StripeService;
import com.BossLiftingClub.BossLifting.User.BusinessUser.UserBusiness;
import com.BossLiftingClub.BossLifting.User.BusinessUser.UserBusinessMembership;
import com.BossLiftingClub.BossLifting.User.BusinessUser.UserBusinessRepository;
import com.BossLiftingClub.BossLifting.User.Membership.Membership;
import com.BossLiftingClub.BossLifting.User.Membership.MembershipRepository;
import com.stripe.exception.StripeException;
import com.stripe.model.Charge;
import com.stripe.model.ChargeCollection;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class AnalyticsService {

    @Autowired
    private UserBusinessRepository userBusinessRepository;

    @Autowired
    private MembershipRepository membershipRepository;

    @Autowired
    private BusinessRepository businessRepository;

    @Autowired
    private StripeService stripeService;

    /**
     * Get comprehensive analytics for a business
     * @param businessTag The business tag (supports clubTag for backward compatibility)
     * @param startDate Start date for the period (null for all time)
     * @param endDate End date for the period (null for now)
     * @return Dashboard metrics
     */
    public AnalyticsDTO.DashboardMetrics getAnalytics(String businessTag, LocalDateTime startDate, LocalDateTime endDate) throws StripeException {
        Business business = businessRepository.findByBusinessTag(businessTag)
                .orElseThrow(() -> new RuntimeException("Business not found: " + businessTag));

        if (!"COMPLETED".equals(business.getOnboardingStatus())) {
            throw new IllegalStateException("Stripe integration not complete");
        }

        String stripeAccountId = business.getStripeAccountId();

        // Use current time if endDate is null
        if (endDate == null) {
            endDate = LocalDateTime.now();
        }

        AnalyticsDTO.DashboardMetrics metrics = new AnalyticsDTO.DashboardMetrics();

        // Get all members for this business
        List<UserBusiness> allMembers = userBusinessRepository.findAllByBusinessTag(businessTag);

        // Calculate member metrics
        calculateMemberMetrics(metrics, allMembers, startDate, endDate);

        // Calculate revenue metrics from Stripe
        calculateRevenueMetrics(metrics, stripeAccountId, startDate, endDate);

        // Calculate MRR
        calculateMRR(metrics, allMembers);

        // Calculate churn
        calculateChurn(metrics, allMembers, startDate, endDate);

        // Calculate LTV
        calculateLTV(metrics, allMembers, stripeAccountId);

        // Get membership breakdown
        metrics.setMembershipBreakdown(getMembershipBreakdown(businessTag, allMembers, stripeAccountId));

        // Get revenue over time
        metrics.setRevenueOverTime(getRevenueOverTime(stripeAccountId, startDate, endDate));

        return metrics;
    }

    /**
     * Calculate member-related metrics
     */
    private void calculateMemberMetrics(AnalyticsDTO.DashboardMetrics metrics, List<UserBusiness> allMembers,
                                       LocalDateTime startDate, LocalDateTime endDate) {
        // Active members: members with all memberships active OR no memberships
        int activeMembers = (int) allMembers.stream()
                .filter(uc -> {
                    List<UserBusinessMembership> memberships = uc.getUserBusinessMemberships();
                    if (memberships == null || memberships.isEmpty()) {
                        return true; // No memberships = active
                    }
                    // All memberships must be active
                    return memberships.stream().allMatch(m -> "ACTIVE".equalsIgnoreCase(m.getStatus()));
                })
                .count();

        metrics.setActiveMembers(activeMembers);
        metrics.setTotalMembers(allMembers.size());

        // New members in period
        if (startDate != null) {
            int newMembers = (int) allMembers.stream()
                    .filter(uc -> uc.getCreatedAt().isAfter(startDate) && uc.getCreatedAt().isBefore(endDate))
                    .count();
            metrics.setNewMembers(newMembers);

            // Calculate previous period metrics for growth comparison
            long periodDays = java.time.Duration.between(startDate, endDate).toDays();
            LocalDateTime previousPeriodStart = startDate.minusDays(periodDays);
            LocalDateTime previousPeriodEnd = startDate;

            int previousActiveMembers = (int) allMembers.stream()
                    .filter(uc -> uc.getCreatedAt().isBefore(previousPeriodEnd))
                    .filter(uc -> {
                        List<UserBusinessMembership> memberships = uc.getUserBusinessMemberships();
                        if (memberships == null || memberships.isEmpty()) return true;
                        return memberships.stream().allMatch(m -> "ACTIVE".equalsIgnoreCase(m.getStatus()));
                    })
                    .count();

            int previousTotalMembers = (int) allMembers.stream()
                    .filter(uc -> uc.getCreatedAt().isBefore(previousPeriodEnd))
                    .count();

            metrics.setPreviousPeriodActiveMembers(previousActiveMembers);
            metrics.setPreviousPeriodTotalMembers(previousTotalMembers);

            // Calculate growth percentages
            if (previousTotalMembers > 0) {
                BigDecimal growth = BigDecimal.valueOf(allMembers.size() - previousTotalMembers)
                        .multiply(BigDecimal.valueOf(100))
                        .divide(BigDecimal.valueOf(previousTotalMembers), 2, RoundingMode.HALF_UP);
                metrics.setMemberGrowthPercent(growth);
            } else {
                metrics.setMemberGrowthPercent(BigDecimal.ZERO);
            }
        } else {
            metrics.setNewMembers(allMembers.size());
            metrics.setPreviousPeriodActiveMembers(0);
            metrics.setPreviousPeriodTotalMembers(0);
            metrics.setMemberGrowthPercent(BigDecimal.ZERO);
        }
    }

    /**
     * Calculate revenue metrics from Stripe
     */
    private void calculateRevenueMetrics(AnalyticsDTO.DashboardMetrics metrics, String stripeAccountId,
                                        LocalDateTime startDate, LocalDateTime endDate) throws StripeException {
        com.stripe.net.RequestOptions requestOptions = com.stripe.net.RequestOptions.builder()
                .setStripeAccount(stripeAccountId)
                .build();

        Map<String, Object> params = new HashMap<>();
        params.put("limit", 100);

        if (startDate != null) {
            Map<String, Object> createdParams = new HashMap<>();
            createdParams.put("gte", startDate.atZone(ZoneId.systemDefault()).toEpochSecond());
            createdParams.put("lte", endDate.atZone(ZoneId.systemDefault()).toEpochSecond());
            params.put("created", createdParams);
        }

        ChargeCollection charges = Charge.list(params, requestOptions);

        BigDecimal totalRevenue = BigDecimal.ZERO;
        BigDecimal totalLifetimeRevenue = BigDecimal.ZERO;
        int failedCount = 0;
        BigDecimal failedAmount = BigDecimal.ZERO;
        int refundedCount = 0;
        BigDecimal refundedAmount = BigDecimal.ZERO;

        // Process all pages of charges
        for (Charge charge : charges.autoPagingIterable(params, requestOptions)) {
            BigDecimal chargeAmount = BigDecimal.valueOf(charge.getAmount()).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);

            // Total lifetime revenue (all successful charges)
            if (charge.getPaid()) {
                totalLifetimeRevenue = totalLifetimeRevenue.add(chargeAmount);

                // Period revenue (if within date range)
                if (startDate == null || isChargeInPeriod(charge, startDate, endDate)) {
                    totalRevenue = totalRevenue.add(chargeAmount);
                }
            }

            // Failed payments
            if ("failed".equals(charge.getStatus())) {
                failedCount++;
                failedAmount = failedAmount.add(chargeAmount);
            }

            // Refunded payments
            if (charge.getRefunded() || (charge.getAmountRefunded() != null && charge.getAmountRefunded() > 0)) {
                refundedCount++;
                BigDecimal refundAmt = BigDecimal.valueOf(charge.getAmountRefunded()).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
                refundedAmount = refundedAmount.add(refundAmt);
            }
        }

        metrics.setTotalRevenue(totalRevenue);
        metrics.setTotalLifetimeRevenue(totalLifetimeRevenue);
        metrics.setFailedPaymentsCount(failedCount);
        metrics.setFailedPaymentsAmount(failedAmount);
        metrics.setRefundedPaymentsCount(refundedCount);
        metrics.setRefundedPaymentsAmount(refundedAmount);

        // Calculate previous period revenue for growth
        if (startDate != null) {
            long periodDays = java.time.Duration.between(startDate, endDate).toDays();
            LocalDateTime previousStart = startDate.minusDays(periodDays);

            Map<String, Object> prevParams = new HashMap<>();
            prevParams.put("limit", 100);
            Map<String, Object> prevCreated = new HashMap<>();
            prevCreated.put("gte", previousStart.atZone(ZoneId.systemDefault()).toEpochSecond());
            prevCreated.put("lte", startDate.atZone(ZoneId.systemDefault()).toEpochSecond());
            prevParams.put("created", prevCreated);

            ChargeCollection prevCharges = Charge.list(prevParams, requestOptions);
            BigDecimal prevRevenue = BigDecimal.ZERO;

            for (Charge charge : prevCharges.autoPagingIterable(prevParams, requestOptions)) {
                if (charge.getPaid()) {
                    prevRevenue = prevRevenue.add(BigDecimal.valueOf(charge.getAmount()).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP));
                }
            }

            metrics.setPreviousPeriodRevenue(prevRevenue);

            // Calculate revenue growth
            if (prevRevenue.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal growth = totalRevenue.subtract(prevRevenue)
                        .multiply(BigDecimal.valueOf(100))
                        .divide(prevRevenue, 2, RoundingMode.HALF_UP);
                metrics.setRevenueGrowthPercent(growth);
            } else {
                metrics.setRevenueGrowthPercent(BigDecimal.ZERO);
            }
        }
    }

    /**
     * Calculate Monthly Recurring Revenue (MRR)
     */
    private void calculateMRR(AnalyticsDTO.DashboardMetrics metrics, List<UserBusiness> allMembers) {
        BigDecimal mrr = BigDecimal.ZERO;

        for (UserBusiness userBusiness : allMembers) {
            for (UserBusinessMembership membership : userBusiness.getUserBusinessMemberships()) {
                if ("ACTIVE".equalsIgnoreCase(membership.getStatus())) {
                    mrr = mrr.add(resolveActualPrice(membership));
                }
            }
        }

        metrics.setMrr(mrr);
        // For now, previous MRR is the same (would need historical data)
        metrics.setPreviousMrr(mrr);
    }

    /**
     * Calculate churn rate and count
     */
    private void calculateChurn(AnalyticsDTO.DashboardMetrics metrics, List<UserBusiness> allMembers,
                               LocalDateTime startDate, LocalDateTime endDate) {
        if (startDate == null) {
            metrics.setChurnRate(BigDecimal.ZERO);
            metrics.setChurnCount(0);
            return;
        }

        int churnedMembers = (int) allMembers.stream()
                .filter(uc -> {
                    return uc.getUserBusinessMemberships().stream()
                            .anyMatch(m -> {
                                String status = m.getStatus();
                                LocalDateTime endDateMembership = m.getEndDate();
                                return ("CANCELLED".equalsIgnoreCase(status) || "INACTIVE".equalsIgnoreCase(status))
                                        && endDateMembership != null
                                        && endDateMembership.isAfter(startDate)
                                        && endDateMembership.isBefore(endDate);
                            });
                })
                .count();

        metrics.setChurnCount(churnedMembers);

        // Calculate churn rate as percentage
        int totalAtStart = (int) allMembers.stream()
                .filter(uc -> uc.getCreatedAt().isBefore(startDate))
                .count();

        if (totalAtStart > 0) {
            BigDecimal churnRate = BigDecimal.valueOf(churnedMembers)
                    .multiply(BigDecimal.valueOf(100))
                    .divide(BigDecimal.valueOf(totalAtStart), 2, RoundingMode.HALF_UP);
            metrics.setChurnRate(churnRate);
        } else {
            metrics.setChurnRate(BigDecimal.ZERO);
        }
    }

    /**
     * Calculate average Lifetime Value (LTV)
     */
    private void calculateLTV(AnalyticsDTO.DashboardMetrics metrics, List<UserBusiness> allMembers,
                             String stripeAccountId) {
        try {
            // Simple LTV = total lifetime revenue / total members
            BigDecimal totalLifetimeRevenue = metrics.getTotalLifetimeRevenue();
            if (totalLifetimeRevenue != null && allMembers.size() > 0) {
                BigDecimal avgLtv = totalLifetimeRevenue.divide(BigDecimal.valueOf(allMembers.size()), 2, RoundingMode.HALF_UP);
                metrics.setAverageLtv(avgLtv);
            } else {
                metrics.setAverageLtv(BigDecimal.ZERO);
            }
        } catch (Exception e) {
            metrics.setAverageLtv(BigDecimal.ZERO);
        }
    }

    /**
     * Get membership breakdown by type
     */
    private List<AnalyticsDTO.MembershipBreakdown> getMembershipBreakdown(String businessTag, List<UserBusiness> allMembers,
                                                                          String stripeAccountId) {
        List<Membership> allMemberships = membershipRepository.findByBusinessTag(businessTag);
        List<AnalyticsDTO.MembershipBreakdown> breakdown = new ArrayList<>();

        for (Membership membership : allMemberships) {
            int memberCount = 0;
            int activeMemberCount = 0;

            for (UserBusiness userBusiness : allMembers) {
                boolean hasMembership = userBusiness.getUserBusinessMemberships().stream()
                        .anyMatch(m -> m.getMembership().getId().equals(membership.getId()));

                boolean hasActiveMembership = userBusiness.getUserBusinessMemberships().stream()
                        .anyMatch(m -> m.getMembership().getId().equals(membership.getId())
                                && "ACTIVE".equalsIgnoreCase(m.getStatus()));

                if (hasMembership) memberCount++;
                if (hasActiveMembership) activeMemberCount++;
            }

            // Parse price from String to BigDecimal
            BigDecimal price = parsePrice(membership.getPrice());
            BigDecimal totalRevenue = BigDecimal.ZERO;
            for (UserBusiness userBusiness : allMembers) {
                for (UserBusinessMembership userMembership : userBusiness.getUserBusinessMemberships()) {
                    if (userMembership.getMembership().getId().equals(membership.getId())
                            && "ACTIVE".equalsIgnoreCase(userMembership.getStatus())) {
                        totalRevenue = totalRevenue.add(resolveActualPrice(userMembership));
                    }
                }
            }
            BigDecimal avgRevenue = activeMemberCount > 0
                    ? totalRevenue.divide(BigDecimal.valueOf(activeMemberCount), 2, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;

            breakdown.add(new AnalyticsDTO.MembershipBreakdown(
                    membership.getTitle(),
                    membership.getId(),
                    memberCount,
                    activeMemberCount,
                    totalRevenue,
                    avgRevenue,
                    price
            ));
        }

        return breakdown;
    }

    private BigDecimal resolveActualPrice(UserBusinessMembership membership) {
        BigDecimal actual = membership.getActualPrice();
        if (actual != null && actual.compareTo(BigDecimal.ZERO) > 0) {
            return actual.setScale(2, RoundingMode.HALF_UP);
        }
        return parsePrice(membership.getMembership().getPrice());
    }

    private BigDecimal parsePrice(String price) {
        if (price == null || price.isEmpty()) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        String normalized = price.replaceAll("[^0-9.]", "");
        if (normalized.isEmpty()) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        try {
            return new BigDecimal(normalized).setScale(2, RoundingMode.HALF_UP);
        } catch (NumberFormatException e) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
    }

    /**
     * Get revenue over time for charting
     */
    private List<AnalyticsDTO.RevenueDataPoint> getRevenueOverTime(String stripeAccountId,
                                                                   LocalDateTime startDate, LocalDateTime endDate) {
        List<AnalyticsDTO.RevenueDataPoint> dataPoints = new ArrayList<>();

        if (startDate == null) {
            // For "all time", return monthly aggregates for last 12 months
            startDate = LocalDateTime.now().minusMonths(12);
            endDate = LocalDateTime.now();
        }

        try {
            com.stripe.net.RequestOptions requestOptions = com.stripe.net.RequestOptions.builder()
                    .setStripeAccount(stripeAccountId)
                    .build();

            Map<String, Object> params = new HashMap<>();
            params.put("limit", 100);
            Map<String, Object> createdParams = new HashMap<>();
            createdParams.put("gte", startDate.atZone(ZoneId.systemDefault()).toEpochSecond());
            createdParams.put("lte", endDate.atZone(ZoneId.systemDefault()).toEpochSecond());
            params.put("created", createdParams);

            ChargeCollection charges = Charge.list(params, requestOptions);

            // Group by month
            Map<String, BigDecimal> revenueByMonth = new TreeMap<>();

            for (Charge charge : charges.autoPagingIterable(params, requestOptions)) {
                if (charge.getPaid()) {
                    LocalDateTime chargeDate = LocalDateTime.ofEpochSecond(charge.getCreated(), 0, java.time.ZoneOffset.UTC);
                    String monthKey = chargeDate.getYear() + "-" + String.format("%02d", chargeDate.getMonthValue());

                    BigDecimal amount = BigDecimal.valueOf(charge.getAmount()).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
                    revenueByMonth.merge(monthKey, amount, BigDecimal::add);
                }
            }

            // Convert to data points
            for (Map.Entry<String, BigDecimal> entry : revenueByMonth.entrySet()) {
                dataPoints.add(new AnalyticsDTO.RevenueDataPoint(
                        entry.getKey(),
                        entry.getValue(),
                        0 // Member count can be added later if needed
                ));
            }
        } catch (StripeException e) {
            System.err.println("Error fetching revenue over time: " + e.getMessage());
        }

        return dataPoints;
    }

    /**
     * Helper method to check if charge is within period
     */
    private boolean isChargeInPeriod(Charge charge, LocalDateTime startDate, LocalDateTime endDate) {
        LocalDateTime chargeDate = LocalDateTime.ofEpochSecond(charge.getCreated(), 0, java.time.ZoneOffset.UTC);
        return chargeDate.isAfter(startDate) && chargeDate.isBefore(endDate);
    }
}