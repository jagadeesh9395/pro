package com.tal.pro.model;

import jakarta.persistence.*;

@Entity
@Table(name = "candidates")
@PrimaryKeyJoinColumn(name = "user_id")
public class Candidate extends User {
    
    @Column(name = "resume_url")
    private String resumeUrl;
    
    @Column(columnDefinition = "TEXT")
    private String skills;
    
    @Column(columnDefinition = "TEXT")
    private String experience;
    
    public Candidate() {
        // Default constructor for JPA
    }
    
    public Candidate(String username, String email, String password, String firstName, String lastName) {
        super(username, email, password, firstName, lastName);
    }
    
    // For backward compatibility
    public Candidate(String username, String email, String password) {
        super(username, email, password);
    }
    
    // Getters and Setters
    public String getResumeUrl() {
        return resumeUrl;
    }

    public void setResumeUrl(String resumeUrl) {
        this.resumeUrl = resumeUrl;
    }

    public String getSkills() {
        return skills;
    }

    public void setSkills(String skills) {
        this.skills = skills;
    }

    public String getExperience() {
        return experience;
    }

    public void setExperience(String experience) {
        this.experience = experience;
    }
}
