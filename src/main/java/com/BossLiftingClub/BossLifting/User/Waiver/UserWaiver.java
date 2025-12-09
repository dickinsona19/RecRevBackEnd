package com.BossLiftingClub.BossLifting.User.Waiver;

import com.BossLiftingClub.BossLifting.Business.Business;
import com.BossLiftingClub.BossLifting.User.User;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "user_waiver",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_user_template", columnNames = {"user_id", "waiver_template_id"})
        })
public class UserWaiver {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    @JsonIgnore
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "business_id")
    private Business business;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "waiver_template_id")
    private WaiverTemplate waiverTemplate;

    @Column(name = "signed_at", nullable = false)
    private LocalDateTime signedAt;

    @Column(name = "signature_image_url", length = 500)
    private String signatureImageUrl;

    @Column(name = "final_pdf_url", length = 500)
    private String finalPdfUrl;

    @Column(name = "signer_ip", length = 64)
    private String signerIp;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }

    public Long getId() {
        return id;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public Business getBusiness() {
        return business;
    }

    public void setBusiness(Business business) {
        this.business = business;
    }

    public WaiverTemplate getWaiverTemplate() {
        return waiverTemplate;
    }

    public void setWaiverTemplate(WaiverTemplate waiverTemplate) {
        this.waiverTemplate = waiverTemplate;
    }

    public LocalDateTime getSignedAt() {
        return signedAt;
    }

    public void setSignedAt(LocalDateTime signedAt) {
        this.signedAt = signedAt;
    }

    public String getSignatureImageUrl() {
        return signatureImageUrl;
    }

    public void setSignatureImageUrl(String signatureImageUrl) {
        this.signatureImageUrl = signatureImageUrl;
    }

    public String getFinalPdfUrl() {
        return finalPdfUrl;
    }

    public void setFinalPdfUrl(String finalPdfUrl) {
        this.finalPdfUrl = finalPdfUrl;
    }

    public String getSignerIp() {
        return signerIp;
    }

    public void setSignerIp(String signerIp) {
        this.signerIp = signerIp;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}

