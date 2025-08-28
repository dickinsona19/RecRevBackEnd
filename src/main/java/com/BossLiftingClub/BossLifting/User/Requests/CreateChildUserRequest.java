package com.BossLiftingClub.BossLifting.User.Requests;

public class CreateChildUserRequest {

    private String firstName;
    private String lastName;
    private String phoneNumber;
    private String password;
    private String entryQrcodeToken;
    private String priceId; // Stripe Price ID for the subscription

    // Getters and setters
    public String getFirstName() {
        return firstName;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public void setLastName(String lastName) {
        this.lastName = lastName;
    }

    public String getPhoneNumber() {
        return phoneNumber;
    }

    public void setPhoneNumber(String phoneNumber) {
        this.phoneNumber = phoneNumber;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getEntryQrcodeToken() {
        return entryQrcodeToken;
    }

    public void setEntryQrcodeToken(String entryQrcodeToken) {
        this.entryQrcodeToken = entryQrcodeToken;
    }

    public String getPriceId() {
        return priceId;
    }

    public void setPriceId(String priceId) {
        this.priceId = priceId;
    }
}