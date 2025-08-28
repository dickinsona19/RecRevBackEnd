package com.BossLiftingClub.BossLifting.Products;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProductsDTO {
    private Long id;
    private String name;
    private String definition;
    private Double price;
    private String imageUrl;
    private String category;
    private String stripeProductId;
    private String clubTag;

    // Static mapper to convert from entity to DTO
    public static ProductsDTO fromEntity(Products product) {
        ProductsDTO dto = new ProductsDTO();
        dto.setId(product.getId());
        dto.setName(product.getName());
        dto.setDefinition(product.getDefinition());
        dto.setPrice(product.getPrice());
        dto.setImageUrl(product.getImageUrl());
        dto.setCategory(product.getCategory());
        dto.setStripeProductId(product.getStripeProductId());
        dto.setClubTag(product.getClubTag());
        return dto;
    }


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

    public String getDefinition() {
        return definition;
    }

    public void setDefinition(String definition) {
        this.definition = definition;
    }

    public Double getPrice() {
        return price;
    }

    public void setPrice(Double price) {
        this.price = price;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getStripeProductId() {
        return stripeProductId;
    }

    public void setStripeProductId(String stripeProductId) {
        this.stripeProductId = stripeProductId;
    }

    public String getClubTag() {
        return clubTag;
    }

    public void setClubTag(String clubTag) {
        this.clubTag = clubTag;
    }
}