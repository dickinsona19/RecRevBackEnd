package com.BossLiftingClub.BossLifting.Club;


import com.BossLiftingClub.BossLifting.Client.Client;
import com.BossLiftingClub.BossLifting.Club.Staff.Staff;
import com.BossLiftingClub.BossLifting.Products.Products;
import com.BossLiftingClub.BossLifting.User.Membership.Membership;
import com.BossLiftingClub.BossLifting.User.User;
import com.fasterxml.jackson.annotation.JsonBackReference;
import com.fasterxml.jackson.annotation.JsonManagedReference;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Entity
@Table(name = "clubs")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Club {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column
    private String title;

    @Column(name = "logo_url")
    private String logoUrl;

    @Column
    private String status;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "club_tag")
    private String clubTag;

    @ManyToOne
    @JoinColumn(name = "client_id")
    @JsonBackReference
    private Client client;

    @ManyToOne
    @JoinColumn(name = "staff_id")
    private Staff staff;

    @OneToMany
    @JoinColumn(name = "club_tag", referencedColumnName = "club_tag")
    @JsonManagedReference
    private Set<Membership> memberships;

    @ManyToMany(mappedBy = "clubs")
    @JsonManagedReference
    private List<User> users = new ArrayList<>();

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getLogoUrl() {
        return logoUrl;
    }

    public void setLogoUrl(String logoUrl) {
        this.logoUrl = logoUrl;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public String getClubTag() {
        return clubTag;
    }

    public void setClubTag(String clubTag) {
        this.clubTag = clubTag;
    }

    public Client getClient() {
        return client;
    }

    public void setClient(Client client) {
        this.client = client;
    }

    public Staff getStaff() {
        return staff;
    }

    public void setStaff(Staff staff) {
        this.staff = staff;
    }

    public Set<Membership> getMemberships() {
        return memberships;
    }

    public void setMemberships(Set<Membership> memberships) {
        this.memberships = memberships;
    }

}