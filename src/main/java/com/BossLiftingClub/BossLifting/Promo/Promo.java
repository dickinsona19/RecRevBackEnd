package com.BossLiftingClub.BossLifting.Promo;

import com.BossLiftingClub.BossLifting.Business.Business;
import com.BossLiftingClub.BossLifting.User.User;
import jakarta.persistence.*;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "promo")
public class Promo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name")
    private String name;

    @Column(name = "code_token", unique = true)
    private String codeToken;

    @ManyToOne
    @JoinColumn(name = "business_id")
    private Business business;

    @Column(name = "stripe_promo_code_id")
    private String stripePromoCodeId;

    @Column(name = "stripe_coupon_id")
    private String stripeCouponId;

    @Column(name = "discount_value")
    private Double discountValue;

    @Column(name = "discount_type")
    private String discountType; // 'percent' or 'amount'

    @Column(name = "duration")
    private String duration; // 'forever', 'once', 'repeating'

    @Column(name = "duration_in_months")
    private Integer durationInMonths;

    @ManyToMany
    @JoinTable(
            name = "promo_users",
            joinColumns = @JoinColumn(name = "promo_id"),
            inverseJoinColumns = @JoinColumn(name = "user_id")
    )
    private List<User> users = new ArrayList<>();

    @Column(name = "free_pass_count")
    private Integer freePassCount = 0;

    @Column(name = "url_visit_count")
    private Integer urlVisitCount = 0;

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getCodeToken() {
        return codeToken;
    }

    public void setCodeToken(String codeToken) {
        this.codeToken = codeToken;
    }

    public Business getBusiness() {
        return business;
    }

    public void setBusiness(Business business) {
        this.business = business;
    }

    public String getStripePromoCodeId() {
        return stripePromoCodeId;
    }

    public void setStripePromoCodeId(String stripePromoCodeId) {
        this.stripePromoCodeId = stripePromoCodeId;
    }

    public String getStripeCouponId() {
        return stripeCouponId;
    }

    public void setStripeCouponId(String stripeCouponId) {
        this.stripeCouponId = stripeCouponId;
    }

    public Double getDiscountValue() {
        return discountValue;
    }

    public void setDiscountValue(Double discountValue) {
        this.discountValue = discountValue;
    }

    public String getDiscountType() {
        return discountType;
    }

    public void setDiscountType(String discountType) {
        this.discountType = discountType;
    }

    public String getDuration() {
        return duration;
    }

    public void setDuration(String duration) {
        this.duration = duration;
    }

    public Integer getDurationInMonths() {
        return durationInMonths;
    }

    public void setDurationInMonths(Integer durationInMonths) {
        this.durationInMonths = durationInMonths;
    }

    public List<User> getUsers() {
        return users;
    }

    public void setUsers(List<User> users) {
        this.users = users;
    }

    public Integer getFreePassCount() {
        return freePassCount;
    }

    public void setFreePassCount(Integer freePassCount) {
        this.freePassCount = freePassCount;
    }

    public Integer getUrlVisitCount() {
        return urlVisitCount;
    }

    public void setUrlVisitCount(Integer urlVisitCount) {
        this.urlVisitCount = urlVisitCount;
    }
}
