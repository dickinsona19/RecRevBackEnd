package com.BossLiftingClub.BossLifting.Analytics;

import com.BossLiftingClub.BossLifting.Club.Club;
import com.BossLiftingClub.BossLifting.Club.ClubRepository;
import com.BossLiftingClub.BossLifting.Client.Client;
import com.BossLiftingClub.BossLifting.User.ClubUser.UserClub;
import com.BossLiftingClub.BossLifting.User.ClubUser.UserClubRepository;
import com.BossLiftingClub.BossLifting.User.ClubUser.UserClubMembership;
import com.BossLiftingClub.BossLifting.User.Membership.Membership;
import com.BossLiftingClub.BossLifting.User.User;
import com.BossLiftingClub.BossLifting.User.UserRepository;
import com.stripe.Stripe;
import com.stripe.model.*;
import com.stripe.param.SubscriptionListParams;
import com.stripe.param.InvoiceListParams;
import com.stripe.param.RefundListParams;
import com.stripe.param.ChargeListParams;
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

    @Autowired
    private final ClubRepository clubRepository;

    @Autowired
    private final UserClubRepository userClubRepository;

    @Autowired
    private final RecentActivityRepository recentActivityRepository;

    public AnalyticsController(UserRepository userRepository, AnalyticsCacheRepository analyticsCacheRepository,
                                ObjectMapper objectMapper, ClubRepository clubRepository, UserClubRepository userClubRepository,
                                RecentActivityRepository recentActivityRepository) {
        this.userRepository = userRepository;
        this.analyticsCacheRepository = analyticsCacheRepository;
        this.objectMapper = objectMapper;
        this.clubRepository = clubRepository;
        this.userClubRepository = userClubRepository;
        this.recentActivityRepository = recentActivityRepository;
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

    /**
     * Get comprehensive dashboard metrics for a club
     * GET /api/analytics/dashboard?clubTag={tag}&startDate={start}&endDate={end}
     */
    @GetMapping("/dashboard")
    @Transactional
    public Map<String, Object> getDashboardMetrics(
            @RequestParam String clubTag,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {
        try {
            logger.info("Fetching dashboard metrics for clubTag={}, startDate={}, endDate={}", clubTag, startDate, endDate);

            Club club = clubRepository.findByClubTag(clubTag)
                    .orElseThrow(() -> new RuntimeException("Club not found with tag: " + clubTag));

            Long clubId = club.getId();
            String stripeAccountId = club.getStripeAccountId();

            if (stripeAccountId == null || stripeAccountId.isEmpty()) {
                throw new RuntimeException("Club does not have Stripe configured");
            }

            // Parse date range - handle ISO 8601 format with timezone (Z suffix)
            LocalDateTime start = null;
            LocalDateTime end = LocalDateTime.now();

            try {
                if (startDate != null && !startDate.isEmpty()) {
                    // Parse ISO 8601 date with timezone and convert to LocalDateTime
                    start = java.time.ZonedDateTime.parse(startDate).toLocalDateTime();
                }
                if (endDate != null && !endDate.isEmpty()) {
                    end = java.time.ZonedDateTime.parse(endDate).toLocalDateTime();
                }
            } catch (Exception e) {
                logger.error("Error parsing dates: startDate={}, endDate={}, error={}", startDate, endDate, e.getMessage());
                Map<String, Object> errorResponse = new HashMap<>();
                errorResponse.put("error", "Invalid date format. Please provide dates in ISO 8601 format.");
                return errorResponse;
            }

            // Get all UserClub records for this club
            List<UserClub> userClubs = userClubRepository.findAllByClubTag(clubTag);

            Map<String, Object> metrics = new HashMap<>();

            // Calculate metrics based on requirements
            metrics.put("totalRevenue", calculateTotalRevenue(stripeAccountId, start, end));
            metrics.put("activeMembers", calculateActiveMembers(userClubs));
            metrics.put("totalMembers", userClubs.size());
            metrics.put("mrr", calculateMRR(userClubs));
            metrics.put("memberGrowth", calculateMemberGrowth(userClubs, start, end));
            metrics.put("revenueGrowth", calculateRevenueGrowth(stripeAccountId, start, end));
            metrics.put("churnRate", calculateChurnRate(userClubs, start, end));
            metrics.put("churnCount", calculateChurnCount(userClubs, start, end));
            metrics.put("newMembers", calculateNewMembers(userClubs, start, end));
            metrics.put("totalLifetimeRevenue", calculateLifetimeRevenue(stripeAccountId));
            metrics.put("averageLTV", calculateAverageLTV(stripeAccountId, userClubs.size()));
            metrics.put("failedPayments", getFailedPayments(stripeAccountId, start, end));
            metrics.put("refundedPayments", getRefundedPayments(stripeAccountId, start, end));
            metrics.put("membershipBreakdown", getMembershipBreakdown(userClubs));

            return metrics;
        } catch (RuntimeException e) {
            // If already a RuntimeException with specific message, rethrow
            if (e.getMessage() != null && e.getMessage().contains("Club not found")) {
                throw e;
            }
            logger.error("Error fetching dashboard metrics: {}", e.getMessage(), e);
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("error", "Unable to fetch analytics data. Please try again later.");
            errorResponse.put("details", e.getMessage());
            return errorResponse;
        } catch (Exception e) {
            logger.error("Unexpected error fetching dashboard metrics: {}", e.getMessage(), e);
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("error", "An unexpected error occurred. Please try again later.");
            return errorResponse;
        }
    }

    private double calculateTotalRevenue(String stripeAccountId, LocalDateTime start, LocalDateTime end) throws Exception {
        com.stripe.net.RequestOptions requestOptions = com.stripe.net.RequestOptions.builder()
                .setStripeAccount(stripeAccountId)
                .build();

        ChargeListParams.Builder paramsBuilder = ChargeListParams.builder().setLimit(100L);

        if (start != null) {
            paramsBuilder.setCreated(ChargeListParams.Created.builder()
                    .setGte(start.atZone(ZoneId.systemDefault()).toEpochSecond())
                    .setLte(end.atZone(ZoneId.systemDefault()).toEpochSecond())
                    .build());
        }

        ChargeCollection charges = Charge.list(paramsBuilder.build(), requestOptions);
        double totalRevenue = 0.0;

        for (Charge charge : charges.autoPagingIterable()) {
            if (charge.getPaid() && !charge.getRefunded()) {
                totalRevenue += charge.getAmount() / 100.0;
            }
        }

        return totalRevenue;
    }

    private int calculateActiveMembers(List<UserClub> userClubs) {
        int active = 0;
        for (UserClub userClub : userClubs) {
            List<UserClubMembership> memberships = userClub.getUserClubMemberships();
            if (memberships == null || memberships.isEmpty()) {
                active++; // No memberships = active
            } else {
                boolean allActive = memberships.stream()
                        .allMatch(m -> "ACTIVE".equalsIgnoreCase(m.getStatus()));
                if (allActive) active++;
            }
        }
        return active;
    }

    private double calculateMRR(List<UserClub> userClubs) {
        double mrr = 0.0;
        for (UserClub userClub : userClubs) {
            for (UserClubMembership membership : userClub.getUserClubMemberships()) {
                if ("ACTIVE".equalsIgnoreCase(membership.getStatus())) {
                    Membership membershipType = membership.getMembership();
                    if (membershipType != null && membershipType.getPrice() != null) {
                        try {
                            mrr += Double.parseDouble(membershipType.getPrice());
                        } catch (NumberFormatException e) {
                            logger.warn("Could not parse price '{}' for membership id {}",
                                    membershipType.getPrice(), membershipType.getId());
                        }
                    }
                }
            }
        }
        return mrr;
    }

    private Map<String, Object> calculateMemberGrowth(List<UserClub> userClubs, LocalDateTime start, LocalDateTime end) {
        Map<String, Object> growth = new HashMap<>();

        if (start == null) {
            growth.put("percentChange", 0.0);
            growth.put("absoluteChange", 0);
            return growth;
        }

        int membersNow = userClubs.size();
        int membersBefore = (int) userClubs.stream()
                .filter(uc -> uc.getCreatedAt().isBefore(start))
                .count();

        int absoluteChange = membersNow - membersBefore;
        double percentChange = membersBefore > 0 ? ((double) absoluteChange / membersBefore) * 100 : 0.0;

        growth.put("percentChange", percentChange);
        growth.put("absoluteChange", absoluteChange);
        return growth;
    }

    private Map<String, Object> calculateRevenueGrowth(String stripeAccountId, LocalDateTime start, LocalDateTime end) throws Exception {
        Map<String, Object> growth = new HashMap<>();

        if (start == null) {
            growth.put("percentChange", 0.0);
            return growth;
        }

        long periodDays = java.time.Duration.between(start, end).toDays();
        LocalDateTime previousStart = start.minusDays(periodDays);

        double currentRevenue = calculateTotalRevenue(stripeAccountId, start, end);
        double previousRevenue = calculateTotalRevenue(stripeAccountId, previousStart, start);

        double percentChange = previousRevenue > 0 ? ((currentRevenue - previousRevenue) / previousRevenue) * 100 : 0.0;

        growth.put("percentChange", percentChange);
        growth.put("currentPeriod", currentRevenue);
        growth.put("previousPeriod", previousRevenue);
        return growth;
    }

    private double calculateChurnRate(List<UserClub> userClubs, LocalDateTime start, LocalDateTime end) {
        if (start == null) return 0.0;

        int churned = calculateChurnCount(userClubs, start, end);
        int totalAtStart = (int) userClubs.stream()
                .filter(uc -> uc.getCreatedAt().isBefore(start))
                .count();

        return totalAtStart > 0 ? ((double) churned / totalAtStart) * 100 : 0.0;
    }

    private int calculateChurnCount(List<UserClub> userClubs, LocalDateTime start, LocalDateTime end) {
        if (start == null || end == null) return 0;

        return (int) userClubs.stream()
                .filter(uc -> uc.getUserClubMemberships().stream()
                        .anyMatch(m -> {
                            LocalDateTime endDate = m.getEndDate();
                            return ("CANCELLED".equalsIgnoreCase(m.getStatus()) || "INACTIVE".equalsIgnoreCase(m.getStatus()))
                                    && endDate != null
                                    && endDate.isAfter(start)
                                    && endDate.isBefore(end);
                        }))
                .count();
    }

    private int calculateNewMembers(List<UserClub> userClubs, LocalDateTime start, LocalDateTime end) {
        if (start == null) return userClubs.size();

        return (int) userClubs.stream()
                .filter(uc -> uc.getCreatedAt().isAfter(start) && uc.getCreatedAt().isBefore(end))
                .count();
    }

    private double calculateLifetimeRevenue(String stripeAccountId) throws Exception {
        return calculateTotalRevenue(stripeAccountId, null, LocalDateTime.now());
    }

    private double calculateAverageLTV(String stripeAccountId, int totalMembers) throws Exception {
        if (totalMembers == 0) return 0.0;
        return calculateLifetimeRevenue(stripeAccountId) / totalMembers;
    }

    private Map<String, Object> getFailedPayments(String stripeAccountId, LocalDateTime start, LocalDateTime end) throws Exception {
        Map<String, Object> failed = new HashMap<>();
        int count = 0;
        double amount = 0.0;

        // This is simplified - you'd need to query Stripe for failed charges/invoices
        failed.put("count", count);
        failed.put("amount", amount);
        return failed;
    }

    private Map<String, Object> getRefundedPayments(String stripeAccountId, LocalDateTime start, LocalDateTime end) throws Exception {
        Map<String, Object> refunded = new HashMap<>();
        int count = 0;
        double amount = 0.0;

        // Query refunds from Stripe
        com.stripe.net.RequestOptions requestOptions = com.stripe.net.RequestOptions.builder()
                .setStripeAccount(stripeAccountId)
                .build();

        RefundListParams.Builder paramsBuilder = RefundListParams.builder();
        if (start != null) {
            paramsBuilder.setCreated(RefundListParams.Created.builder()
                    .setGte(start.atZone(ZoneId.systemDefault()).toEpochSecond())
                    .setLte(end.atZone(ZoneId.systemDefault()).toEpochSecond())
                    .build());
        }

        RefundCollection refunds = Refund.list(paramsBuilder.build(), requestOptions);
        for (Refund refund : refunds.autoPagingIterable()) {
            count++;
            amount += refund.getAmount() / 100.0;
        }

        refunded.put("count", count);
        refunded.put("amount", amount);
        return refunded;
    }

    private List<Map<String, Object>> getMembershipBreakdown(List<UserClub> userClubs) {
        // Group members by membership type
        Map<String, Map<String, Object>> breakdownMap = new HashMap<>();

        for (UserClub userClub : userClubs) {
            for (UserClubMembership ucm : userClub.getUserClubMemberships()) {
                Membership membership = ucm.getMembership();
                String membershipName = membership.getTitle();

                breakdownMap.putIfAbsent(membershipName, new HashMap<>());
                Map<String, Object> data = breakdownMap.get(membershipName);

                data.put("membershipName", membershipName);
                data.put("price", membership.getPrice());
                data.putIfAbsent("totalCount", 0);
                data.putIfAbsent("activeCount", 0);

                data.put("totalCount", (int) data.get("totalCount") + 1);
                if ("ACTIVE".equalsIgnoreCase(ucm.getStatus())) {
                    data.put("activeCount", (int) data.get("activeCount") + 1);
                }
            }
        }

        return new ArrayList<>(breakdownMap.values());
    }

    /**
     * Get club-specific overview analytics
     * GET /api/analytics/club-overview?clubId={id}
     */
    @GetMapping("/club-overview")
    @Transactional
    public ClubOverviewResponse getClubOverview(@RequestParam Long clubId) {
        try {
            logger.info("Fetching club overview analytics for clubId={}", clubId);

            // Get the club
            Club club = clubRepository.findById(clubId)
                    .orElseThrow(() -> new RuntimeException("Club not found with id: " + clubId));

            // Get all UserClub records for this club
            List<UserClub> userClubs = userClubRepository.findByClubId(clubId);
            logger.info("Found {} UserClub records for clubId={}", userClubs.size(), clubId);

            // Get Stripe Account ID from club
            String stripeAccountId = club.getStripeAccountId();

            // Calculate Total Active Members (users with at least one ACTIVE membership)
            int totalActiveMembers = 0;
            for (UserClub userClub : userClubs) {
                boolean hasActiveMembership = false;
                for (UserClubMembership membership : userClub.getUserClubMemberships()) {
                    if ("ACTIVE".equalsIgnoreCase(membership.getStatus())) {
                        hasActiveMembership = true;
                        break;
                    }
                }
                if (hasActiveMembership) {
                    totalActiveMembers++;
                }
            }

            // Calculate New Members (UserClubs created in last 30 days)
            LocalDateTime thirtyDaysAgo = LocalDateTime.now().minusDays(30);
            int newMembers = 0;
            for (UserClub userClub : userClubs) {
                if (userClub.getCreatedAt() != null && userClub.getCreatedAt().isAfter(thirtyDaysAgo)) {
                    newMembers++;
                }
            }

            // Calculate MRR from active memberships
            double mrr = 0.0;
            for (UserClub userClub : userClubs) {
                for (UserClubMembership membership : userClub.getUserClubMemberships()) {
                    if ("ACTIVE".equalsIgnoreCase(membership.getStatus())) {
                        Membership membershipType = membership.getMembership();
                        if (membershipType != null && membershipType.getPrice() != null) {
                            try {
                                // Parse price string to double
                                double price = Double.parseDouble(membershipType.getPrice());

                                // Convert to monthly if it's annual
                                String chargeInterval = membershipType.getChargeInterval();
                                if ("yearly".equalsIgnoreCase(chargeInterval) || "annual".equalsIgnoreCase(chargeInterval)) {
                                    price = price / 12.0;
                                }

                                mrr += price;
                            } catch (NumberFormatException e) {
                                logger.warn("Could not parse price '{}' for membership id {}", membershipType.getPrice(), membershipType.getId());
                            }
                        }
                    }
                }
            }

            // Calculate Total Revenue from Stripe - Pull ALL charges from the connected account
            double totalRevenue = 0.0;

            logger.info("Fetching revenue from Stripe Account ID: {} for clubId={}", stripeAccountId, clubId);

            // Fetch ALL charges directly from the connected account
            if (stripeAccountId == null || stripeAccountId.isEmpty()) {
                logger.error("Cannot fetch revenue from Stripe: Stripe Account ID is null or empty for clubId={}. " +
                    "Please ensure the club's client has a valid stripe_account_id configured.", clubId);
            } else {
                try {
                    com.stripe.net.RequestOptions requestOptions = com.stripe.net.RequestOptions.builder()
                            .setStripeAccount(stripeAccountId)
                            .build();

                    int totalCharges = 0;
                    logger.info("Fetching ALL charges from Stripe account: {}", stripeAccountId);

                    // Fetch ALL charges from this connected account (no customer filter)
                    ChargeListParams params = ChargeListParams.builder()
                            .setLimit(100L)
                            .build();

                    ChargeCollection charges = Charge.list(params, requestOptions);

                    for (Charge charge : charges.autoPagingIterable()) {
                        // Only count successful charges that haven't been refunded
                        if (charge.getPaid() && !charge.getRefunded()) {
                            double amount = charge.getAmount() / 100.0;
                            totalRevenue += amount;
                            totalCharges++;
                            logger.debug("Added charge ${} (ID: {})", amount, charge.getId());
                        }
                    }
                    logger.info("Total revenue from Stripe: ${} from {} charges for clubId={}",
                        totalRevenue, totalCharges, clubId);
                } catch (Exception e) {
                    logger.error("Error fetching Stripe data for clubId={}: {}", clubId, e.getMessage(), e);
                }
            }

            ClubOverviewResponse response = new ClubOverviewResponse();
            response.setTotalRevenue(totalRevenue);
            response.setMrr(mrr);
            response.setTotalActiveMembers(totalActiveMembers);
            response.setNewMembers(newMembers);

            logger.info("Club overview calculated: totalRevenue={}, mrr={}, activeMembers={}, newMembers={}",
                    totalRevenue, mrr, totalActiveMembers, newMembers);

            return response;
        } catch (Exception e) {
            logger.error("Error fetching club overview for clubId={}: {}", clubId, e.getMessage(), e);
            throw new RuntimeException("Error fetching club overview: " + e.getMessage());
        }
    }

    public static class ClubOverviewResponse {
        private double totalRevenue;
        private double mrr;
        private int totalActiveMembers;
        private int newMembers;

        public double getTotalRevenue() { return totalRevenue; }
        public void setTotalRevenue(double totalRevenue) { this.totalRevenue = totalRevenue; }
        public double getMrr() { return mrr; }
        public void setMrr(double mrr) { this.mrr = mrr; }
        public int getTotalActiveMembers() { return totalActiveMembers; }
        public void setTotalActiveMembers(int totalActiveMembers) { this.totalActiveMembers = totalActiveMembers; }
        public int getNewMembers() { return newMembers; }
        public void setNewMembers(int newMembers) { this.newMembers = newMembers; }
    }

    /**
     * Get revenue chart data from Stripe for a specific club
     * GET /api/analytics/revenue-chart?clubId={id}&period={period}
     *
     * @param clubId The club ID
     * @param period Time period: "today", "7d", "30d", "90d", "1y", or "all"
     */
    @GetMapping("/revenue-chart")
    @Transactional
    public RevenueChartResponse getRevenueChart(
            @RequestParam Long clubId,
            @RequestParam(defaultValue = "30d") String period) {
        try {
            logger.info("Fetching revenue chart data for clubId={}, period={}", clubId, period);

            // Get the club
            Club club = clubRepository.findById(clubId)
                    .orElseThrow(() -> new RuntimeException("Club not found with id: " + clubId));

            // Get Stripe Account ID from club
            String stripeAccountId = club.getStripeAccountId();

            if (stripeAccountId == null || stripeAccountId.isEmpty()) {
                logger.error("No Stripe account ID found for clubId={}", clubId);
                throw new RuntimeException("Club does not have a Stripe account configured");
            }

            logger.info("Using Stripe account ID: {} for clubId={}", stripeAccountId, clubId);

            // Calculate date range based on period
            LocalDateTime endDate = LocalDateTime.now();
            LocalDateTime startDate;

            switch (period.toLowerCase()) {
                case "today":
                    startDate = LocalDate.now().atStartOfDay();
                    break;
                case "7d":
                    startDate = endDate.minusDays(7);
                    break;
                case "30d":
                    startDate = endDate.minusDays(30);
                    break;
                case "90d":
                    startDate = endDate.minusDays(90);
                    break;
                case "1y":
                    startDate = endDate.minusYears(1);
                    break;
                case "all":
                    startDate = LocalDateTime.of(2000, 1, 1, 0, 0); // Far back enough for "all time"
                    break;
                default:
                    startDate = endDate.minusDays(30); // Default to 30 days
            }

            long startEpoch = startDate.atZone(ZoneId.systemDefault()).toEpochSecond();

            // Map to store revenue by date (date string -> revenue amount)
            Map<String, Double> revenueByDate = new TreeMap<>(); // TreeMap to keep dates sorted
            int totalChargesProcessed = 0;

            // Fetch ALL charges directly from the connected account
            try {
                com.stripe.net.RequestOptions requestOptions = com.stripe.net.RequestOptions.builder()
                        .setStripeAccount(stripeAccountId)
                        .build();

                logger.info("Fetching ALL charges from Stripe account {} from {} to {}",
                    stripeAccountId, startDate.toLocalDate(), endDate.toLocalDate());

                // Fetch all charges from this connected account (no customer filter)
                ChargeListParams params = ChargeListParams.builder()
                        .setCreated(ChargeListParams.Created.builder()
                                .setGte(startEpoch)
                                .build())
                        .setLimit(100L)
                        .build();

                logger.debug("Fetching charges for Stripe account: {}", stripeAccountId);
                ChargeCollection charges = Charge.list(params, requestOptions);

                for (Charge charge : charges.autoPagingIterable()) {
                    // Only count successful charges that haven't been refunded
                    if (charge.getPaid() && !charge.getRefunded()) {
                        LocalDateTime chargeDateTime = LocalDateTime
                                .ofEpochSecond(charge.getCreated(), 0, java.time.ZoneOffset.UTC);

                        String dateKey;
                        // For "today", group by hour instead of day
                        if ("today".equalsIgnoreCase(period)) {
                            // Format as "HH:00" for hourly grouping
                            dateKey = String.format("%02d:00", chargeDateTime.getHour());
                        } else {
                            // Format as YYYY-MM-DD for daily grouping
                            dateKey = chargeDateTime.toLocalDate().toString();
                        }

                        double amount = charge.getAmount() / 100.0; // Convert cents to dollars
                        revenueByDate.put(dateKey, revenueByDate.getOrDefault(dateKey, 0.0) + amount);

                        totalChargesProcessed++;

                        logger.debug("Added charge: ${} on {} (ID: {})", amount, dateKey, charge.getId());
                    } else {
                        logger.debug("Skipping charge {} - Paid: {}, Refunded: {}",
                            charge.getId(), charge.getPaid(), charge.getRefunded());
                    }
                }

                logger.info("Total charges processed: {}, Total unique dates: {}",
                    totalChargesProcessed, revenueByDate.size());
            } catch (Exception e) {
                logger.error("Error fetching Stripe data for clubId={}: {}", clubId, e.getMessage(), e);
                throw new RuntimeException("Error fetching Stripe data: " + e.getMessage());
            }

            // For "today", ensure we have all 24 hours (0:00 to 23:00) with zeros for missing hours
            if ("today".equalsIgnoreCase(period)) {
                Map<String, Double> completeHourlyData = new TreeMap<>();
                for (int hour = 0; hour < 24; hour++) {
                    String hourKey = String.format("%02d:00", hour);
                    completeHourlyData.put(hourKey, revenueByDate.getOrDefault(hourKey, 0.0));
                }
                revenueByDate = completeHourlyData;
            }

            // Convert map to arrays for response
            List<String> labels = new ArrayList<>(revenueByDate.keySet());
            List<Double> values = new ArrayList<>(revenueByDate.values());

            RevenueChartResponse response = new RevenueChartResponse();
            response.setLabels(labels.toArray(new String[0]));
            response.setValues(values.stream().mapToDouble(Double::doubleValue).toArray());
            response.setPeriod(period);
            response.setStartDate(startDate.toString());
            response.setEndDate(endDate.toString());

            logger.info("Revenue chart data calculated: {} data points from {} to {}",
                    labels.size(), startDate.toLocalDate(), endDate.toLocalDate());

            return response;
        } catch (Exception e) {
            logger.error("Error fetching revenue chart for clubId={}, period={}: {}", clubId, period, e.getMessage(), e);
            throw new RuntimeException("Error fetching revenue chart: " + e.getMessage());
        }
    }

    public static class RevenueChartResponse {
        private String[] labels;
        private double[] values;
        private String period;
        private String startDate;
        private String endDate;

        public String[] getLabels() { return labels; }
        public void setLabels(String[] labels) { this.labels = labels; }
        public double[] getValues() { return values; }
        public void setValues(double[] values) { this.values = values; }
        public String getPeriod() { return period; }
        public void setPeriod(String period) { this.period = period; }
        public String getStartDate() { return startDate; }
        public void setStartDate(String startDate) { this.startDate = startDate; }
        public String getEndDate() { return endDate; }
        public void setEndDate(String endDate) { this.endDate = endDate; }
    }

    /**
     * Get available balance for payouts from Stripe
     * GET /api/analytics/balance?clubId={id}
     */
    @GetMapping("/balance")
    @Transactional
    public BalanceResponse getBalance(@RequestParam Long clubId) {
        try {
            logger.info("Fetching balance for clubId={}", clubId);

            // Get the club
            Club club = clubRepository.findById(clubId)
                    .orElseThrow(() -> new RuntimeException("Club not found with id: " + clubId));

            // Get Stripe Account ID from club
            String stripeAccountId = club.getStripeAccountId();

            if (stripeAccountId == null || stripeAccountId.isEmpty()) {
                logger.error("No Stripe account ID found for clubId={}", clubId);
                throw new RuntimeException("Club does not have a Stripe account configured");
            }

            // Fetch balance from Stripe
            com.stripe.net.RequestOptions requestOptions = com.stripe.net.RequestOptions.builder()
                    .setStripeAccount(stripeAccountId)
                    .build();

            com.stripe.model.Balance balance = com.stripe.model.Balance.retrieve(requestOptions);

            // Calculate total available balance across all currencies
            double totalAvailable = 0.0;
            double totalPending = 0.0;
            String currency = "usd"; // Default

            for (int i = 0; i < balance.getAvailable().size(); i++) {
                var money = balance.getAvailable().get(i);
                totalAvailable += money.getAmount() / 100.0;
                currency = money.getCurrency();
            }

            for (int i = 0; i < balance.getPending().size(); i++) {
                var money = balance.getPending().get(i);
                totalPending += money.getAmount() / 100.0;
            }

            BalanceResponse response = new BalanceResponse();
            response.setAvailable(totalAvailable);
            response.setPending(totalPending);
            response.setCurrency(currency);

            logger.info("Balance retrieved for clubId={}: available={}, pending={}", clubId, totalAvailable, totalPending);

            return response;
        } catch (Exception e) {
            logger.error("Error fetching balance for clubId={}: {}", clubId, e.getMessage(), e);
            throw new RuntimeException("Error fetching balance: " + e.getMessage());
        }
    }

    public static class BalanceResponse {
        private double available;
        private double pending;
        private String currency;

        public double getAvailable() { return available; }
        public void setAvailable(double available) { this.available = available; }
        public double getPending() { return pending; }
        public void setPending(double pending) { this.pending = pending; }
        public String getCurrency() { return currency; }
        public void setCurrency(String currency) { this.currency = currency; }
    }

    /**
     * Create a payout to transfer funds to the connected account's bank
     * POST /api/analytics/payout?clubId={id}&amount={amount}
     */
    @org.springframework.web.bind.annotation.PostMapping("/payout")
    @Transactional
    public PayoutResponse createPayout(
            @RequestParam Long clubId,
            @RequestParam(required = false) Double amount) {
        try {
            logger.info("Creating payout for clubId={}, amount={}", clubId, amount);

            // Get the club
            Club club = clubRepository.findById(clubId)
                    .orElseThrow(() -> new RuntimeException("Club not found with id: " + clubId));

            // Get Stripe Account ID from club
            String stripeAccountId = club.getStripeAccountId();

            if (stripeAccountId == null || stripeAccountId.isEmpty()) {
                logger.error("No Stripe account ID found for clubId={}", clubId);
                throw new RuntimeException("Club does not have a Stripe account configured");
            }

            // Fetch balance to determine available funds
            com.stripe.net.RequestOptions requestOptions = com.stripe.net.RequestOptions.builder()
                    .setStripeAccount(stripeAccountId)
                    .build();

            com.stripe.model.Balance balance = com.stripe.model.Balance.retrieve(requestOptions);

            double availableBalance = 0.0;
            String currency = "usd";

            for (int i = 0; i < balance.getAvailable().size(); i++) {
                var money = balance.getAvailable().get(i);
                availableBalance += money.getAmount() / 100.0;
                currency = money.getCurrency();
            }

            if (availableBalance <= 0) {
                throw new RuntimeException("No available balance to payout");
            }

            // If amount not specified, payout entire available balance
            double payoutAmount = amount != null ? amount : availableBalance;

            // Validate amount
            if (payoutAmount <= 0) {
                throw new RuntimeException("Payout amount must be greater than zero");
            }

            if (payoutAmount > availableBalance) {
                throw new RuntimeException("Payout amount ($" + payoutAmount + ") exceeds available balance ($" + availableBalance + ")");
            }

            // Convert to cents for Stripe
            long amountInCents = (long) (payoutAmount * 100);

            // Create payout
            com.stripe.param.PayoutCreateParams params = com.stripe.param.PayoutCreateParams.builder()
                    .setAmount(amountInCents)
                    .setCurrency(currency)
                    .setDescription("Payout for club " + club.getTitle())
                    .build();

            com.stripe.model.Payout payout = com.stripe.model.Payout.create(params, requestOptions);

            PayoutResponse response = new PayoutResponse();
            response.setPayoutId(payout.getId());
            response.setAmount(payoutAmount);
            response.setCurrency(currency);
            response.setStatus(payout.getStatus());
            response.setArrivalDate(payout.getArrivalDate() != null ?
                    java.time.LocalDateTime.ofEpochSecond(payout.getArrivalDate(), 0, java.time.ZoneOffset.UTC).toLocalDate().toString() :
                    null);
            response.setDescription(payout.getDescription());

            logger.info("Payout created successfully for clubId={}: payoutId={}, amount=${}",
                    clubId, payout.getId(), payoutAmount);

            return response;
        } catch (Exception e) {
            logger.error("Error creating payout for clubId={}: {}", clubId, e.getMessage(), e);
            throw new RuntimeException("Error creating payout: " + e.getMessage());
        }
    }

    public static class PayoutResponse {
        private String payoutId;
        private double amount;
        private String currency;
        private String status;
        private String arrivalDate;
        private String description;

        public String getPayoutId() { return payoutId; }
        public void setPayoutId(String payoutId) { this.payoutId = payoutId; }
        public double getAmount() { return amount; }
        public void setAmount(double amount) { this.amount = amount; }
        public String getCurrency() { return currency; }
        public void setCurrency(String currency) { this.currency = currency; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public String getArrivalDate() { return arrivalDate; }
        public void setArrivalDate(String arrivalDate) { this.arrivalDate = arrivalDate; }
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
    }

    /**
     * Get recent activities for a club
     * GET /api/analytics/recent-activity?clubId={id}
     */
    @GetMapping("/recent-activity")
    @Transactional
    public List<RecentActivityResponse> getRecentActivity(@RequestParam Long clubId) {
        try {
            logger.info("Fetching recent activity for clubId={}", clubId);

            // Get activities from last 2 days
            LocalDateTime twoDaysAgo = LocalDateTime.now().minusDays(2);
            List<RecentActivity> activities = recentActivityRepository.findRecentByClubId(clubId, twoDaysAgo);

            List<RecentActivityResponse> response = new ArrayList<>();
            for (RecentActivity activity : activities) {
                RecentActivityResponse dto = new RecentActivityResponse();
                dto.setId(activity.getId());
                dto.setActivityType(activity.getActivityType());
                dto.setDescription(activity.getDescription());
                dto.setAmount(activity.getAmount());
                dto.setCustomerName(activity.getCustomerName());
                dto.setCreatedAt(activity.getCreatedAt());
                response.add(dto);
            }

            logger.info("Found {} recent activities for clubId={}", response.size(), clubId);
            return response;
        } catch (Exception e) {
            logger.error("Error fetching recent activity for clubId={}: {}", clubId, e.getMessage(), e);
            throw new RuntimeException("Error fetching recent activity: " + e.getMessage());
        }
    }

    /**
     * Scheduled job to clean up old activities (older than 2 days)
     * Runs daily at 2 AM
     */
    @Scheduled(cron = "0 0 2 * * ?")
    @Transactional
    public void cleanupOldActivities() {
        try {
            logger.info("Starting cleanup of old activities");

            LocalDateTime twoDaysAgo = LocalDateTime.now().minusDays(2);
            int deletedCount = recentActivityRepository.deleteOlderThan(twoDaysAgo);

            logger.info("Cleanup completed: deleted {} old activities", deletedCount);
        } catch (Exception e) {
            logger.error("Error during activity cleanup: {}", e.getMessage(), e);
        }
    }

    public static class RecentActivityResponse {
        private Long id;
        private String activityType;
        private String description;
        private Double amount;
        private String customerName;
        private LocalDateTime createdAt;

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public String getActivityType() { return activityType; }
        public void setActivityType(String activityType) { this.activityType = activityType; }
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
        public Double getAmount() { return amount; }
        public void setAmount(Double amount) { this.amount = amount; }
        public String getCustomerName() { return customerName; }
        public void setCustomerName(String customerName) { this.customerName = customerName; }
        public LocalDateTime getCreatedAt() { return createdAt; }
        public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    }
}