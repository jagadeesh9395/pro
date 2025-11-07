package com.tal.pro.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "references")
public class Reference {
    
    @Id
    private String id;
    private String candidateId;
    private String name;
    private String relationship;
    private String email;
    private String phone;
    private String company;
    private String position;
    
    // Getters and Setters
    public String getId() {
        return id;
    }
    
    public void setId(String id) {
        this.id = id;
    }
    
    public String getCandidateId() {
        return candidateId;
    }
    
    public void setCandidateId(String candidateId) {
        this.candidateId = candidateId;
    }
    
    public String getName() {
        return name;
    }
    
    public void setName(String name) {
        this.name = name;
    }
    
    public String getRelationship() {
        return relationship;
    }
    
    public void setRelationship(String relationship) {
        this.relationship = relationship;
    }
    
    public String getEmail() {
        return email;
    }
    
    public void setEmail(String email) {
        this.email = email;
    }
    
    public String getPhone() {
        return phone;
    }
    
    public void setPhone(String phone) {
        this.phone = phone;
    }
    
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
}
