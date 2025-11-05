package com.tal.pro.model;

import jakarta.persistence.*;

@Entity
@Table(name = "recruiters")
@PrimaryKeyJoinColumn(name = "user_id")
public class Recruiter extends User {
    
    @Column(name = "company")
    private String company;
    
    @Column(name = "position")
    private String position;
    
    @Column(name = "company_description", columnDefinition = "TEXT")
    private String companyDescription;
    
    @Column(name = "website")
    private String website;
    
    public Recruiter() {
        // Default constructor for JPA
    }

    public Recruiter(String username, String email, String password, String firstName, String lastName) {
        super(username, email, password, firstName, lastName);
    }
    
    // For backward compatibility
    public Recruiter(String username, String email, String password) {
        super(username, email, password);
    }
    
    // Getters and Setters
    public String getCompany() {
        return company;
    }

    public void setCompany(String company) {
        this.company = company;
    }

    public String getPosition() {
        return position;
    }

    public void setPosition(String position) {
        this.position = position;
    }
    
    public String getCompanyDescription() {
        return companyDescription;
    }
    
    public void setCompanyDescription(String companyDescription) {
        this.companyDescription = companyDescription;
    }
    
    public String getWebsite() {
        return website;
    }
    
    public void setWebsite(String website) {
        this.website = website;
    }
}
