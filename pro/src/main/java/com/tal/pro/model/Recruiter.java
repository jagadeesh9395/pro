package com.tal.pro.model;

import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

@Document(collection = "recruiters")
public class Recruiter extends User {
    
    @Field("company")
    private String company;
    
    @Field("position")
    private String position;
    
    @Field("company_description")
    private String companyDescription;
    
    @Field("website")
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
