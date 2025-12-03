package com.BossLiftingClub.BossLifting.Promo;

import com.BossLiftingClub.BossLifting.User.UserDTO;
import java.util.List;

public class PromoDTO {
    private Long id;
    private String name;
    private String codeToken;
    private List<UserDTO> users; // Include full UserDTO objects instead of just IDs
    private Integer freePassCount;
    private Integer urlVisitCount;
    private String businessTag;
    private Double discountValue;
    private String discountType;
    private String duration;
    private Integer durationInMonths;

    // Constructor
    public PromoDTO(Long id, String name, String codeToken, List<UserDTO> users, Integer freePassCount, Integer urlVisitCount,
                    String businessTag, Double discountValue, String discountType, String duration, Integer durationInMonths) {
        this.id = id;
        this.name = name;
        this.codeToken = codeToken;
        this.users = users;
        this.freePassCount = freePassCount;
        this.urlVisitCount = urlVisitCount;
        this.businessTag = businessTag;
        this.discountValue = discountValue;
        this.discountType = discountType;
        this.duration = duration;
        this.durationInMonths = durationInMonths;
    }

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

    public List<UserDTO> getUsers() {
        return users;
    }

    public void setUsers(List<UserDTO> users) {
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

    public String getBusinessTag() {
        return businessTag;
    }

    public void setBusinessTag(String businessTag) {
        this.businessTag = businessTag;
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
}
