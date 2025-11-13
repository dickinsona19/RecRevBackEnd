package com.BossLiftingClub.BossLifting.User.Membership;

import org.springframework.beans.factory.annotation.Value;
import com.stripe.Stripe;
import com.stripe.model.Price;
import com.stripe.model.Product;
import com.stripe.param.PriceCreateParams;
import com.stripe.param.ProductCreateParams;
import com.stripe.net.RequestOptions;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;
import com.BossLiftingClub.BossLifting.Club.Club;
import com.BossLiftingClub.BossLifting.Club.ClubRepository;

@Service
public class MembershipServiceImpl implements MembershipService {
    private static final Logger logger = LoggerFactory.getLogger(MembershipServiceImpl.class);

    @Value("${stripe.secret.key}")
    private String stripeApiKey;

    @Autowired
    private MembershipRepository membershipRepository;

    @Autowired
    private ClubRepository clubRepository;

    @PostConstruct
    public void initStripe() {
        Stripe.apiKey = stripeApiKey;
    }

    public MembershipServiceImpl(MembershipRepository membershipRepository, ClubRepository clubRepository) {
        this.membershipRepository = membershipRepository;
        this.clubRepository = clubRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public List<MembershipDTO> getMembershipsByClubTag(String clubTag) {
        logger.debug("Fetching memberships for clubTag: {}", clubTag);
        List<Membership> memberships = membershipRepository.findByClubTag(clubTag);
        return memberships.stream()
                .map(this::mapToDTO)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public Membership createMembershipWithStripe(Membership membership) {
        // Fetch the Club by clubTag to get the associated Client
        Club club = clubRepository.findByClubTag(membership.getClubTag())
                .orElseThrow(() -> new RuntimeException("Club not found for clubTag: " + membership.getClubTag()));

        if (!"COMPLETED".equals(club.getOnboardingStatus())) {
            throw new IllegalStateException("Stripe integration not complete. Please complete Stripe onboarding first.");
        }

        String stripeAccountId = club.getStripeAccountId();
        if (stripeAccountId == null) {
            throw new RuntimeException("Client or Stripe account ID not found for clubTag: " + membership.getClubTag());
        }

        // Create Stripe Product and Price using the club's Stripe account ID
        String stripePriceId = createStripePriceForMembership(membership, stripeAccountId);
        membership.setStripePriceId(stripePriceId);
        return membershipRepository.save(membership);
    }

    @Override
    @Transactional
    public Membership archiveMembership(Long id) {
        Membership membership = membershipRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Membership not found"));
        membership.setArchived(true);
        return membershipRepository.save(membership);
    }

    private String createStripePriceForMembership(Membership membership, String stripeAccountId) {
        try {
            // Convert price string to cents (e.g., "49.99" -> 4999)
            long unitAmount = (long) (Double.parseDouble(membership.getPrice()) * 100);

            // 1. Create Stripe Product on the client's connected account
            ProductCreateParams productParams = ProductCreateParams.builder()
                    .setName(membership.getTitle())
                    .setDescription("Membership plan for club: " + membership.getClubTag())
                    .build();

            RequestOptions requestOptions = RequestOptions.builder()
                    .setStripeAccount(stripeAccountId)
                    .build();

            Product product = Product.create(productParams, requestOptions);

            // 2. Create Stripe Price on the client's connected account
            PriceCreateParams priceParams = PriceCreateParams.builder()
                    .setUnitAmount(unitAmount)
                    .setCurrency("usd")
                    .setRecurring(
                            PriceCreateParams.Recurring.builder()
                                    .setInterval(resolveInterval(membership.getChargeInterval()))
                                    .build()
                    )
                    .setProduct(product.getId())
                    .build();

            Price price = Price.create(priceParams, requestOptions);

            // 3. Return Stripe Price ID
            return price.getId();

        } catch (Exception e) {
            throw new RuntimeException("Stripe error for account " + stripeAccountId + ": " + e.getMessage(), e);
        }
    }

    private PriceCreateParams.Recurring.Interval resolveInterval(String input) {
        return switch (input.trim().toLowerCase()) {
            case "day", "daily" -> PriceCreateParams.Recurring.Interval.DAY;
            case "week", "weekly" -> PriceCreateParams.Recurring.Interval.WEEK;
            case "month", "monthly" -> PriceCreateParams.Recurring.Interval.MONTH;
            case "year", "annual", "yearly" -> PriceCreateParams.Recurring.Interval.YEAR;
            default -> throw new IllegalArgumentException("Unsupported charge interval: " + input);
        };
    }

    private MembershipDTO mapToDTO(Membership membership) {
        MembershipDTO dto = new MembershipDTO();
        dto.setId(membership.getId());
        dto.setTitle(membership.getTitle());
        dto.setPrice(membership.getPrice());
        dto.setChargeInterval(membership.getChargeInterval());
        dto.setClubTag(membership.getClubTag());
        return dto;
    }
}