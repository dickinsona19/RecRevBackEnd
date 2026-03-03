package com.BossLiftingClub.BossLifting.Analytics;

import com.BossLiftingClub.BossLifting.Business.Business;
import com.BossLiftingClub.BossLifting.Business.BusinessRepository;
import com.BossLiftingClub.BossLifting.User.BusinessUser.UserBusiness;
import com.BossLiftingClub.BossLifting.User.BusinessUser.UserBusinessRepository;
import com.BossLiftingClub.BossLifting.User.BusinessUser.UserBusinessMembership;
import com.BossLiftingClub.BossLifting.User.Membership.Membership;
import com.BossLiftingClub.BossLifting.User.User;
import com.BossLiftingClub.BossLifting.User.UserRepository;
import com.stripe.model.*;
import com.stripe.param.SubscriptionListParams;
import com.stripe.param.InvoiceListParams;
import com.stripe.param.RefundListParams;
import com.stripe.param.ChargeListParams;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.format.annotation.DateTimeFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping("/api/analytics")
public class AnalyticsController {

    private static final Logger logger = LoggerFactory.getLogger(AnalyticsController.class);

    private static final long OVERVIEW_CACHE_TTL_MS = 5 * 60 * 1000;
    private static final Map<String, CachedOverviewBundle> overviewBundleCache = new ConcurrentHashMap<>();

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
    private final BusinessRepository businessRepository;

    @Autowired
    private final UserBusinessRepository userBusinessRepository;

    @Autowired
    private final RecentActivityRepository recentActivityRepository;

    public AnalyticsController(UserRepository userRepository, AnalyticsCacheRepository analyticsCacheRepository,
                                ObjectMapper objectMapper, BusinessRepository businessRepository, UserBusinessRepository userBusinessRepository,
                                RecentActivityRepository recentActivityRepository) {
        this.userRepository = userRepository;
        this.analyticsCacheRepository = analyticsCacheRepository;
        this.objectMapper = objectMapper;
        this.businessRepository = businessRepository;
        this.userBusinessRepository = userBusinessRepository;
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
     * Get individual metric - Total Revenue (fast, uses Stripe Balance for "all time")
     * GET /api/analytics/metric/total-revenue?businessTag={tag}&startDate={start}&endDate={end}
     */
    @GetMapping("/metric/total-revenue")
    @Transactional
    public Map<String, Object> getTotalRevenue(
            @RequestParam(required = false) String businessTag,
            @RequestParam(required = false) String clubTag,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {
        try {
            String tag = businessTag != null ? businessTag : clubTag;
            Business business = businessRepository.findByBusinessTag(tag)
                    .orElseThrow(() -> new RuntimeException("Business not found"));
            String stripeAccountId = null; // Single-tenant: platform account only
            LocalDateTime start = parseDate(startDate);
            LocalDateTime end = parseDate(endDate, LocalDateTime.now());

            com.stripe.net.RequestOptions requestOptions = (stripeAccountId != null && !stripeAccountId.isEmpty())
                    ? com.stripe.net.RequestOptions.builder().setStripeAccount(stripeAccountId).build()
                    : null;

            double revenue;
            // Always use optimized charge fetching - only fetch charges in the date range
            if (start == null) {
                // For "all time", use wide date range (e.g. 10 years) instead of unbounded fetch
                LocalDateTime chargeStart = LocalDateTime.now().minusYears(10);
                List<Charge> allCharges = fetchChargesForDateRange(requestOptions, chargeStart, LocalDateTime.now());
                revenue = calculateTotalRevenueFromCache(allCharges, null, LocalDateTime.now());
            } else {
                // For date ranges, only fetch charges in that range (much faster)
                List<Charge> charges = fetchChargesForDateRange(requestOptions, start, end);
                revenue = calculateTotalRevenueFromCache(charges, start, end);
            }

            Map<String, Object> result = new HashMap<>();
            result.put("value", revenue);
            return result;
        } catch (Exception e) {
            logger.error("Error fetching total revenue: {}", e.getMessage(), e);
            Map<String, Object> error = new HashMap<>();
            error.put("error", e.getMessage());
            return error;
        }
    }

    /**
     * Get individual metric - Active Members
     */
    @GetMapping("/metric/active-members")
    @Transactional
    public Map<String, Object> getActiveMembers(
            @RequestParam(required = false) String businessTag,
            @RequestParam(required = false) String clubTag) {
        try {
            String tag = businessTag != null ? businessTag : clubTag;
            List<UserBusiness> userBusinesses = userBusinessRepository.findAllByBusinessTag(tag);
            int active = calculateActiveMembers(userBusinesses);
            Map<String, Object> result = new HashMap<>();
            result.put("value", active);
            return result;
        } catch (Exception e) {
            logger.error("Error fetching active members: {}", e.getMessage(), e);
            Map<String, Object> error = new HashMap<>();
            error.put("error", e.getMessage());
            return error;
        }
    }

    /**
     * Get individual metric - MRR
     */
    @GetMapping("/metric/mrr")
    @Transactional
    public Map<String, Object> getMRR(
            @RequestParam(required = false) String businessTag,
            @RequestParam(required = false) String clubTag) {
        try {
            String tag = businessTag != null ? businessTag : clubTag;
            List<UserBusiness> userBusinesses = userBusinessRepository.findAllByBusinessTag(tag);
            double mrr = calculateMRR(userBusinesses);
            Map<String, Object> result = new HashMap<>();
            result.put("value", mrr);
            return result;
        } catch (Exception e) {
            logger.error("Error fetching MRR: {}", e.getMessage(), e);
            Map<String, Object> error = new HashMap<>();
            error.put("error", e.getMessage());
            return error;
        }
    }

    /**
     * Get individual metric - Average LTV
     */
    @GetMapping("/metric/average-ltv")
    @Transactional
    public Map<String, Object> getAverageLTV(
            @RequestParam(required = false) String businessTag,
            @RequestParam(required = false) String clubTag) {
        try {
            String tag = businessTag != null ? businessTag : clubTag;
            Business business = businessRepository.findByBusinessTag(tag)
                    .orElseThrow(() -> new RuntimeException("Business not found"));
            String stripeAccountId = null; // Single-tenant: platform account only
            List<UserBusiness> userBusinesses = userBusinessRepository.findAllByBusinessTag(tag);
            com.stripe.net.RequestOptions requestOptions = (stripeAccountId != null && !stripeAccountId.isEmpty())
                    ? com.stripe.net.RequestOptions.builder().setStripeAccount(stripeAccountId).build()
                    : null;
            
            // Fetch charges in practical range for lifetime revenue (10 years)
            LocalDateTime chargeStart = LocalDateTime.now().minusYears(10);
            List<Charge> allCharges = fetchChargesForDateRange(requestOptions, chargeStart, LocalDateTime.now());
            double lifetimeRevenue = calculateLifetimeRevenueFromCache(allCharges);
            
            double ltv = userBusinesses.size() > 0 ? lifetimeRevenue / userBusinesses.size() : 0.0;
            Map<String, Object> result = new HashMap<>();
            result.put("value", ltv);
            return result;
        } catch (Exception e) {
            logger.error("Error fetching average LTV: {}", e.getMessage(), e);
            Map<String, Object> error = new HashMap<>();
            error.put("error", e.getMessage());
            return error;
        }
    }

    // Helper method to parse dates
    private LocalDateTime parseDate(String dateStr, LocalDateTime defaultValue) {
        if (dateStr == null || dateStr.isEmpty()) {
            return defaultValue;
        }
        try {
            return java.time.ZonedDateTime.parse(dateStr).toLocalDateTime();
        } catch (Exception e) {
            logger.warn("Error parsing date: {}, using default", dateStr);
            return defaultValue;
        }
    }

    private LocalDateTime parseDate(String dateStr) {
        return parseDate(dateStr, null);
    }

    // Optimized: Fetch only charges for specific date range
    private List<Charge> fetchChargesForDateRange(com.stripe.net.RequestOptions requestOptions, LocalDateTime start, LocalDateTime end) {
        List<Charge> charges = new ArrayList<>();
        try {
            long startEpoch = start.atZone(ZoneId.systemDefault()).toEpochSecond();
            long endEpoch = end.atZone(ZoneId.systemDefault()).toEpochSecond();
            
            ChargeListParams params = ChargeListParams.builder()
                    .setLimit(100L)
                    .setCreated(ChargeListParams.Created.builder()
                            .setGte(startEpoch)
                            .setLte(endEpoch)
                            .build())
                    .build();
            
            ChargeCollection chargeCollection = requestOptions != null ? Charge.list(params, requestOptions) : Charge.list(params);
            for (Charge charge : chargeCollection.autoPagingIterable()) {
                charges.add(charge);
            }
            logger.debug("Fetched {} charges for date range", charges.size());
        } catch (Exception e) {
            logger.error("Error fetching charges for date range: {}", e.getMessage(), e);
        }
        return charges;
    }

    /**
     * Get comprehensive dashboard metrics for a business
     * GET /api/analytics/dashboard?businessTag={tag}&startDate={start}&endDate={end}
     */
    @GetMapping("/dashboard")
    @Transactional
    public Map<String, Object> getDashboardMetrics(
            @RequestParam(required = false) String businessTag,
            @RequestParam(required = false) String clubTag, // Backward compatibility
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {
        try {
            String tag = businessTag != null ? businessTag : clubTag;
            if (tag == null) {
                throw new RuntimeException("businessTag parameter is required");
            }
            Business business = businessRepository.findByBusinessTag(tag)
                    .orElseThrow(() -> new RuntimeException("Business not found with tag: " + tag));

            String stripeAccountId = null; // Single-tenant: platform account only

            // Parse date range - handle ISO 8601 format with timezone (Z suffix)
            LocalDateTime start = null;
            LocalDateTime end = LocalDateTime.now();

            try {
                if (startDate != null && !startDate.isEmpty()) {
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

            List<UserBusiness> userBusinesses = userBusinessRepository.findAllByBusinessTag(tag);

            com.stripe.net.RequestOptions requestOptions = (stripeAccountId != null && !stripeAccountId.isEmpty())
                    ? com.stripe.net.RequestOptions.builder().setStripeAccount(stripeAccountId).build()
                    : null;
            
            // Use date-filtered charge fetch when we have dates - much faster than fetching ALL charges
            LocalDateTime chargeStart = start;
            LocalDateTime chargeEnd = end;
            if (chargeStart == null) {
                chargeStart = LocalDateTime.now().minusYears(2);
                chargeEnd = LocalDateTime.now();
            }
            List<Charge> allCharges = fetchChargesForDateRange(requestOptions, chargeStart, chargeEnd);
            
            List<Refund> allRefunds = fetchAllRefunds(requestOptions, chargeStart, chargeEnd);

            Map<String, Object> metrics = new HashMap<>();

            // Calculate metrics based on requirements - reuse cached data
            metrics.put("totalRevenue", calculateTotalRevenueFromCache(allCharges, start, end));
            metrics.put("activeMembers", calculateActiveMembers(userBusinesses));
            metrics.put("totalMembers", userBusinesses.size());
            metrics.put("mrr", calculateMRR(userBusinesses));
            metrics.put("memberGrowth", calculateMemberGrowth(userBusinesses, start, end));
            metrics.put("revenueGrowth", calculateRevenueGrowthFromCache(allCharges, start, end));
            metrics.put("churnRate", calculateChurnRate(userBusinesses, start, end));
            metrics.put("churnCount", calculateChurnCount(userBusinesses, start, end));
            metrics.put("newMembers", calculateNewMembers(userBusinesses, start, end));
            metrics.put("totalLifetimeRevenue", calculateLifetimeRevenueFromCache(allCharges));
            metrics.put("averageLTV", calculateAverageLTVFromCache(allCharges, userBusinesses.size()));
            metrics.put("failedPayments", getFailedPayments(stripeAccountId, start, end));
            metrics.put("refundedPayments", getRefundedPaymentsFromCache(allRefunds, start, end));
            metrics.put("membershipBreakdown", getMembershipBreakdown(userBusinesses));

            return metrics;
        } catch (RuntimeException e) {
            // If already a RuntimeException with specific message, rethrow
            if (e.getMessage() != null && e.getMessage().contains("Business not found")) {
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

    // OPTIMIZATION: Fetch all refunds once and cache them
    private List<Refund> fetchAllRefunds(com.stripe.net.RequestOptions requestOptions, LocalDateTime start, LocalDateTime end) {
        List<Refund> allRefunds = new ArrayList<>();
        try {
            RefundListParams.Builder paramsBuilder = RefundListParams.builder();
            if (start != null) {
                paramsBuilder.setCreated(RefundListParams.Created.builder()
                        .setGte(start.atZone(ZoneId.systemDefault()).toEpochSecond())
                        .setLte(end.atZone(ZoneId.systemDefault()).toEpochSecond())
                        .build());
            }
            RefundCollection refunds = requestOptions != null ? Refund.list(paramsBuilder.build(), requestOptions) : Refund.list(paramsBuilder.build());
            for (Refund refund : refunds.autoPagingIterable()) {
                allRefunds.add(refund);
            }
            logger.debug("Fetched {} refunds from Stripe", allRefunds.size());
        } catch (Exception e) {
            logger.error("Error fetching refunds: {}", e.getMessage(), e);
        }
        return allRefunds;
    }

    // Calculate revenue from cached charges (much faster)
    private double calculateTotalRevenueFromCache(List<Charge> allCharges, LocalDateTime start, LocalDateTime end) {
        long startEpoch = start != null ? start.atZone(ZoneId.systemDefault()).toEpochSecond() : 0;
        long endEpoch = end != null ? end.atZone(ZoneId.systemDefault()).toEpochSecond() : Long.MAX_VALUE;
        
        double totalRevenue = 0.0;
        for (Charge charge : allCharges) {
            if (charge.getPaid() && !charge.getRefunded()) {
                long chargeTime = charge.getCreated();
                if (chargeTime >= startEpoch && chargeTime <= endEpoch) {
                    totalRevenue += charge.getAmount() / 100.0;
                }
            }
        }
        return totalRevenue;
    }

    private double calculateTotalRevenue(String stripeAccountId, LocalDateTime start, LocalDateTime end) throws Exception {
        com.stripe.net.RequestOptions requestOptions = (stripeAccountId != null && !stripeAccountId.isEmpty())
                ? com.stripe.net.RequestOptions.builder().setStripeAccount(stripeAccountId).build()
                : null;

        ChargeListParams.Builder paramsBuilder = ChargeListParams.builder().setLimit(100L);

        if (start != null) {
            paramsBuilder.setCreated(ChargeListParams.Created.builder()
                    .setGte(start.atZone(ZoneId.systemDefault()).toEpochSecond())
                    .setLte(end.atZone(ZoneId.systemDefault()).toEpochSecond())
                    .build());
        }

        ChargeCollection charges = requestOptions != null ? Charge.list(paramsBuilder.build(), requestOptions) : Charge.list(paramsBuilder.build());
        double totalRevenue = 0.0;

        for (Charge charge : charges.autoPagingIterable()) {
            if (charge.getPaid() && !charge.getRefunded()) {
                totalRevenue += charge.getAmount() / 100.0;
            }
        }

        return totalRevenue;
    }

    private int calculateActiveMembers(List<UserBusiness> userBusinesses) {
        int active = 0;
        for (UserBusiness userBusiness : userBusinesses) {
            List<UserBusinessMembership> memberships = userBusiness.getUserBusinessMemberships();
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

    private double calculateMRR(List<UserBusiness> userBusinesses) {
        double mrr = 0.0;
        for (UserBusiness userBusiness : userBusinesses) {
            for (UserBusinessMembership membership : userBusiness.getUserBusinessMemberships()) {
                if ("ACTIVE".equalsIgnoreCase(membership.getStatus())) {
                    double price = getLockedPrice(membership);
                    mrr += price;
                }
            }
        }
        return roundTwo(mrr);
    }

    private Map<String, Object> calculateMemberGrowth(List<UserBusiness> userBusinesses, LocalDateTime start, LocalDateTime end) {
        Map<String, Object> growth = new HashMap<>();

        if (start == null) {
            growth.put("percentChange", 0.0);
            growth.put("absoluteChange", 0);
            return growth;
        }

        int membersNow = userBusinesses.size();
        int membersBefore = (int) userBusinesses.stream()
                .filter(ub -> ub.getCreatedAt().isBefore(start))
                .count();

        int absoluteChange = membersNow - membersBefore;
        double percentChange = membersBefore > 0 ? ((double) absoluteChange / membersBefore) * 100 : 0.0;

        growth.put("percentChange", percentChange);
        growth.put("absoluteChange", absoluteChange);
        return growth;
    }

    // Optimized version using cached charges
    private Map<String, Object> calculateRevenueGrowthFromCache(List<Charge> allCharges, LocalDateTime start, LocalDateTime end) {
        Map<String, Object> growth = new HashMap<>();

        if (start == null) {
            growth.put("percentChange", 0.0);
            return growth;
        }

        long periodDays = java.time.Duration.between(start, end).toDays();
        LocalDateTime previousStart = start.minusDays(periodDays);

        double currentRevenue = calculateTotalRevenueFromCache(allCharges, start, end);
        double previousRevenue = calculateTotalRevenueFromCache(allCharges, previousStart, start);

        double percentChange = previousRevenue > 0 ? ((currentRevenue - previousRevenue) / previousRevenue) * 100 : 0.0;

        growth.put("percentChange", percentChange);
        growth.put("currentPeriod", currentRevenue);
        growth.put("previousPeriod", previousRevenue);
        return growth;
    }

    // Legacy method kept for backward compatibility
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

    private double calculateChurnRate(List<UserBusiness> userBusinesses, LocalDateTime start, LocalDateTime end) {
        if (start == null) return 0.0;

        int churned = calculateChurnCount(userBusinesses, start, end);
        int totalAtStart = (int) userBusinesses.stream()
                .filter(ub -> ub.getCreatedAt().isBefore(start))
                .count();

        return totalAtStart > 0 ? ((double) churned / totalAtStart) * 100 : 0.0;
    }

    private int calculateChurnCount(List<UserBusiness> userBusinesses, LocalDateTime start, LocalDateTime end) {
        if (start == null || end == null) return 0;

        return (int) userBusinesses.stream()
                .filter(ub -> ub.getUserBusinessMemberships().stream()
                        .anyMatch(m -> {
                            LocalDateTime endDate = m.getEndDate();
                            return ("CANCELLED".equalsIgnoreCase(m.getStatus()) || "INACTIVE".equalsIgnoreCase(m.getStatus()))
                                    && endDate != null
                                    && endDate.isAfter(start)
                                    && endDate.isBefore(end);
                        }))
                .count();
    }

    private int calculateNewMembers(List<UserBusiness> userBusinesses, LocalDateTime start, LocalDateTime end) {
        if (start == null) return userBusinesses.size();

        return (int) userBusinesses.stream()
                .filter(ub -> ub.getCreatedAt().isAfter(start) && ub.getCreatedAt().isBefore(end))
                .count();
    }

    // Optimized version using cached charges
    private double calculateLifetimeRevenueFromCache(List<Charge> allCharges) {
        return calculateTotalRevenueFromCache(allCharges, null, LocalDateTime.now());
    }

    private double calculateAverageLTVFromCache(List<Charge> allCharges, int totalMembers) {
        if (totalMembers == 0) return 0.0;
        return calculateLifetimeRevenueFromCache(allCharges) / totalMembers;
    }

    // Legacy methods kept for backward compatibility
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

    // Optimized version using cached refunds
    private Map<String, Object> getRefundedPaymentsFromCache(List<Refund> allRefunds, LocalDateTime start, LocalDateTime end) {
        Map<String, Object> refunded = new HashMap<>();
        int count = 0;
        double amount = 0.0;
        
        long startEpoch = start != null ? start.atZone(ZoneId.systemDefault()).toEpochSecond() : 0;
        long endEpoch = end != null ? end.atZone(ZoneId.systemDefault()).toEpochSecond() : Long.MAX_VALUE;
        
        for (Refund refund : allRefunds) {
            long refundTime = refund.getCreated();
            if (refundTime >= startEpoch && refundTime <= endEpoch) {
                count++;
                amount += refund.getAmount() / 100.0;
            }
        }

        refunded.put("count", count);
        refunded.put("amount", amount);
        return refunded;
    }

    private Map<String, Object> getRefundedPayments(String stripeAccountId, LocalDateTime start, LocalDateTime end) throws Exception {
        Map<String, Object> refunded = new HashMap<>();
        int count = 0;
        double amount = 0.0;

        com.stripe.net.RequestOptions requestOptions = (stripeAccountId != null && !stripeAccountId.isEmpty())
                ? com.stripe.net.RequestOptions.builder().setStripeAccount(stripeAccountId).build()
                : null;

        RefundListParams.Builder paramsBuilder = RefundListParams.builder();
        if (start != null) {
            paramsBuilder.setCreated(RefundListParams.Created.builder()
                    .setGte(start.atZone(ZoneId.systemDefault()).toEpochSecond())
                    .setLte(end.atZone(ZoneId.systemDefault()).toEpochSecond())
                    .build());
        }

        RefundCollection refunds = requestOptions != null ? Refund.list(paramsBuilder.build(), requestOptions) : Refund.list(paramsBuilder.build());
        for (Refund refund : refunds.autoPagingIterable()) {
            count++;
            amount += refund.getAmount() / 100.0;
        }

        refunded.put("count", count);
        refunded.put("amount", amount);
        return refunded;
    }

    private List<Map<String, Object>> getMembershipBreakdown(List<UserBusiness> userBusinesses) {
        Map<String, Map<String, Object>> breakdownMap = new HashMap<>();

        for (UserBusiness userBusiness : userBusinesses) {
            for (UserBusinessMembership ubm : userBusiness.getUserBusinessMemberships()) {
                Membership membership = ubm.getMembership();
                String membershipName = membership.getTitle();

                breakdownMap.putIfAbsent(membershipName, new HashMap<>());
                Map<String, Object> data = breakdownMap.get(membershipName);

                data.put("membershipName", membershipName);
                data.putIfAbsent("totalCount", 0);
                data.putIfAbsent("activeCount", 0);

                double planPrice = parsePlanPrice(membership.getPrice());
                if (!data.containsKey("planPrice")) {
                    data.put("planPrice", planPrice);
                }

                double lockedPrice = getLockedPrice(ubm);
                data.put("totalCount", (int) data.get("totalCount") + 1);
                data.put("totalLockedRevenue", ((Double) data.getOrDefault("totalLockedRevenue", 0.0)) + lockedPrice);

                if ("ACTIVE".equalsIgnoreCase(ubm.getStatus())) {
                    data.put("activeCount", (int) data.get("activeCount") + 1);
                    data.put("activeLockedRevenue", ((Double) data.getOrDefault("activeLockedRevenue", 0.0)) + lockedPrice);
                }
            }
        }

        List<Map<String, Object>> breakdown = new ArrayList<>();
        for (Map<String, Object> data : breakdownMap.values()) {
            int activeCount = (int) data.getOrDefault("activeCount", 0);
            double activeLockedRevenue = (double) data.getOrDefault("activeLockedRevenue", 0.0);
            double averageLockedPrice = activeCount > 0 ? activeLockedRevenue / activeCount : (double) data.getOrDefault("planPrice", 0.0);

            data.put("price", roundTwo(averageLockedPrice));
            data.put("monthlyRevenue", roundTwo(activeLockedRevenue));

            data.remove("activeLockedRevenue");
            data.remove("totalLockedRevenue");
            breakdown.add(data);
        }

        return breakdown;
    }

    /**
     * Combined overview bundle: stats + balance + activity in one request.
     * Cached for 5 minutes. Use this for fast initial Overview page load.
     */
    @GetMapping("/overview-bundle")
    @Transactional
    public ResponseEntity<?> getOverviewBundle(
            @RequestParam Long businessId,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {
        try {
            LocalDateTime start = parseOverviewDate(startDate);
            LocalDateTime end = parseOverviewDate(endDate);
            String cacheKey = "bundle_" + businessId + "_" + (startDate != null ? startDate : "") + "_" + (endDate != null ? endDate : "");
            CachedOverviewBundle cached = overviewBundleCache.get(cacheKey);
            long now = System.currentTimeMillis();
            if (cached != null && now < cached.expiresAt) {
                return ResponseEntity.ok(cached.data);
            }
            if (overviewBundleCache.size() > 50) {
                overviewBundleCache.entrySet().removeIf(e -> {
                    CachedOverviewBundle c = e.getValue();
                    return c != null && now >= c.expiresAt;
                });
            }
            Map<String, Object> bundle = new HashMap<>();
            ClubOverviewResponse overview = getClubOverviewInternal(businessId, start, end);
            bundle.put("overview", Map.of(
                    "totalRevenue", overview.getTotalRevenue(),
                    "mrr", overview.getMrr(),
                    "totalActiveMembers", overview.getTotalActiveMembers(),
                    "newMembers", overview.getNewMembers()));
            try {
                BalanceResponse balance = getBalanceInternal(businessId);
                bundle.put("balance", Map.of(
                        "available", balance.getAvailable(),
                        "pending", balance.getPending(),
                        "currency", balance.getCurrency() != null ? balance.getCurrency() : "usd",
                        "pendingByDate", balance.getPendingByDate() != null ? balance.getPendingByDate() : Map.of()));
            } catch (Exception e) {
                logger.warn("Balance fetch failed in bundle: {}", e.getMessage());
                bundle.put("balance", Map.of("available", 0.0, "pending", 0.0, "currency", "usd", "pendingByDate", Map.of()));
            }
            try {
                List<Map<String, Object>> activity = getOverviewActivityInternal(businessId);
                bundle.put("activity", activity);
            } catch (Exception e) {
                logger.warn("Activity fetch failed in bundle: {}", e.getMessage());
                bundle.put("activity", List.of());
            }
            overviewBundleCache.put(cacheKey, new CachedOverviewBundle(bundle, System.currentTimeMillis() + OVERVIEW_CACHE_TTL_MS));
            return ResponseEntity.ok(bundle);
        } catch (Exception e) {
            logger.error("Error fetching overview bundle for businessId={}: {}", businessId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to fetch overview: " + e.getMessage()));
        }
    }

    private BalanceResponse getBalanceInternal(Long businessId) {
        Business business = businessRepository.findById(businessId)
                .orElseThrow(() -> new RuntimeException("Business not found"));
        String stripeAccountId = null;
        com.stripe.net.RequestOptions ro = (stripeAccountId != null && !stripeAccountId.isEmpty())
                ? com.stripe.net.RequestOptions.builder().setStripeAccount(stripeAccountId).build() : null;
        com.stripe.model.Balance balance;
        try {
            balance = ro != null ? com.stripe.model.Balance.retrieve(ro) : com.stripe.model.Balance.retrieve();
        } catch (com.stripe.exception.StripeException e) {
            logger.warn("Failed to retrieve Stripe balance: {}", e.getMessage());
            BalanceResponse empty = new BalanceResponse();
            empty.setAvailable(0.0);
            empty.setPending(0.0);
            empty.setCurrency("usd");
            empty.setPendingByDate(Collections.emptyMap());
            return empty;
        }
        double totalAvailable = 0.0, totalPending = 0.0;
        String currency = "usd";
        for (int i = 0; i < balance.getAvailable().size(); i++) {
            var m = balance.getAvailable().get(i);
            totalAvailable += m.getAmount() / 100.0;
            currency = m.getCurrency() != null ? String.valueOf(m.getCurrency()) : "usd";
        }
        for (int i = 0; i < balance.getPending().size(); i++) {
            totalPending += balance.getPending().get(i).getAmount() / 100.0;
        }
        Map<String, Double> pendingByDate = new HashMap<>();
        try {
            long now = java.time.Instant.now().getEpochSecond();
            var params = com.stripe.param.BalanceTransactionListParams.builder().setLimit(50L).build();
            var txns = ro != null ? com.stripe.model.BalanceTransaction.list(params, ro) : com.stripe.model.BalanceTransaction.list(params);
            for (var t : txns.getData()) {
                Long avOn = t.getAvailableOn();
                if (avOn != null && avOn > now && t.getAmount() > 0) {
                    String key = java.time.Instant.ofEpochSecond(avOn).atZone(ZoneId.systemDefault()).toLocalDate().toString();
                    pendingByDate.merge(key, t.getAmount() / 100.0, Double::sum);
                }
            }
        } catch (Exception e) {
            logger.debug("Pending by date fetch failed: {}", e.getMessage());
        }
        BalanceResponse resp = new BalanceResponse();
        resp.setAvailable(totalAvailable);
        resp.setPending(totalPending);
        resp.setCurrency(currency);
        resp.setPendingByDate(pendingByDate);
        return resp;
    }

    private List<Map<String, Object>> getOverviewActivityInternal(Long businessId) throws Exception {
        Business business = businessRepository.findById(businessId)
                .orElseThrow(() -> new RuntimeException("Business not found"));
        String stripeAccountId = null;
        List<Map<String, Object>> activities = new ArrayList<>();
        LocalDateTime sevenDaysAgo = LocalDateTime.now().minusDays(7);
        long sevenDaysEpoch = sevenDaysAgo.atZone(ZoneId.systemDefault()).toEpochSecond();
        com.stripe.net.RequestOptions ro = (stripeAccountId != null && !stripeAccountId.isEmpty())
                ? com.stripe.net.RequestOptions.builder().setStripeAccount(stripeAccountId).build() : null;
        ChargeListParams chargeParams = ChargeListParams.builder()
                .setLimit(50L)
                .setCreated(ChargeListParams.Created.builder().setGte(sevenDaysEpoch).build())
                .build();
        var chargeColl = ro != null ? Charge.list(chargeParams, ro) : Charge.list(chargeParams);
        for (Charge charge : chargeColl.getData()) {
            if (charge.getCreated() < sevenDaysEpoch) break;
            if ("succeeded".equals(charge.getStatus()) && charge.getPaid()) {
                Map<String, Object> a = new HashMap<>();
                a.put("type", "PAYMENT");
                a.put("icon", "DollarSign");
                a.put("text", "Payment received from " + (charge.getBillingDetails() != null && charge.getBillingDetails().getName() != null ? charge.getBillingDetails().getName() : "Customer"));
                a.put("amount", charge.getAmount() / 100.0);
                a.put("time", formatTimeAgo(charge.getCreated()));
                a.put("timestamp", charge.getCreated());
                activities.add(a);
            }
        }
        InvoiceListParams openParams = InvoiceListParams.builder().setStatus(InvoiceListParams.Status.OPEN).setLimit(20L).build();
        var openInvs = ro != null ? Invoice.list(openParams, ro) : Invoice.list(openParams);
        for (Invoice inv : openInvs.getData()) {
            if (inv.getCreated() < sevenDaysEpoch) break;
            Map<String, Object> a = new HashMap<>();
            a.put("type", "FAILED_PAYMENT");
            a.put("icon", "AlertCircle");
            a.put("text", "Failed payment from " + (inv.getCustomerName() != null ? inv.getCustomerName() : "Customer"));
            a.put("amount", inv.getAmountDue() / 100.0);
            a.put("time", formatTimeAgo(inv.getCreated()));
            a.put("timestamp", inv.getCreated());
            activities.add(a);
        }
        List<UserBusiness> newMembers = userBusinessRepository.findByBusinessIdWithMemberships(businessId).stream()
                .filter(ub -> ub.getCreatedAt() != null && ub.getCreatedAt().isAfter(sevenDaysAgo))
                .sorted((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()))
                .limit(20)
                .collect(java.util.stream.Collectors.toList());
        for (UserBusiness ub : newMembers) {
            Map<String, Object> a = new HashMap<>();
            a.put("type", "NEW_MEMBER");
            a.put("icon", "UserPlus");
            a.put("text", "New member " + ub.getUser().getFirstName() + " " + ub.getUser().getLastName() + " joined");
            a.put("time", formatTimeAgo(ub.getCreatedAt().atZone(ZoneId.systemDefault()).toEpochSecond()));
            a.put("timestamp", ub.getCreatedAt().atZone(ZoneId.systemDefault()).toEpochSecond());
            activities.add(a);
        }
        activities.sort((x, y) -> Long.compare((Long) y.get("timestamp"), (Long) x.get("timestamp")));
        return activities.stream().limit(20).collect(java.util.stream.Collectors.toList());
    }

    private LocalDateTime parseOverviewDate(String dateStr) {
        if (dateStr == null || dateStr.isBlank()) return null;
        try {
            return java.time.ZonedDateTime.parse(dateStr).toLocalDateTime();
        } catch (Exception e) {
            try {
                return LocalDate.parse(dateStr).atStartOfDay();
            } catch (Exception e2) {
                return null;
            }
        }
    }

    private static class CachedOverviewBundle {
        final Map<String, Object> data;
        final long expiresAt;
        CachedOverviewBundle(Map<String, Object> data, long expiresAt) {
            this.data = data;
            this.expiresAt = expiresAt;
        }
    }

    /**
     * Get business-specific overview analytics
     * GET /api/analytics/business-overview?businessId={id}&startDate={date}&endDate={date}
     * startDate and endDate are optional - if not provided, calculates revenue for all time
     */
    @GetMapping("/business-overview")
    @Transactional
    public ResponseEntity<?> getBusinessOverview(
            @RequestParam Long businessId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate) {
        try {
            // TODO: Add business scope validation for staff after verifying staff business loading works
            // For now, we allow all authenticated users to access any business
            // This will be secured once we verify the staff business relationship is properly loaded
            
            return ResponseEntity.ok(getClubOverviewInternal(businessId, startDate, endDate));
        } catch (Exception e) {
            logger.error("Error fetching business overview for businessId={}: {}", businessId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to fetch business overview: " + e.getMessage()));
        }
    }
    
    /**
     * Backward compatibility endpoint
     * GET /api/analytics/club-overview?clubId={id}
     * @deprecated Use /api/analytics/business-overview?businessId={id} instead
     */
    @Deprecated
    @GetMapping("/club-overview")
    @Transactional
    public ResponseEntity<?> getClubOverview(@RequestParam Long clubId,
                                             @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
                                             @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate) {
        return getBusinessOverview(clubId, startDate, endDate);
    }
    
    private ClubOverviewResponse getClubOverviewInternal(Long businessId, LocalDateTime startDate, LocalDateTime endDate) {
        try {
            // Get the business
            Business business = businessRepository.findById(businessId)
                    .orElseThrow(() -> new RuntimeException("Business not found with id: " + businessId));

            List<UserBusiness> userBusinesses = userBusinessRepository.findByBusinessIdWithMemberships(businessId);
            logger.info("Found {} UserBusiness records for businessId={}", userBusinesses.size(), businessId);

            String stripeAccountId = null; // Single-tenant: platform account only

            // Calculate Total Active Members (users with at least one ACTIVE membership)
            int totalActiveMembers = 0;
            for (UserBusiness userBusiness : userBusinesses) {
                boolean hasActiveMembership = false;
                for (UserBusinessMembership membership : userBusiness.getUserBusinessMemberships()) {
                    if ("ACTIVE".equalsIgnoreCase(membership.getStatus())) {
                        hasActiveMembership = true;
                        break;
                    }
                }
                if (hasActiveMembership) {
                    totalActiveMembers++;
                }
            }

            // Calculate New Members (UserBusiness created in last 30 days)
            LocalDateTime thirtyDaysAgo = LocalDateTime.now().minusDays(30);
            int newMembers = 0;
            for (UserBusiness userBusiness : userBusinesses) {
                if (userBusiness.getCreatedAt() != null && userBusiness.getCreatedAt().isAfter(thirtyDaysAgo)) {
                    newMembers++;
                }
            }

            // Calculate MRR from active memberships
            double mrr = 0.0;
            for (UserBusiness userBusiness : userBusinesses) {
                for (UserBusinessMembership membership : userBusiness.getUserBusinessMemberships()) {
                    if ("ACTIVE".equalsIgnoreCase(membership.getStatus())) {
                        double price = getLockedPrice(membership);
                        Membership membershipType = membership.getMembership();
                        if (membershipType != null) {
                            String chargeInterval = membershipType.getChargeInterval();
                            if ("yearly".equalsIgnoreCase(chargeInterval) || "annual".equalsIgnoreCase(chargeInterval)) {
                                price = price / 12.0;
                            }
                        }
                        mrr += price;
                    }
                }
            }

            // Calculate Total Revenue from Stripe - Pull ALL charges from the connected account
            double totalRevenue = 0.0;

            try {
                    com.stripe.net.RequestOptions requestOptions = (stripeAccountId != null && !stripeAccountId.isEmpty())
                            ? com.stripe.net.RequestOptions.builder().setStripeAccount(stripeAccountId).build()
                            : null;

                    int totalCharges = 0;

                    long startEpoch;
                    long endEpoch;
                    if (startDate != null && endDate != null) {
                        startEpoch = startDate.atZone(ZoneId.systemDefault()).toEpochSecond();
                        endEpoch = endDate.atZone(ZoneId.systemDefault()).toEpochSecond();
                    } else {
                        LocalDateTime defEnd = LocalDateTime.now();
                        LocalDateTime defStart = defEnd.minusMonths(12);
                        startEpoch = defStart.atZone(ZoneId.systemDefault()).toEpochSecond();
                        endEpoch = defEnd.atZone(ZoneId.systemDefault()).toEpochSecond();
                    }
                    ChargeListParams params = ChargeListParams.builder()
                            .setLimit(100L)
                            .setCreated(ChargeListParams.Created.builder()
                                    .setGte(startEpoch)
                                    .setLte(endEpoch)
                                    .build())
                            .build();

                    ChargeCollection charges = requestOptions != null ? Charge.list(params, requestOptions) : Charge.list(params);

                    for (Charge charge : charges.autoPagingIterable()) {
                        // Only count successful charges that haven't been refunded
                        if (charge.getPaid() && !charge.getRefunded()) {
                            double amount = charge.getAmount() / 100.0;
                            totalRevenue += amount;
                            totalCharges++;
                            logger.debug("Added charge ${} (ID: {})", amount, charge.getId());
                        }
                    }
                    logger.info("Total revenue from Stripe: ${} from {} charges for businessId={}",
                        totalRevenue, totalCharges, businessId);
                } catch (Exception e) {
                    logger.error("Error fetching Stripe data for businessId={}: {}", businessId, e.getMessage(), e);
                }

            ClubOverviewResponse response = new ClubOverviewResponse();
            response.setTotalRevenue(totalRevenue);
            response.setMrr(mrr);
            response.setTotalActiveMembers(totalActiveMembers);
            response.setNewMembers(newMembers);

            logger.info("Business overview calculated: totalRevenue={}, mrr={}, activeMembers={}, newMembers={}",
                    totalRevenue, mrr, totalActiveMembers, newMembers);

            return response;
        } catch (Exception e) {
            logger.error("Error fetching business overview for businessId={}: {}", businessId, e.getMessage(), e);
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
     * Get revenue chart data from Stripe for a specific business
     * GET /api/analytics/revenue-chart?businessId={id}&period={period}
     *
     * @param businessId The business ID
     * @param period Time period: "today", "7d", "30d", "90d", "1y", or "all"
     */
    @GetMapping("/revenue-chart")
    @Transactional
    public ResponseEntity<?> getRevenueChart(
            @RequestParam(required = false) Long businessId,
            @RequestParam(required = false) Long clubId, // Backward compatibility
            @RequestParam(defaultValue = "30d") String period,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {
        Long actualBusinessId = businessId != null ? businessId : clubId;
        if (actualBusinessId == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", "businessId parameter is required"));
        }
        
        try {
            // Get the business
            Business business = businessRepository.findById(actualBusinessId)
                    .orElse(null);

            if (business == null) {
                logger.error("Business not found with id: {}", actualBusinessId);
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("error", "Business not found with id: " + actualBusinessId));
            }

            String stripeAccountId = null; // Single-tenant: platform account only

            // Calculate date range based on period or custom dates
            LocalDateTime endDateTime = LocalDateTime.now();
            LocalDateTime startDateTime;

            if (startDate != null && !startDate.isEmpty()) {
                try {
                    // Parse dates (assuming YYYY-MM-DD format from frontend date input)
                    startDateTime = LocalDate.parse(startDate).atStartOfDay();
                    if (endDate != null && !endDate.isEmpty()) {
                        endDateTime = LocalDate.parse(endDate).atTime(23, 59, 59);
                    }
                    // If custom dates are provided, we treat it as a custom period
                    period = "custom";
                } catch (Exception e) {
                    return ResponseEntity.badRequest().body(Map.of("error", "Invalid date format. Use YYYY-MM-DD"));
                }
            } else {
                switch (period.toLowerCase()) {
                    case "today":
                        startDateTime = LocalDate.now().atStartOfDay();
                        break;
                    case "7d":
                        startDateTime = endDateTime.minusDays(7);
                        break;
                    case "30d":
                        startDateTime = endDateTime.minusDays(30);
                        break;
                    case "90d":
                        startDateTime = endDateTime.minusDays(90);
                        break;
                    case "1y":
                        startDateTime = endDateTime.minusYears(1);
                        break;
                    case "all":
                        startDateTime = LocalDateTime.of(2000, 1, 1, 0, 0); // Far back enough for "all time"
                        break;
                    default:
                        startDateTime = endDateTime.minusDays(30); // Default to 30 days
                }
            }

            long startEpoch = startDateTime.atZone(ZoneId.systemDefault()).toEpochSecond();
            long endEpoch = endDateTime.atZone(ZoneId.systemDefault()).toEpochSecond();

            // Map to store revenue by date (date string -> revenue amount)
            Map<String, Double> revenueByDate = new TreeMap<>(); // TreeMap to keep dates sorted
            int totalChargesProcessed = 0;

            try {
                com.stripe.net.RequestOptions requestOptions = (stripeAccountId != null && !stripeAccountId.isEmpty())
                        ? com.stripe.net.RequestOptions.builder().setStripeAccount(stripeAccountId).build()
                        : null;

                ChargeListParams.Builder paramsBuilder = ChargeListParams.builder()
                        .setCreated(ChargeListParams.Created.builder()
                                .setGte(startEpoch)
                                .setLte(endEpoch)
                                .build())
                        .setLimit(100L);

                ChargeCollection charges = requestOptions != null ? Charge.list(paramsBuilder.build(), requestOptions) : Charge.list(paramsBuilder.build());
                Iterable<Charge> chargesIterable = charges.autoPagingIterable();

                for (Charge charge : chargesIterable) {
                    // Only count successful charges that haven't been refunded
                    if (charge.getPaid() && !charge.getRefunded()) {
                        LocalDateTime chargeDateTime = LocalDateTime
                                .ofEpochSecond(charge.getCreated(), 0, java.time.ZoneOffset.UTC);

                        String dateKey;
                        // For "today" or single day selection, group by hour
                        boolean isSingleDay = startDateTime.toLocalDate().isEqual(endDateTime.toLocalDate());
                        
                        if ("today".equalsIgnoreCase(period) || isSingleDay) {
                            // Format as "HH:00" for hourly grouping
                            dateKey = String.format("%02d:00", chargeDateTime.getHour());
                        } else {
                            // Format as YYYY-MM-DD for daily grouping
                            dateKey = chargeDateTime.toLocalDate().toString();
                        }

                        double amount = charge.getAmount() / 100.0; // Convert cents to dollars
                        revenueByDate.put(dateKey, revenueByDate.getOrDefault(dateKey, 0.0) + amount);

                        totalChargesProcessed++;
                    }
                }

                logger.info("Total charges processed: {}, Total unique dates: {}",
                    totalChargesProcessed, revenueByDate.size());
            } catch (Exception e) {
                logger.error("Error fetching Stripe data for businessId={}: {}", actualBusinessId, e.getMessage(), e);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body(Map.of("error", "Error fetching Stripe data: " + e.getMessage()));
            }

            // For "today" or single day, ensure we have all 24 hours
            boolean isSingleDay = startDateTime.toLocalDate().isEqual(endDateTime.toLocalDate());
            if ("today".equalsIgnoreCase(period) || isSingleDay) {
                Map<String, Double> completeHourlyData = new TreeMap<>();
                for (int hour = 0; hour < 24; hour++) {
                    String hourKey = String.format("%02d:00", hour);
                    completeHourlyData.put(hourKey, revenueByDate.getOrDefault(hourKey, 0.0));
                }
                revenueByDate = completeHourlyData;
            } else {
                // Fill in missing days with 0
                LocalDate curr = startDateTime.toLocalDate();
                LocalDate end = endDateTime.toLocalDate();
                while (!curr.isAfter(end)) {
                    String key = curr.toString();
                    revenueByDate.putIfAbsent(key, 0.0);
                    curr = curr.plusDays(1);
                }
            }

            // Convert map to arrays for response
            List<String> labels = new ArrayList<>(revenueByDate.keySet());
            List<Double> values = new ArrayList<>(revenueByDate.values());

            RevenueChartResponse response = new RevenueChartResponse();
            response.setLabels(labels.toArray(new String[0]));
            response.setValues(values.stream().mapToDouble(Double::doubleValue).toArray());
            response.setPeriod(period);
            response.setStartDate(startDateTime.toString());
            response.setEndDate(endDateTime.toString());

            logger.info("Revenue chart data calculated: {} data points from {} to {}",
                    labels.size(), startDateTime.toLocalDate(), endDateTime.toLocalDate());

            return ResponseEntity.ok(response);
        } catch (RuntimeException e) {
            // Re-throw if it's already a proper HTTP error response
            if (e.getMessage() != null && e.getMessage().contains("Business not found")) {
                logger.error("Business not found for businessId={}, period={}: {}", actualBusinessId, period, e.getMessage());
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("error", e.getMessage()));
            }
            if (e.getMessage() != null && e.getMessage().contains("Stripe account")) {
                logger.error("Stripe account issue for businessId={}, period={}: {}", actualBusinessId, period, e.getMessage());
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(Map.of("error", e.getMessage()));
            }
            logger.error("Error fetching revenue chart for businessId={}, period={}: {}", actualBusinessId, period, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Error fetching revenue chart: " + e.getMessage()));
        } catch (Exception e) {
            logger.error("Unexpected error fetching revenue chart for businessId={}, period={}: {}", actualBusinessId, period, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Unexpected error fetching revenue chart: " + e.getMessage()));
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
     * GET /api/analytics/new-members-chart?businessId={id}&startDate={date}&endDate={date}
     * Returns chart data for new members created per day/hour
     */
    @GetMapping("/new-members-chart")
    @Transactional
    public ResponseEntity<?> getNewMembersChart(
            @RequestParam(required = false) Long businessId,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {
        if (businessId == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", "businessId parameter is required"));
        }

        try {
            Business business = businessRepository.findById(businessId)
                    .orElse(null);

            if (business == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("error", "Business not found"));
            }

            // Calculate date range
            LocalDateTime endDateTime = LocalDateTime.now();
            LocalDateTime startDateTime;

            if (startDate != null && !startDate.isEmpty()) {
                startDateTime = LocalDate.parse(startDate).atStartOfDay();
                if (endDate != null && !endDate.isEmpty()) {
                    endDateTime = LocalDate.parse(endDate).atTime(23, 59, 59);
                }
            } else {
                startDateTime = endDateTime.minusDays(30);
            }

            boolean isSingleDay = startDateTime.toLocalDate().isEqual(endDateTime.toLocalDate());
            Map<String, Long> membersByDate = new TreeMap<>();

            List<UserBusiness> userBusinesses = userBusinessRepository.findByBusinessIdWithMemberships(businessId);
            
            for (UserBusiness userBusiness : userBusinesses) {
                LocalDateTime createdAt = userBusiness.getCreatedAt();
                if (createdAt != null && !createdAt.isBefore(startDateTime) && !createdAt.isAfter(endDateTime)) {
                    String dateKey;
                    if (isSingleDay) {
                        dateKey = String.format("%02d:00", createdAt.getHour());
                    } else {
                        dateKey = createdAt.toLocalDate().toString();
                    }
                    membersByDate.put(dateKey, membersByDate.getOrDefault(dateKey, 0L) + 1);
                }
            }

            // Fill in missing dates/hours with 0
            if (isSingleDay) {
                for (int hour = 0; hour < 24; hour++) {
                    String hourKey = String.format("%02d:00", hour);
                    membersByDate.putIfAbsent(hourKey, 0L);
                }
            } else {
                LocalDate curr = startDateTime.toLocalDate();
                LocalDate end = endDateTime.toLocalDate();
                while (!curr.isAfter(end)) {
                    String key = curr.toString();
                    membersByDate.putIfAbsent(key, 0L);
                    curr = curr.plusDays(1);
                }
            }

            List<String> labels = new ArrayList<>(membersByDate.keySet());
            List<Double> values = new ArrayList<>(membersByDate.values().stream().map(Long::doubleValue).toList());

            RevenueChartResponse response = new RevenueChartResponse();
            response.setLabels(labels.toArray(new String[0]));
            response.setValues(values.stream().mapToDouble(Double::doubleValue).toArray());
            response.setPeriod(isSingleDay ? "today" : "custom");
            response.setStartDate(startDateTime.toString());
            response.setEndDate(endDateTime.toString());

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error fetching new members chart: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Error fetching new members chart: " + e.getMessage()));
        }
    }

    /**
     * GET /api/analytics/total-members-chart?businessId={id}&startDate={date}&endDate={date}
     * Returns chart data for total active members per day/hour
     */
    @GetMapping("/total-members-chart")
    @Transactional
    public ResponseEntity<?> getTotalMembersChart(
            @RequestParam(required = false) Long businessId,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {
        if (businessId == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", "businessId parameter is required"));
        }

        try {
            Business business = businessRepository.findById(businessId)
                    .orElse(null);

            if (business == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("error", "Business not found"));
            }

            // Calculate date range
            LocalDateTime endDateTime = LocalDateTime.now();
            LocalDateTime startDateTime;

            if (startDate != null && !startDate.isEmpty()) {
                startDateTime = LocalDate.parse(startDate).atStartOfDay();
                if (endDate != null && !endDate.isEmpty()) {
                    endDateTime = LocalDate.parse(endDate).atTime(23, 59, 59);
                }
            } else {
                startDateTime = endDateTime.minusDays(30);
            }

            boolean isSingleDay = startDateTime.toLocalDate().isEqual(endDateTime.toLocalDate());
            Map<String, Long> membersByDate = new TreeMap<>();

            List<UserBusiness> userBusinesses = userBusinessRepository.findByBusinessIdWithMemberships(businessId);
            
            // For each date/hour in range, count members who existed at that point
            if (isSingleDay) {
                for (int hour = 0; hour < 24; hour++) {
                    String hourKey = String.format("%02d:00", hour);
                    LocalDateTime checkTime = startDateTime.toLocalDate().atTime(hour, 0);
                    long count = userBusinesses.stream()
                            .filter(ub -> ub.getCreatedAt() != null && !ub.getCreatedAt().isAfter(checkTime))
                            .count();
                    membersByDate.put(hourKey, count);
                }
            } else {
                LocalDate curr = startDateTime.toLocalDate();
                LocalDate end = endDateTime.toLocalDate();
                while (!curr.isAfter(end)) {
                    String key = curr.toString();
                    LocalDateTime checkTime = curr.atTime(23, 59, 59);
                    long count = userBusinesses.stream()
                            .filter(ub -> ub.getCreatedAt() != null && !ub.getCreatedAt().isAfter(checkTime))
                            .count();
                    membersByDate.put(key, count);
                    curr = curr.plusDays(1);
                }
            }

            List<String> labels = new ArrayList<>(membersByDate.keySet());
            List<Double> values = new ArrayList<>(membersByDate.values().stream().map(Long::doubleValue).toList());

            RevenueChartResponse response = new RevenueChartResponse();
            response.setLabels(labels.toArray(new String[0]));
            response.setValues(values.stream().mapToDouble(Double::doubleValue).toArray());
            response.setPeriod(isSingleDay ? "today" : "custom");
            response.setStartDate(startDateTime.toString());
            response.setEndDate(endDateTime.toString());

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error fetching total members chart: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Error fetching total members chart: " + e.getMessage()));
        }
    }

    /**
     * GET /api/analytics/mrr-chart?businessId={id}&startDate={date}&endDate={date}
     * Returns chart data for Monthly Recurring Revenue per day/hour
     */
    @GetMapping("/mrr-chart")
    @Transactional
    public ResponseEntity<?> getMrrChart(
            @RequestParam(required = false) Long businessId,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {
        if (businessId == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", "businessId parameter is required"));
        }

        try {
            Business business = businessRepository.findById(businessId)
                    .orElse(null);

            if (business == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("error", "Business not found"));
            }

            String stripeAccountId = null; // Single-tenant: platform account only

            LocalDateTime endDateTime = LocalDateTime.now();
            LocalDateTime startDateTime;

            if (startDate != null && !startDate.isEmpty()) {
                startDateTime = LocalDate.parse(startDate).atStartOfDay();
                if (endDate != null && !endDate.isEmpty()) {
                    endDateTime = LocalDate.parse(endDate).atTime(23, 59, 59);
                }
            } else {
                startDateTime = endDateTime.minusDays(30);
            }

            boolean isSingleDay = startDateTime.toLocalDate().isEqual(endDateTime.toLocalDate());
            Map<String, Double> mrrByDate = new TreeMap<>();

            com.stripe.net.RequestOptions requestOptions = (stripeAccountId != null && !stripeAccountId.isEmpty())
                    ? com.stripe.net.RequestOptions.builder().setStripeAccount(stripeAccountId).build()
                    : null;

            SubscriptionListParams.Builder paramsBuilder = SubscriptionListParams.builder()
                    .setLimit(100L);

            SubscriptionCollection subscriptions = requestOptions != null ? Subscription.list(paramsBuilder.build(), requestOptions) : Subscription.list(paramsBuilder.build());
            Iterable<Subscription> subscriptionsIterable = subscriptions.autoPagingIterable();

            // Collect all subscriptions with their monthly amounts and dates
            List<SubscriptionData> subscriptionDataList = new ArrayList<>();
            for (Subscription subscription : subscriptionsIterable) {
                if (subscription.getItems() != null && subscription.getItems().getData() != null) {
                    for (SubscriptionItem item : subscription.getItems().getData()) {
                        if (item.getPrice() != null && item.getPrice().getRecurring() != null) {
                            String interval = item.getPrice().getRecurring().getInterval();
                            Long unitAmount = item.getPrice().getUnitAmount();
                            
                            if (unitAmount != null && "month".equals(interval)) {
                                double monthlyAmount = unitAmount / 100.0;
                                LocalDateTime subCreated = LocalDateTime.ofEpochSecond(
                                        subscription.getCreated(), 0, java.time.ZoneOffset.UTC);
                                LocalDateTime subEnded = subscription.getCanceledAt() != null
                                        ? LocalDateTime.ofEpochSecond(subscription.getCanceledAt(), 0, java.time.ZoneOffset.UTC)
                                        : null;
                                String status = subscription.getStatus();
                                
                                subscriptionDataList.add(new SubscriptionData(monthlyAmount, subCreated, subEnded, status));
                            }
                        }
                    }
                }
            }

            // Calculate MRR for each date/hour in range
            if (isSingleDay) {
                for (int hour = 0; hour < 24; hour++) {
                    String hourKey = String.format("%02d:00", hour);
                    LocalDateTime checkTime = startDateTime.toLocalDate().atTime(hour, 0);
                    double mrr = subscriptionDataList.stream()
                            .filter(sub -> {
                                boolean created = !sub.createdAt.isAfter(checkTime);
                                boolean notEnded = sub.endedAt == null || !sub.endedAt.isBefore(checkTime);
                                boolean active = "active".equals(sub.status) || "trialing".equals(sub.status) || "past_due".equals(sub.status);
                                return created && notEnded && active;
                            })
                            .mapToDouble(sub -> sub.monthlyAmount)
                            .sum();
                    mrrByDate.put(hourKey, mrr);
                }
            } else {
                LocalDate curr = startDateTime.toLocalDate();
                LocalDate end = endDateTime.toLocalDate();
                while (!curr.isAfter(end)) {
                    String key = curr.toString();
                    LocalDateTime checkTime = curr.atTime(23, 59, 59);
                    double mrr = subscriptionDataList.stream()
                            .filter(sub -> {
                                boolean created = !sub.createdAt.isAfter(checkTime);
                                boolean notEnded = sub.endedAt == null || !sub.endedAt.isBefore(checkTime);
                                boolean active = "active".equals(sub.status) || "trialing".equals(sub.status) || "past_due".equals(sub.status);
                                return created && notEnded && active;
                            })
                            .mapToDouble(sub -> sub.monthlyAmount)
                            .sum();
                    mrrByDate.put(key, mrr);
                    curr = curr.plusDays(1);
                }
            }

            List<String> labels = new ArrayList<>(mrrByDate.keySet());
            List<Double> values = new ArrayList<>(mrrByDate.values());

            RevenueChartResponse response = new RevenueChartResponse();
            response.setLabels(labels.toArray(new String[0]));
            response.setValues(values.stream().mapToDouble(Double::doubleValue).toArray());
            response.setPeriod(isSingleDay ? "today" : "custom");
            response.setStartDate(startDateTime.toString());
            response.setEndDate(endDateTime.toString());

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error fetching MRR chart: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Error fetching MRR chart: " + e.getMessage()));
        }
    }

    // Helper class for MRR calculation
    private static class SubscriptionData {
        double monthlyAmount;
        LocalDateTime createdAt;
        LocalDateTime endedAt;
        String status;

        SubscriptionData(double monthlyAmount, LocalDateTime createdAt, LocalDateTime endedAt, String status) {
            this.monthlyAmount = monthlyAmount;
            this.createdAt = createdAt;
            this.endedAt = endedAt;
            this.status = status;
        }
    }

    /**
     * Get available balance for payouts from Stripe
     * GET /api/analytics/balance?businessId={id}
     */
    @GetMapping("/balance")
    @Transactional
    public BalanceResponse getBalance(
            @RequestParam(required = false) Long businessId,
            @RequestParam(required = false) Long clubId) { // Backward compatibility
        Long actualBusinessId = businessId != null ? businessId : clubId;
        if (actualBusinessId == null) {
            throw new RuntimeException("businessId parameter is required");
        }
        try {
            // Get the business
            Business business = businessRepository.findById(actualBusinessId)
                    .orElseThrow(() -> new RuntimeException("Business not found with id: " + actualBusinessId));

            String stripeAccountId = null; // Single-tenant: platform account only

            com.stripe.net.RequestOptions requestOptions = (stripeAccountId != null && !stripeAccountId.isEmpty())
                    ? com.stripe.net.RequestOptions.builder().setStripeAccount(stripeAccountId).build()
                    : null;

            com.stripe.model.Balance balance = requestOptions != null ? com.stripe.model.Balance.retrieve(requestOptions) : com.stripe.model.Balance.retrieve();

            // Calculate total available balance across all currencies
            double totalAvailable = 0.0;
            double totalPending = 0.0;
            String currency = "usd"; // Default

            for (int i = 0; i < balance.getAvailable().size(); i++) {
                var money = balance.getAvailable().get(i);
                totalAvailable += money.getAmount() / 100.0;
                currency = money.getCurrency() != null ? String.valueOf(money.getCurrency()) : "usd";
            }

            // Calculate total pending from balance
            for (int i = 0; i < balance.getPending().size(); i++) {
                var money = balance.getPending().get(i);
                totalPending += money.getAmount() / 100.0;
            }

            // Fetch pending balance transactions to get dates
            // Transactions that have available_on in the future are pending
            java.util.Map<String, Double> pendingByDate = new java.util.HashMap<>();
            long currentTime = java.time.Instant.now().getEpochSecond();
            
            try {
                com.stripe.param.BalanceTransactionListParams params = com.stripe.param.BalanceTransactionListParams.builder()
                        .setLimit(100L)
                        .build();
                
                com.stripe.model.BalanceTransactionCollection transactions = requestOptions != null ? com.stripe.model.BalanceTransaction.list(params, requestOptions) : com.stripe.model.BalanceTransaction.list(params);
                
                for (com.stripe.model.BalanceTransaction transaction : transactions.getData()) {
                    Long availableOn = transaction.getAvailableOn();
                    if (availableOn != null && availableOn > currentTime) {
                        // This transaction is pending (available in the future)
                        java.time.LocalDate availableDate = java.time.Instant.ofEpochSecond(availableOn)
                                .atZone(java.time.ZoneId.systemDefault())
                                .toLocalDate();
                        String dateKey = availableDate.toString();
                        double amount = transaction.getAmount() / 100.0;
                        // Only count positive amounts (credits)
                        if (amount > 0) {
                            pendingByDate.merge(dateKey, amount, (a, b) -> a + b);
                        }
                    }
                }
            } catch (Exception e) {
                logger.warn("Error fetching pending transactions by date, continuing without date breakdown: {}", e.getMessage());
            }

            BalanceResponse response = new BalanceResponse();
            response.setAvailable(totalAvailable);
            response.setPending(totalPending);
            response.setCurrency(currency);
            response.setPendingByDate(pendingByDate);

            return response;
        } catch (Exception e) {
            logger.error("Error fetching balance for businessId={}: {}", actualBusinessId, e.getMessage(), e);
            throw new RuntimeException("Error fetching balance: " + e.getMessage());
        }
    }

    public static class BalanceResponse {
        private double available;
        private double pending;
        private String currency;
        private java.util.Map<String, Double> pendingByDate;

        public double getAvailable() { return available; }
        public void setAvailable(double available) { this.available = available; }
        public double getPending() { return pending; }
        public void setPending(double pending) { this.pending = pending; }
        public String getCurrency() { return currency; }
        public void setCurrency(String currency) { this.currency = currency; }
        public java.util.Map<String, Double> getPendingByDate() { return pendingByDate; }
        public void setPendingByDate(java.util.Map<String, Double> pendingByDate) { this.pendingByDate = pendingByDate; }
    }

    /**
     * Create a payout to transfer funds to the connected account's bank
     * POST /api/analytics/payout?clubId={id}&amount={amount}
     */
    @org.springframework.web.bind.annotation.PostMapping("/payout")
    @Transactional
    public PayoutResponse createPayout(
            @RequestParam(required = false) Long businessId,
            @RequestParam(required = false) Long clubId, // Backward compatibility
            @RequestParam(required = false) Double amount) {
        Long actualBusinessId = businessId != null ? businessId : clubId;
        if (actualBusinessId == null) {
            throw new RuntimeException("businessId parameter is required");
        }
        try {
            logger.info("Creating payout for businessId={}, amount={}", actualBusinessId, amount);

            // Get the business
            Business business = businessRepository.findById(actualBusinessId)
                    .orElseThrow(() -> new RuntimeException("Business not found with id: " + actualBusinessId));

            String stripeAccountId = null; // Single-tenant: platform account only

            com.stripe.net.RequestOptions requestOptions = (stripeAccountId != null && !stripeAccountId.isEmpty())
                    ? com.stripe.net.RequestOptions.builder().setStripeAccount(stripeAccountId).build()
                    : null;

            com.stripe.model.Balance balance = requestOptions != null ? com.stripe.model.Balance.retrieve(requestOptions) : com.stripe.model.Balance.retrieve();

            double availableBalance = 0.0;
            String currency = "usd";

            for (int i = 0; i < balance.getAvailable().size(); i++) {
                var money = balance.getAvailable().get(i);
                availableBalance += money.getAmount() / 100.0;
                currency = money.getCurrency() != null ? String.valueOf(money.getCurrency()) : "usd";
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
                    .setDescription("Payout for business " + business.getTitle())
                    .build();

            com.stripe.model.Payout payout = requestOptions != null ? com.stripe.model.Payout.create(params, requestOptions) : com.stripe.model.Payout.create(params);

            PayoutResponse response = new PayoutResponse();
            response.setPayoutId(payout.getId());
            response.setAmount(payoutAmount);
            response.setCurrency(currency);
            response.setStatus(payout.getStatus());
            response.setArrivalDate(payout.getArrivalDate() != null ?
                    java.time.LocalDateTime.ofEpochSecond(payout.getArrivalDate(), 0, java.time.ZoneOffset.UTC).toLocalDate().toString() :
                    null);
            response.setDescription(payout.getDescription());

            logger.info("Payout created successfully for businessId={}: payoutId={}, amount=${}",
                    actualBusinessId, payout.getId(), payoutAmount);

            return response;
        } catch (Exception e) {
            logger.error("Error creating payout for businessId={}: {}", actualBusinessId, e.getMessage(), e);
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
     * Get recent activities for a business
     * GET /api/analytics/recent-activity?businessId={id}
     */
    @GetMapping("/recent-activity")
    @Transactional
    public List<RecentActivityResponse> getRecentActivity(
            @RequestParam(required = false) Long businessId,
            @RequestParam(required = false) Long clubId) { // Backward compatibility
        Long actualBusinessId = businessId != null ? businessId : clubId;
        if (actualBusinessId == null) {
            throw new RuntimeException("businessId parameter is required");
        }
        try {
            // Get activities from last 2 days
            LocalDateTime twoDaysAgo = LocalDateTime.now().minusDays(2);
            List<RecentActivity> activities = recentActivityRepository.findRecentByBusinessId(actualBusinessId, twoDaysAgo);

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

            logger.info("Found {} recent activities for businessId={}", response.size(), actualBusinessId);
            return response;
        } catch (Exception e) {
            logger.error("Error fetching recent activity for businessId={}: {}", actualBusinessId, e.getMessage(), e);
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

    /**
     * Get recent activity for overview page (payments, failed payments, new members)
     * GET /api/analytics/overview-activity?businessId={id}
     */
    @GetMapping("/overview-activity")
    @Transactional
    public ResponseEntity<?> getOverviewActivity(@RequestParam Long businessId) {
        try {
            // Validate business exists
            Business business = businessRepository.findById(businessId)
                    .orElseThrow(() -> new RuntimeException("Business not found with id: " + businessId));
            
            // TODO: Add business scope validation for staff after verifying staff business loading works
            // For now, we allow all authenticated users to access any business
            // This will be secured once we verify the staff business relationship is properly loaded
            
            String stripeAccountId = null; // Single-tenant: platform account only
            List<Map<String, Object>> activities = new ArrayList<>();

            try {
                    LocalDateTime sevenDaysAgo = LocalDateTime.now().minusDays(7);
                    long sevenDaysAgoEpoch = sevenDaysAgo.atZone(ZoneId.systemDefault()).toEpochSecond();

                    com.stripe.net.RequestOptions requestOptions = (stripeAccountId != null && !stripeAccountId.isEmpty())
                            ? com.stripe.net.RequestOptions.builder().setStripeAccount(stripeAccountId).build()
                            : null;

                    ChargeListParams chargeParams = ChargeListParams.builder()
                            .setLimit(50L)
                            .build();

                    com.stripe.model.ChargeCollection chargeColl = requestOptions != null ? Charge.list(chargeParams, requestOptions) : Charge.list(chargeParams);
                    for (Charge charge : chargeColl.autoPagingIterable()) {
                        if (charge.getCreated() < sevenDaysAgoEpoch) break;
                        if ("succeeded".equals(charge.getStatus()) && charge.getPaid()) {
                            Map<String, Object> activity = new HashMap<>();
                            activity.put("type", "PAYMENT");
                            activity.put("icon", "DollarSign");
                            String customerName = charge.getBillingDetails() != null && charge.getBillingDetails().getName() != null
                                    ? charge.getBillingDetails().getName()
                                    : "Customer";
                            activity.put("text", "Payment received from " + customerName);
                            activity.put("amount", charge.getAmount() / 100.0);
                            activity.put("time", formatTimeAgo(charge.getCreated()));
                            activity.put("timestamp", charge.getCreated());
                            activities.add(activity);
                        }
                    }

                    // Get failed payments (OPEN and UNCOLLECTIBLE invoices)
                    InvoiceListParams openParams = InvoiceListParams.builder()
                            .setStatus(InvoiceListParams.Status.OPEN)
                            .setLimit(50L)
                            .build();

                    com.stripe.model.InvoiceCollection openInvoices = requestOptions != null ? Invoice.list(openParams, requestOptions) : Invoice.list(openParams);
                    for (Invoice invoice : openInvoices.autoPagingIterable()) {
                        if (invoice.getCreated() < sevenDaysAgoEpoch) break;
                        Map<String, Object> activity = new HashMap<>();
                        activity.put("type", "FAILED_PAYMENT");
                        activity.put("icon", "AlertCircle");
                        String customerName = invoice.getCustomerName() != null ? invoice.getCustomerName() : "Customer";
                        activity.put("text", "Failed payment from " + customerName);
                        activity.put("amount", invoice.getAmountDue() / 100.0);
                        activity.put("time", formatTimeAgo(invoice.getCreated()));
                        activity.put("timestamp", invoice.getCreated());
                        activities.add(activity);
                    }

                    InvoiceListParams uncollectibleParams = InvoiceListParams.builder()
                            .setStatus(InvoiceListParams.Status.UNCOLLECTIBLE)
                            .setLimit(50L)
                            .build();

                    com.stripe.model.InvoiceCollection uncollectibleInvoices = requestOptions != null ? Invoice.list(uncollectibleParams, requestOptions) : Invoice.list(uncollectibleParams);
                    for (Invoice invoice : uncollectibleInvoices.autoPagingIterable()) {
                        if (invoice.getCreated() < sevenDaysAgoEpoch) break;
                        Map<String, Object> activity = new HashMap<>();
                        activity.put("type", "FAILED_PAYMENT");
                        activity.put("icon", "AlertCircle");
                        String customerName = invoice.getCustomerName() != null ? invoice.getCustomerName() : "Customer";
                        activity.put("text", "Failed payment from " + customerName);
                        activity.put("amount", invoice.getAmountDue() / 100.0);
                        activity.put("time", formatTimeAgo(invoice.getCreated()));
                        activity.put("timestamp", invoice.getCreated());
                        activities.add(activity);
                    }
                } catch (Exception e) {
                    logger.error("Error fetching Stripe data for recent activity: {}", e.getMessage(), e);
                }

            LocalDateTime sevenDaysAgo = LocalDateTime.now().minusDays(7);
            List<UserBusiness> newMembers = userBusinessRepository.findByBusinessIdWithMemberships(businessId).stream()
                    .filter(ub -> ub.getCreatedAt() != null && ub.getCreatedAt().isAfter(sevenDaysAgo))
                    .sorted((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()))
                    .limit(20)
                    .collect(java.util.stream.Collectors.toList());

            for (UserBusiness userBusiness : newMembers) {
                Map<String, Object> activity = new HashMap<>();
                activity.put("type", "NEW_MEMBER");
                activity.put("icon", "UserPlus");
                String memberName = userBusiness.getUser().getFirstName() + " " + userBusiness.getUser().getLastName();
                activity.put("text", "New member " + memberName + " joined");
                activity.put("time", formatTimeAgo(userBusiness.getCreatedAt().atZone(ZoneId.systemDefault()).toEpochSecond()));
                activity.put("timestamp", userBusiness.getCreatedAt().atZone(ZoneId.systemDefault()).toEpochSecond());
                activities.add(activity);
            }

            // Sort by timestamp (most recent first) and limit to 20
            activities.sort((a, b) -> {
                long timeA = (long) a.get("timestamp");
                long timeB = (long) b.get("timestamp");
                return Long.compare(timeB, timeA);
            });

            return ResponseEntity.ok(activities.stream().limit(20).collect(java.util.stream.Collectors.toList()));

        } catch (Exception e) {
            logger.error("Error fetching overview activity for businessId={}: {}", businessId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to fetch overview activity: " + e.getMessage()));
        }
    }

    private String formatTimeAgo(long timestamp) {
        long now = System.currentTimeMillis() / 1000;
        long diff = now - timestamp;

        if (diff < 60) return "just now";
        if (diff < 3600) return (diff / 60) + " minutes ago";
        if (diff < 86400) return (diff / 3600) + " hours ago";
        return (diff / 86400) + " days ago";
    }

    private double getLockedPrice(UserBusinessMembership membership) {
        if (membership.getActualPrice() != null) {
            return membership.getActualPrice().doubleValue();
        }
        Membership membershipType = membership.getMembership();
        if (membershipType != null) {
            return parsePlanPrice(membershipType.getPrice());
        }
        return 0.0;
    }

    private double parsePlanPrice(String price) {
        if (price == null || price.trim().isEmpty()) {
            return 0.0;
        }
        try {
            String normalized = price.replaceAll("[^0-9.]", "");
            if (normalized.isEmpty()) {
                return 0.0;
            }
            return Double.parseDouble(normalized);
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }

    private double roundTwo(double value) {
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP).doubleValue();
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