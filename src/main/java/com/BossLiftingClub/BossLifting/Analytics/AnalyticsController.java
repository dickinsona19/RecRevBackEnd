package com.BossLiftingClub.BossLifting.Analytics;

import com.BossLiftingClub.BossLifting.User.User;
import com.BossLiftingClub.BossLifting.User.UserRepository;
import com.stripe.Stripe;
import com.stripe.model.*;
import com.stripe.param.SubscriptionListParams;
import com.stripe.param.InvoiceListParams;
import com.stripe.param.RefundListParams;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;

@RestController
@RequestMapping("/api/analytics")
public class AnalyticsController {

    private static final Logger logger = LoggerFactory.getLogger(AnalyticsController.class);

    private static final Map<String, String> MONTH_MAP = createMonthMap();

    private static Map<String, String> createMonthMap() {
        Map<String, String> map = new HashMap<>();
        map.put("january", "01");
        map.put("february", "02");
        map.put("march", "03");
        map.put("april", "04");
        map.put("may", "05");
        map.put("june", "06");
        map.put("july", "07");
        map.put("august", "08");
        map.put("september", "09");
        map.put("october", "10");
        map.put("november", "11");
        map.put("december", "12");
        map.put("jan", "01");
        map.put("feb", "02");
        map.put("mar", "03");
        map.put("apr", "04");
        map.put("jun", "06");
        map.put("jul", "07");
        map.put("aug", "08");
        map.put("sep", "09");
        map.put("oct", "10");
        map.put("nov", "11");
        map.put("dec", "12");
        return Collections.unmodifiableMap(map);
    }

    @Autowired
    private final UserRepository userRepository;

    @Autowired
    private final AnalyticsCacheRepository analyticsCacheRepository;

    @Autowired
    private final ObjectMapper objectMapper;

    public AnalyticsController(UserRepository userRepository, AnalyticsCacheRepository analyticsCacheRepository, ObjectMapper objectMapper) {
        this.userRepository = userRepository;
        this.analyticsCacheRepository = analyticsCacheRepository;
        this.objectMapper = objectMapper;
    }

    @GetMapping
    @Transactional
    public AnalyticsResponse getAnalytics(
            @RequestParam String userType,
            @RequestParam(required = false) String month,
            @RequestParam(defaultValue = "false") boolean includeMaintenance) {
        try {
            // Validate userType
            if (!userType.equals("all") && !userType.equals("founder") && !userType.equals("monthly") && !userType.equals("annual") && !userType.equals("misc") && !userType.equals("maintenance")) {
                throw new IllegalArgumentException("Invalid userType: " + userType);
            }

            // Standardize month to yyyy-MM format
            String standardizedMonth;
            if (month == null) {
                standardizedMonth = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM"));
            } else if (month.matches("\\d{4}-\\d{2}")) {
                standardizedMonth = month;
            } else {
                String lowerMonth = month.toLowerCase();
                if (MONTH_MAP.containsKey(lowerMonth)) {
                    int year = LocalDate.now().getYear();
                    standardizedMonth = year + "-" + MONTH_MAP.get(lowerMonth);
                } else {
                    throw new IllegalArgumentException("Invalid month format: " + month + ". Use yyyy-MM or full month name.");
                }
            }

            String cacheKey = String.format("%s_%s_%s", userType, standardizedMonth, includeMaintenance);

            // Check cache first
            Optional<AnalyticsCache> cacheOptional = analyticsCacheRepository.findById(cacheKey);
            if (cacheOptional.isPresent()) {
                AnalyticsCache cache = cacheOptional.get();
                if (cache.getLastUpdated() != null && cache.getLastUpdated().isAfter(LocalDateTime.now().minusHours(12))) {
                    logger.info("Serving cached analytics data for key={}", cacheKey);
                    return objectMapper.readValue(cache.getAnalyticsData(), AnalyticsResponse.class);
                }
            }

            // Calculate live data if cache is empty or stale
            AnalyticsResponse response = calculateAnalytics(userType, standardizedMonth, includeMaintenance);

            // Save or update cache
            AnalyticsCache cache = cacheOptional.orElse(new AnalyticsCache());
            cache.setCacheKey(cacheKey);
            cache.setAnalyticsData(objectMapper.writeValueAsString(response));
            cache.setLastUpdated(LocalDateTime.now());
            analyticsCacheRepository.save(cache);

            logger.info("Calculated and cached analytics data for key={}", cacheKey);
            return response;
        } catch (Exception e) {
            logger.error("Unexpected error in getAnalytics for userType={}, month={}, includeMaintenance={}: {}", userType, month, includeMaintenance, e.getMessage(), e);
            throw new RuntimeException("Error fetching analytics data: " + e.getMessage());
        }
    }

    @Scheduled(cron = "0 0 0,12 * * ?") // Run at 00:00 and 12:00 daily
    @Transactional
    public void updateAnalyticsCache() {
        try {
            logger.info("Starting scheduled analytics cache update");
            String[] userTypes = {"all", "founder", "monthly", "annual", "misc", "maintenance"};
            boolean[] includes = {true, false};
            for (String userType : userTypes) {
                for (boolean include : includes) {
                    String cacheKey = String.format("%s_current_%s", userType, include);
                    AnalyticsResponse response = calculateAnalytics(userType, null, include);
                    Optional<AnalyticsCache> cacheOptional = analyticsCacheRepository.findById(cacheKey);
                    AnalyticsCache cache = cacheOptional.orElse(new AnalyticsCache());
                    cache.setCacheKey(cacheKey);
                    cache.setAnalyticsData(objectMapper.writeValueAsString(response));
                    cache.setLastUpdated(LocalDateTime.now());
                    analyticsCacheRepository.save(cache);
                    logger.info("Cached analytics data for key={}", cacheKey);
                }
            }
            logger.info("Completed scheduled analytics cache update");
        } catch (Exception e) {
            logger.error("Error updating analytics cache: {}", e.getMessage(), e);
        }
    }

    private AnalyticsResponse calculateAnalytics(String userType, String month, boolean includeMaintenance) {
        try {
            // Define Price IDs for categorization
            Map<String, String> priceIds = new HashMap<>();
            priceIds.put("founder", "price_1R6aIfGHcVHSTvgIlwN3wmyD");
            priceIds.put("monthly", "price_1RF313GHcVHSTvgI4HXgjwOA");
            priceIds.put("annual", "price_1RJJuTGHcVHSTvgI2pVN6hfx");
            priceIds.put("maintenance", "price_1RF30SGHcVHSTvgIpegCzQ0m");

            String maintenanceId = priceIds.get("maintenance");

            boolean skipMaintenance = !includeMaintenance && !userType.equals("maintenance");

            // Fetch all users from UserRepository
            List<User> users;
            try {
                users = userRepository.findAll();
                logger.info("Fetched {} users from UserRepository", users.size());
            } catch (Exception e) {
                logger.error("Error fetching users: {}", e.getMessage(), e);
                throw new RuntimeException("Failed to fetch users: " + e.getMessage());
            }

            Set<String> ourCustomers = new HashSet<>();
            for (User user : users) {
                if (user.getUserStripeMemberId() != null) {
                    ourCustomers.add(user.getUserStripeMemberId());
                }
            }

            // Fetch all paid invoices
            InvoiceListParams paidInvoicesParams = InvoiceListParams.builder()
                    .setStatus(InvoiceListParams.Status.PAID)
                    .build();
            List<Invoice> allPaidInvoices = new ArrayList<>();
            for (Invoice invoice : Invoice.list(paidInvoicesParams).autoPagingIterable()) {
                if (ourCustomers.contains(invoice.getCustomer())) {
                    allPaidInvoices.add(invoice);
                }
            }

            // Fetch failed invoices (OPEN status)
            InvoiceListParams openInvoicesParams = InvoiceListParams.builder()
                    .setStatus(InvoiceListParams.Status.OPEN)
                    .build();
            List<Invoice> openInvoices = new ArrayList<>();
            for (Invoice invoice : Invoice.list(openInvoicesParams).autoPagingIterable()) {
                if (ourCustomers.contains(invoice.getCustomer())) {
                    openInvoices.add(invoice);
                }
            }

            // Fetch failed invoices (UNCOLLECTIBLE status)
            InvoiceListParams uncollectibleInvoicesParams = InvoiceListParams.builder()
                    .setStatus(InvoiceListParams.Status.UNCOLLECTIBLE)
                    .build();
            List<Invoice> uncollectibleInvoices = new ArrayList<>();
            for (Invoice invoice : Invoice.list(uncollectibleInvoicesParams).autoPagingIterable()) {
                if (ourCustomers.contains(invoice.getCustomer())) {
                    uncollectibleInvoices.add(invoice);
                }
            }

            // Combine failed invoices
            List<Invoice> allFailedInvoices = new ArrayList<>();
            allFailedInvoices.addAll(openInvoices);
            allFailedInvoices.addAll(uncollectibleInvoices);

            // Fetch all refunds
            RefundListParams refundParams = RefundListParams.builder().build();
            List<Refund> allRefunds = new ArrayList<>();
            for (Refund refund : Refund.list(refundParams).autoPagingIterable()) {
                // Get customer ID from the associated Charge
                if (refund.getCharge() != null) {
                    try {
                        Charge charge = Charge.retrieve(refund.getCharge());
                        if (charge.getCustomer() != null && ourCustomers.contains(charge.getCustomer())) {
                            allRefunds.add(refund);
                        }
                    } catch (Exception e) {
                        logger.warn("Error retrieving charge for refund {}: {}", refund.getId(), e.getMessage());
                    }
                }
            }

            // Fetch all subscriptions for each user
            List<Subscription> allSubscriptions = new ArrayList<>();
            for (User user : users) {
                String customerId = user.getUserStripeMemberId();
                if (customerId == null) {
                    logger.warn("Skipping user with ID {}: stripeCustomerId is null", user.getId());
                    continue;
                }

                try {
                    SubscriptionListParams params = SubscriptionListParams.builder()
                            .setCustomer(customerId)
                            .setStatus(SubscriptionListParams.Status.ALL)
                            .build();

                    SubscriptionCollection subscriptions = Subscription.list(params);
                    for (Subscription sub : subscriptions.autoPagingIterable()) {
                        allSubscriptions.add(sub);
                    }
                } catch (Exception e) {
                    logger.error("Error fetching subscriptions for user {}: {}", user.getId(), e.getMessage());
                    continue;
                }
            }

            // Filter relevant subscriptions based on type and maintenance flag
            List<Subscription> filteredSubscriptions = new ArrayList<>();
            for (Subscription sub : allSubscriptions) {
                boolean isRelevant = false;
                for (SubscriptionItem item : sub.getItems().getData()) {
                    String priceId = item.getPrice() != null ? item.getPrice().getId() : null;
                    if (priceId == null) continue;
                    if (skipMaintenance && priceId.equals(maintenanceId)) continue;
                    String subType = getTypeFromPriceId(priceIds, priceId);
                    if (matchesUserType(userType, subType)) {
                        isRelevant = true;
                        break;
                    }
                }
                if (isRelevant) {
                    filteredSubscriptions.add(sub);
                }
            }

            // Determine selected month
            LocalDate nowDate = LocalDate.now();
            LocalDate selectedMonthDate = month != null ? LocalDate.parse(month + "-01") : nowDate.withDayOfMonth(1);
            boolean isCurrentMonth = selectedMonthDate.equals(nowDate.withDayOfMonth(1));
            long currentEpoch = System.currentTimeMillis() / 1000;

            LocalDate startOfSelectedMonth = selectedMonthDate.withDayOfMonth(1);
            LocalDate endOfSelectedMonth = startOfSelectedMonth.plusMonths(1).minusDays(1);
            long startSelectedEpoch = startOfSelectedMonth.atStartOfDay(ZoneId.systemDefault()).toEpochSecond();
            long endSelectedEpoch = endOfSelectedMonth.atTime(23, 59, 59).atZone(ZoneId.systemDefault()).toEpochSecond();

            LocalDate startOfPreviousMonth = startOfSelectedMonth.minusMonths(1);
            LocalDate endOfPreviousMonth = startOfPreviousMonth.plusMonths(1).minusDays(1);
            long startPreviousEpoch = startOfPreviousMonth.atStartOfDay(ZoneId.systemDefault()).toEpochSecond();
            long endPreviousEpoch = endOfPreviousMonth.atTime(23, 59, 59).atZone(ZoneId.systemDefault()).toEpochSecond();

            // Calculate lifetime revenue and customers per type
            Map<String, Double> lifetimeRevenuePerType = new HashMap<>();
            Map<String, Set<String>> customersPerType = new HashMap<>();
            Set<String> allPayingCustomers = new HashSet<>();
            for (Invoice invoice : allPaidInvoices) {
                String customer = invoice.getCustomer();
                boolean isRefunded = allRefunds.stream().anyMatch(refund ->
                        refund.getCharge() != null && refund.getCharge().equals(invoice.getCharge()));
                if (isRefunded) continue;
                for (InvoiceLineItem line : invoice.getLines().getData()) {
                    String priceId = line.getPrice() != null ? line.getPrice().getId() : null;
                    if (priceId == null) continue;
                    if (skipMaintenance && priceId.equals(maintenanceId)) continue;
                    String subType = getTypeFromPriceId(priceIds, priceId);
                    if (!matchesUserType(userType, subType)) continue;
                    double amount = line.getAmount() / 100.0;
                    lifetimeRevenuePerType.put(subType, lifetimeRevenuePerType.getOrDefault(subType, 0.0) + amount);
                    Set<String> customers = customersPerType.computeIfAbsent(subType, k -> new HashSet<>());
                    customers.add(customer);
                    allPayingCustomers.add(customer);
                }
            }

            double totalLifetimeRevenue = lifetimeRevenuePerType.values().stream().mapToDouble(Double::doubleValue).sum();
            double averageLTV = allPayingCustomers.size() > 0 ? totalLifetimeRevenue / allPayingCustomers.size() : 0;

            // Prepare userTypeBreakdown
            Map<String, UserTypeData> userTypeBreakdown = new HashMap<>();
            for (Map.Entry<String, Double> entry : lifetimeRevenuePerType.entrySet()) {
                String type = entry.getKey();
                UserTypeData data = new UserTypeData();
                data.setRevenue(entry.getValue());
                Set<String> customers = customersPerType.getOrDefault(type, new HashSet<>());
                data.setLtv(customers.size() > 0 ? entry.getValue() / customers.size() : 0);
                userTypeBreakdown.put(type, data);
            }

            // Calculate actual revenue for selected month
            double actualRevenueSelected = calculateRevenueForPeriod(allPaidInvoices, startSelectedEpoch, endSelectedEpoch, userType, priceIds, skipMaintenance, maintenanceId, allRefunds);

            // Calculate actual revenue for previous month
            double actualRevenuePrevious = calculateRevenueForPeriod(allPaidInvoices, startPreviousEpoch, endPreviousEpoch, userType, priceIds, skipMaintenance, maintenanceId, allRefunds);

            // Calculate projected revenue (only for current month)
            double projectedRevenue = 0;
            if (isCurrentMonth) {
                projectedRevenue = calculateProjectedRevenue(filteredSubscriptions, currentEpoch, endSelectedEpoch, priceIds, userType, skipMaintenance, maintenanceId);
            }

            // Calculate historical monthly data (last 6 months including selected)
            final int numHistoricalMonths = 6;
            String[] historicalLabels = new String[numHistoricalMonths];
            Double[] historicalActual = new Double[numHistoricalMonths];
            Double[] historicalProjected = new Double[numHistoricalMonths];
            LocalDate histMonth = selectedMonthDate.minusMonths(numHistoricalMonths - 1);
            for (int i = 0; i < numHistoricalMonths; i++) {
                LocalDate mStart = histMonth.withDayOfMonth(1);
                LocalDate mEnd = histMonth.plusMonths(1).minusDays(1);
                long mStartEpoch = mStart.atStartOfDay(ZoneId.systemDefault()).toEpochSecond();
                long mEndEpoch = mEnd.atTime(23, 59, 59).atZone(ZoneId.systemDefault()).toEpochSecond();
                double rev = calculateRevenueForPeriod(allPaidInvoices, mStartEpoch, mEndEpoch, userType, priceIds, skipMaintenance, maintenanceId, allRefunds);
                historicalLabels[i] = histMonth.format(DateTimeFormatter.ofPattern("MMM yyyy"));
                historicalActual[i] = rev;
                historicalProjected[i] = 0.0;
                histMonth = histMonth.plusMonths(1);
            }
            if (isCurrentMonth) {
                historicalProjected[numHistoricalMonths - 1] = projectedRevenue;
            }

            // Calculate failed and refunded payments for selected month
            int failedPaymentCount = 0;
            double failedPaymentAmount = 0.0;
            for (Invoice invoice : allFailedInvoices) {
                if (invoice.getCreated() >= startSelectedEpoch && invoice.getCreated() <= endSelectedEpoch) {
                    for (InvoiceLineItem line : invoice.getLines().getData()) {
                        String priceId = line.getPrice() != null ? line.getPrice().getId() : null;
                        if (priceId == null) continue;
                        if (skipMaintenance && priceId.equals(maintenanceId)) continue;
                        String subType = getTypeFromPriceId(priceIds, priceId);
                        if (matchesUserType(userType, subType)) {
                            failedPaymentCount++;
                            failedPaymentAmount += line.getAmount() / 100.0;
                        }
                    }
                }
            }

            int refundedPaymentCount = 0;
            double refundedPaymentAmount = 0.0;
            for (Refund refund : allRefunds) {
                if (refund.getCreated() >= startSelectedEpoch && refund.getCreated() <= endSelectedEpoch) {
                    String chargeId = refund.getCharge();
                    Invoice invoice = allPaidInvoices.stream()
                            .filter(inv -> chargeId != null && chargeId.equals(inv.getCharge()))
                            .findFirst()
                            .orElse(null);
                    if (invoice != null) {
                        for (InvoiceLineItem line : invoice.getLines().getData()) {
                            String priceId = line.getPrice() != null ? line.getPrice().getId() : null;
                            if (priceId == null) continue;
                            if (skipMaintenance && priceId.equals(maintenanceId)) continue;
                            String subType = getTypeFromPriceId(priceIds, priceId);
                            if (matchesUserType(userType, subType)) {
                                refundedPaymentCount++;
                                refundedPaymentAmount += refund.getAmount() / 100.0;
                            }
                        }
                    }
                }
            }

            // Calculate churn, new subs, active count, MRR for selected month
            int activeAtStart = 0;
            int numberCanceled = 0;
            int newSubscriptions = 0;
            int userCount = 0; // Active at end of month
            double mrr = 0.0;
            for (Subscription sub : filteredSubscriptions) {
                if (sub.getStartDate() <= startSelectedEpoch && (sub.getCanceledAt() == null || sub.getCanceledAt() > startSelectedEpoch)) {
                    activeAtStart++;
                }
                if (sub.getCanceledAt() != null && sub.getCanceledAt() >= startSelectedEpoch && sub.getCanceledAt() <= endSelectedEpoch) {
                    numberCanceled++;
                }
                if (sub.getStartDate() >= startSelectedEpoch && sub.getStartDate() <= endSelectedEpoch) {
                    newSubscriptions++;
                }
                if (sub.getStartDate() <= endSelectedEpoch && (sub.getCanceledAt() == null || sub.getCanceledAt() > endSelectedEpoch)) {
                    userCount++;
                    for (SubscriptionItem item : sub.getItems().getData()) {
                        String priceId = item.getPrice() != null ? item.getPrice().getId() : null;
                        if (priceId == null) continue;
                        if (skipMaintenance && priceId.equals(maintenanceId)) continue;
                        String subType = getTypeFromPriceId(priceIds, priceId);
                        if (!matchesUserType(userType, subType)) continue;
                        long unitAmount = item.getPrice().getUnitAmount();
                        String interval = item.getPrice().getRecurring() != null ? item.getPrice().getRecurring().getInterval() : "month";
                        double monthlyAmount = (unitAmount / 100.0) / (interval.equals("year") ? 12 : 1);
                        mrr += monthlyAmount;
                    }
                }
            }
            double churnRate = activeAtStart > 0 ? (numberCanceled / (double) activeAtStart) * 100 : 0;

            // Set counts in userTypeBreakdown (current active per type)
            Map<String, Integer> activeCountsPerType = new HashMap<>();
            for (Subscription sub : filteredSubscriptions) {
                if (sub.getStartDate() <= endSelectedEpoch && (sub.getCanceledAt() == null || sub.getCanceledAt() > endSelectedEpoch)) {
                    for (SubscriptionItem item : sub.getItems().getData()) {
                        String priceId = item.getPrice() != null ? item.getPrice().getId() : null;
                        if (priceId == null) continue;
                        if (skipMaintenance && priceId.equals(maintenanceId)) continue;
                        String subType = getTypeFromPriceId(priceIds, priceId);
                        if (matchesUserType(userType, subType)) {
                            activeCountsPerType.put(subType, activeCountsPerType.getOrDefault(subType, 0) + 1);
                            break;
                        }
                    }
                }
            }
            for (Map.Entry<String, UserTypeData> entry : userTypeBreakdown.entrySet()) {
                entry.getValue().setCount(activeCountsPerType.getOrDefault(entry.getKey(), 0));
            }

            // Calculate percentage change based on actual
            double percentageChange = actualRevenuePrevious > 0 ?
                    ((actualRevenueSelected - actualRevenuePrevious) / actualRevenuePrevious) * 100 : 0;

            double combinedTotal = actualRevenueSelected + projectedRevenue;

            // Prepare response
            AnalyticsResponse response = new AnalyticsResponse();
            response.setTotalRevenue(actualRevenueSelected);
            response.setUserCount(userCount);
            response.setProjectedRevenue(projectedRevenue);
            response.setMonthlyComparison(new MonthlyComparison(
                    actualRevenueSelected,
                    actualRevenuePrevious,
                    percentageChange,
                    combinedTotal
            ));
            response.setChartData(new ChartData(
                    historicalLabels,
                    historicalActual,
                    historicalProjected
            ));
            response.setUserTypeBreakdown(userTypeBreakdown);
            response.setChurnCount(numberCanceled);
            response.setChurnRate(churnRate);
            response.setNewSubscriptions(newSubscriptions);
            response.setMrr(mrr);
            response.setTotalLifetimeRevenue(totalLifetimeRevenue);
            response.setAverageLTV(averageLTV);
            response.setFailedPaymentCount(failedPaymentCount);
            response.setFailedPaymentAmount(failedPaymentAmount);
            response.setRefundedPaymentCount(refundedPaymentCount);
            response.setRefundedPaymentAmount(refundedPaymentAmount);

            logger.info("Analytics processed successfully for userType={}, month={}, includeMaintenance={}", userType, month, includeMaintenance);

            return response;
        } catch (Exception e) {
            logger.error("Error in calculateAnalytics for userType={}, month={}, includeMaintenance={}: {}", userType, month, includeMaintenance, e.getMessage(), e);
            throw new RuntimeException("Error calculating analytics data: " + e.getMessage());
        }
    }

    private String getTypeFromPriceId(Map<String, String> priceIds, String priceId) {
        for (Map.Entry<String, String> entry : priceIds.entrySet()) {
            if (entry.getValue().equals(priceId)) {
                return entry.getKey();
            }
        }
        return "misc";
    }

    private boolean matchesUserType(String userType, String subType) {
        return userType.equals("all") || userType.equals(subType) || (userType.equals("misc") && subType.equals("misc"));
    }

    private double calculateRevenueForPeriod(List<Invoice> allPaidInvoices, long startEpoch, long endEpoch, String userType, Map<String, String> priceIds, boolean skipMaintenance, String maintenanceId, List<Refund> allRefunds) {
        double revenue = 0;
        for (Invoice invoice : allPaidInvoices) {
            if (invoice.getCreated() < startEpoch || invoice.getCreated() > endEpoch) continue;
            boolean isRefunded = allRefunds.stream().anyMatch(refund ->
                    refund.getCharge() != null && refund.getCharge().equals(invoice.getCharge()));
            if (isRefunded) continue;
            for (InvoiceLineItem line : invoice.getLines().getData()) {
                String priceId = line.getPrice() != null ? line.getPrice().getId() : null;
                if (priceId == null) continue;
                if (skipMaintenance && priceId.equals(maintenanceId)) continue;
                String subType = getTypeFromPriceId(priceIds, priceId);
                if (matchesUserType(userType, subType)) {
                    revenue += line.getAmount() / 100.0;
                }
            }
        }
        return revenue;
    }

    private double calculateProjectedRevenue(List<Subscription> filteredSubscriptions, long currentEpoch, long endEpoch, Map<String, String> priceIds, String userType, boolean skipMaintenance, String maintenanceId) {
        double projected = 0;
        for (Subscription sub : filteredSubscriptions) {
            if (!sub.getStatus().equals("active")) continue;
            for (SubscriptionItem item : sub.getItems().getData()) {
                String priceId = item.getPrice() != null ? item.getPrice().getId() : null;
                if (priceId == null) continue;
                if (skipMaintenance && priceId.equals(maintenanceId)) continue;
                String subType = getTypeFromPriceId(priceIds, priceId);
                if (!matchesUserType(userType, subType)) continue;
                long unitAmount = item.getPrice().getUnitAmount();
                double amount = unitAmount / 100.0;
                if (sub.getCurrentPeriodEnd() > currentEpoch && sub.getCurrentPeriodEnd() <= endEpoch) {
                    projected += amount;
                }
            }
        }
        return projected;
    }

    // DTO classes
    public static class AnalyticsResponse {
        private double totalRevenue;
        private int userCount;
        private double projectedRevenue;
        private MonthlyComparison monthlyComparison;
        private ChartData chartData;
        private Map<String, UserTypeData> userTypeBreakdown;
        private int churnCount;
        private double churnRate;
        private int newSubscriptions;
        private double mrr;
        private double totalLifetimeRevenue;
        private double averageLTV;
        private int failedPaymentCount;
        private double failedPaymentAmount;
        private int refundedPaymentCount;
        private double refundedPaymentAmount;

        public double getTotalRevenue() { return totalRevenue; }
        public void setTotalRevenue(double totalRevenue) { this.totalRevenue = totalRevenue; }
        public int getUserCount() { return userCount; }
        public void setUserCount(int userCount) { this.userCount = userCount; }
        public double getProjectedRevenue() { return projectedRevenue; }
        public void setProjectedRevenue(double projectedRevenue) { this.projectedRevenue = projectedRevenue; }
        public MonthlyComparison getMonthlyComparison() { return monthlyComparison; }
        public void setMonthlyComparison(MonthlyComparison monthlyComparison) { this.monthlyComparison = monthlyComparison; }
        public ChartData getChartData() { return chartData; }
        public void setChartData(ChartData chartData) { this.chartData = chartData; }
        public Map<String, UserTypeData> getUserTypeBreakdown() { return userTypeBreakdown; }
        public void setUserTypeBreakdown(Map<String, UserTypeData> userTypeBreakdown) { this.userTypeBreakdown = userTypeBreakdown; }
        public int getChurnCount() { return churnCount; }
        public void setChurnCount(int churnCount) { this.churnCount = churnCount; }
        public double getChurnRate() { return churnRate; }
        public void setChurnRate(double churnRate) { this.churnRate = churnRate; }
        public int getNewSubscriptions() { return newSubscriptions; }
        public void setNewSubscriptions(int newSubscriptions) { this.newSubscriptions = newSubscriptions; }
        public double getMrr() { return mrr; }
        public void setMrr(double mrr) { this.mrr = mrr; }
        public double getTotalLifetimeRevenue() { return totalLifetimeRevenue; }
        public void setTotalLifetimeRevenue(double totalLifetimeRevenue) { this.totalLifetimeRevenue = totalLifetimeRevenue; }
        public double getAverageLTV() { return averageLTV; }
        public void setAverageLTV(double averageLTV) { this.averageLTV = averageLTV; }
        public int getFailedPaymentCount() { return failedPaymentCount; }
        public void setFailedPaymentCount(int failedPaymentCount) { this.failedPaymentCount = failedPaymentCount; }
        public double getFailedPaymentAmount() { return failedPaymentAmount; }
        public void setFailedPaymentAmount(double failedPaymentAmount) { this.failedPaymentAmount = failedPaymentAmount; }
        public int getRefundedPaymentCount() { return refundedPaymentCount; }
        public void setRefundedPaymentCount(int refundedPaymentCount) { this.refundedPaymentCount = refundedPaymentCount; }
        public double getRefundedPaymentAmount() { return refundedPaymentAmount; }
        public void setRefundedPaymentAmount(double refundedPaymentAmount) { this.refundedPaymentAmount = refundedPaymentAmount; }
    }

    public static class MonthlyComparison {
        private double thisMonth;
        private double lastMonth;
        private double percentageChange;
        private double total;

        public MonthlyComparison(double thisMonth, double lastMonth, double percentageChange, double total) {
            this.thisMonth = thisMonth;
            this.lastMonth = lastMonth;
            this.percentageChange = percentageChange;
            this.total = total;
        }

        public double getThisMonth() { return thisMonth; }
        public double getLastMonth() { return lastMonth; }
        public double getPercentageChange() { return percentageChange; }
        public double getTotal() { return total; }
    }

    public static class ChartData {
        private String[] labels;
        private Double[] thisMonthActual;
        private Double[] thisMonthProjected;

        public ChartData(String[] labels, Double[] thisMonthActual, Double[] thisMonthProjected) {
            this.labels = labels;
            this.thisMonthActual = thisMonthActual;
            this.thisMonthProjected = thisMonthProjected;
        }

        public String[] getLabels() { return labels; }
        public Double[] getThisMonthActual() { return thisMonthActual; }
        public Double[] getThisMonthProjected() { return thisMonthProjected; }
    }

    public static class UserTypeData {
        private int count;
        private double revenue;
        private double ltv;

        public int getCount() { return count; }
        public void setCount(int count) { this.count = count; }
        public double getRevenue() { return revenue; }
        public void setRevenue(double revenue) { this.revenue = revenue; }
        public double getLtv() { return ltv; }
        public void setLtv(double ltv) { this.ltv = ltv; }
    }
}