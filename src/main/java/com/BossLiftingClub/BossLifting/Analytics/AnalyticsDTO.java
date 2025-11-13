package com.BossLiftingClub.BossLifting.Analytics;

import java.math.BigDecimal;
import java.util.List;

public class AnalyticsDTO {

    // Main analytics response
    public static class DashboardMetrics {
        private BigDecimal totalRevenue;
        private BigDecimal previousPeriodRevenue;
        private Integer activeMembers;
        private Integer previousPeriodActiveMembers;
        private Integer totalMembers;
        private Integer previousPeriodTotalMembers;
        private BigDecimal mrr;
        private BigDecimal previousMrr;
        private BigDecimal revenueGrowthPercent;
        private BigDecimal memberGrowthPercent;
        private BigDecimal churnRate;
        private Integer churnCount;
        private Integer newMembers;
        private BigDecimal totalLifetimeRevenue;
        private BigDecimal averageLtv;
        private Integer failedPaymentsCount;
        private BigDecimal failedPaymentsAmount;
        private Integer refundedPaymentsCount;
        private BigDecimal refundedPaymentsAmount;
        private List<MembershipBreakdown> membershipBreakdown;
        private List<RevenueDataPoint> revenueOverTime;

        // Getters and Setters
        public BigDecimal getTotalRevenue() { return totalRevenue; }
        public void setTotalRevenue(BigDecimal totalRevenue) { this.totalRevenue = totalRevenue; }

        public BigDecimal getPreviousPeriodRevenue() { return previousPeriodRevenue; }
        public void setPreviousPeriodRevenue(BigDecimal previousPeriodRevenue) { this.previousPeriodRevenue = previousPeriodRevenue; }

        public Integer getActiveMembers() { return activeMembers; }
        public void setActiveMembers(Integer activeMembers) { this.activeMembers = activeMembers; }

        public Integer getPreviousPeriodActiveMembers() { return previousPeriodActiveMembers; }
        public void setPreviousPeriodActiveMembers(Integer previousPeriodActiveMembers) { this.previousPeriodActiveMembers = previousPeriodActiveMembers; }

        public Integer getTotalMembers() { return totalMembers; }
        public void setTotalMembers(Integer totalMembers) { this.totalMembers = totalMembers; }

        public Integer getPreviousPeriodTotalMembers() { return previousPeriodTotalMembers; }
        public void setPreviousPeriodTotalMembers(Integer previousPeriodTotalMembers) { this.previousPeriodTotalMembers = previousPeriodTotalMembers; }

        public BigDecimal getMrr() { return mrr; }
        public void setMrr(BigDecimal mrr) { this.mrr = mrr; }

        public BigDecimal getPreviousMrr() { return previousMrr; }
        public void setPreviousMrr(BigDecimal previousMrr) { this.previousMrr = previousMrr; }

        public BigDecimal getRevenueGrowthPercent() { return revenueGrowthPercent; }
        public void setRevenueGrowthPercent(BigDecimal revenueGrowthPercent) { this.revenueGrowthPercent = revenueGrowthPercent; }

        public BigDecimal getMemberGrowthPercent() { return memberGrowthPercent; }
        public void setMemberGrowthPercent(BigDecimal memberGrowthPercent) { this.memberGrowthPercent = memberGrowthPercent; }

        public BigDecimal getChurnRate() { return churnRate; }
        public void setChurnRate(BigDecimal churnRate) { this.churnRate = churnRate; }

        public Integer getChurnCount() { return churnCount; }
        public void setChurnCount(Integer churnCount) { this.churnCount = churnCount; }

        public Integer getNewMembers() { return newMembers; }
        public void setNewMembers(Integer newMembers) { this.newMembers = newMembers; }

        public BigDecimal getTotalLifetimeRevenue() { return totalLifetimeRevenue; }
        public void setTotalLifetimeRevenue(BigDecimal totalLifetimeRevenue) { this.totalLifetimeRevenue = totalLifetimeRevenue; }

        public BigDecimal getAverageLtv() { return averageLtv; }
        public void setAverageLtv(BigDecimal averageLtv) { this.averageLtv = averageLtv; }

        public Integer getFailedPaymentsCount() { return failedPaymentsCount; }
        public void setFailedPaymentsCount(Integer failedPaymentsCount) { this.failedPaymentsCount = failedPaymentsCount; }

        public BigDecimal getFailedPaymentsAmount() { return failedPaymentsAmount; }
        public void setFailedPaymentsAmount(BigDecimal failedPaymentsAmount) { this.failedPaymentsAmount = failedPaymentsAmount; }

        public Integer getRefundedPaymentsCount() { return refundedPaymentsCount; }
        public void setRefundedPaymentsCount(Integer refundedPaymentsCount) { this.refundedPaymentsCount = refundedPaymentsCount; }

        public BigDecimal getRefundedPaymentsAmount() { return refundedPaymentsAmount; }
        public void setRefundedPaymentsAmount(BigDecimal refundedPaymentsAmount) { this.refundedPaymentsAmount = refundedPaymentsAmount; }

        public List<MembershipBreakdown> getMembershipBreakdown() { return membershipBreakdown; }
        public void setMembershipBreakdown(List<MembershipBreakdown> membershipBreakdown) { this.membershipBreakdown = membershipBreakdown; }

        public List<RevenueDataPoint> getRevenueOverTime() { return revenueOverTime; }
        public void setRevenueOverTime(List<RevenueDataPoint> revenueOverTime) { this.revenueOverTime = revenueOverTime; }
    }

    // Membership breakdown by type
    public static class MembershipBreakdown {
        private String membershipName;
        private Long membershipId;
        private Integer memberCount;
        private Integer activeMemberCount;
        private BigDecimal totalRevenue;
        private BigDecimal averageRevenue;
        private BigDecimal monthlyPrice;

        public MembershipBreakdown() {}

        public MembershipBreakdown(String membershipName, Long membershipId, Integer memberCount,
                                  Integer activeMemberCount, BigDecimal totalRevenue,
                                  BigDecimal averageRevenue, BigDecimal monthlyPrice) {
            this.membershipName = membershipName;
            this.membershipId = membershipId;
            this.memberCount = memberCount;
            this.activeMemberCount = activeMemberCount;
            this.totalRevenue = totalRevenue;
            this.averageRevenue = averageRevenue;
            this.monthlyPrice = monthlyPrice;
        }

        // Getters and Setters
        public String getMembershipName() { return membershipName; }
        public void setMembershipName(String membershipName) { this.membershipName = membershipName; }

        public Long getMembershipId() { return membershipId; }
        public void setMembershipId(Long membershipId) { this.membershipId = membershipId; }

        public Integer getMemberCount() { return memberCount; }
        public void setMemberCount(Integer memberCount) { this.memberCount = memberCount; }

        public Integer getActiveMemberCount() { return activeMemberCount; }
        public void setActiveMemberCount(Integer activeMemberCount) { this.activeMemberCount = activeMemberCount; }

        public BigDecimal getTotalRevenue() { return totalRevenue; }
        public void setTotalRevenue(BigDecimal totalRevenue) { this.totalRevenue = totalRevenue; }

        public BigDecimal getAverageRevenue() { return averageRevenue; }
        public void setAverageRevenue(BigDecimal averageRevenue) { this.averageRevenue = averageRevenue; }

        public BigDecimal getMonthlyPrice() { return monthlyPrice; }
        public void setMonthlyPrice(BigDecimal monthlyPrice) { this.monthlyPrice = monthlyPrice; }
    }

    // Revenue data point for charts
    public static class RevenueDataPoint {
        private String date;
        private BigDecimal amount;
        private Integer memberCount;

        public RevenueDataPoint() {}

        public RevenueDataPoint(String date, BigDecimal amount, Integer memberCount) {
            this.date = date;
            this.amount = amount;
            this.memberCount = memberCount;
        }

        public String getDate() { return date; }
        public void setDate(String date) { this.date = date; }

        public BigDecimal getAmount() { return amount; }
        public void setAmount(BigDecimal amount) { this.amount = amount; }

        public Integer getMemberCount() { return memberCount; }
        public void setMemberCount(Integer memberCount) { this.memberCount = memberCount; }
    }

    // Failed/Refunded payment details
    public static class PaymentDetail {
        private String paymentId;
        private Long userId;
        private String userName;
        private String userEmail;
        private BigDecimal amount;
        private String status;
        private String failureReason;
        private Long createdAt;

        public PaymentDetail() {}

        // Getters and Setters
        public String getPaymentId() { return paymentId; }
        public void setPaymentId(String paymentId) { this.paymentId = paymentId; }

        public Long getUserId() { return userId; }
        public void setUserId(Long userId) { this.userId = userId; }

        public String getUserName() { return userName; }
        public void setUserName(String userName) { this.userName = userName; }

        public String getUserEmail() { return userEmail; }
        public void setUserEmail(String userEmail) { this.userEmail = userEmail; }

        public BigDecimal getAmount() { return amount; }
        public void setAmount(BigDecimal amount) { this.amount = amount; }

        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }

        public String getFailureReason() { return failureReason; }
        public void setFailureReason(String failureReason) { this.failureReason = failureReason; }

        public Long getCreatedAt() { return createdAt; }
        public void setCreatedAt(Long createdAt) { this.createdAt = createdAt; }
    }
}
