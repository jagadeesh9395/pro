package com.tal.pro.model;

import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;
import org.springframework.data.mongodb.core.mapping.DBRef;

@Document(collection = "candidates")
public class Candidate extends User {
    
    @DBRef
    private Resume resume;
    
    @Field("resume_url")
    private String resumeUrl;
    
    @Field("skills")
    private String skills;
    
    @Field("experience")
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
    public Resume getResume() {
        return resume;
    }

    public void setResume(Resume resume) {
        this.resume = resume;
        if (resume != null) {
            this.resumeUrl = "/api/resumes/" + resume.getId();
        } else {
            this.resumeUrl = null;
        }
    }

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
