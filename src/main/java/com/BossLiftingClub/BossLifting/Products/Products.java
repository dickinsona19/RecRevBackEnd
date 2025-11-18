package com.BossLiftingClub.BossLifting.Products;

import com.BossLiftingClub.BossLifting.Business.Business;
import com.fasterxml.jackson.annotation.JsonBackReference;
import jakarta.persistence.*;

@Entity
@Table(name = "products")
public class Products {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column
    private String name;

    @Column
    private String definition;

    @Column
    private Double price;

    @Column(name = "image_url")
    private String imageUrl;

    @Column
    private String category;

    @Column(name = "stripe_product_id")
    private String stripeProductId;

    @Column(name = "business_tag")
    private String businessTag;
    
    // Backward compatibility - map club_tag to business_tag
    @Deprecated
    @Column(name = "club_tag", insertable = false, updatable = false)
    private String clubTag;


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

    public String getBusinessTag() {
        return businessTag;
    }

    public void setBusinessTag(String businessTag) {
        this.businessTag = businessTag;
    }
    
    // Backward compatibility getters/setters
    @Deprecated
    public String getClubTag() {
        return businessTag;
    }

    @Deprecated
    public void setClubTag(String clubTag) {
        this.businessTag = clubTag;
    }
}