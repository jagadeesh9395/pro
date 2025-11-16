package com.tal.pro.model;

import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;
import org.springframework.data.mongodb.core.mapping.DBRef;
import java.util.Arrays;

import java.util.ArrayList;
import java.util.List;

@Document(collection = "candidates")
public class Candidate extends User {
    
    @DBRef
    private Resume resume;
    
    @Field("resume_url")
    private String resumeUrl;
    
    @Field("skills")
    private String skills; // Comma-separated skills
    
    @Field("experience")
    private String experience;
    
    @Field("location")
    private String location;
    
    @Field("current_company")
    private String currentCompany;
    
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
    
    // Helper method to add a single skill
    public void addSkill(String skill) {
        if (skill != null && !skill.trim().isEmpty()) {
            if (this.skills == null || this.skills.isEmpty()) {
                this.skills = skill.trim();
            } else if (!Arrays.asList(this.skills.split("\\s*,\\s*")).contains(skill.trim())) {
                this.skills += ", " + skill.trim();
            }
        }
    }
    
    // Get skills as list
    public List<String> getSkillsList() {
        if (this.skills == null || this.skills.isEmpty()) {
            return new ArrayList<>();
        }
        return Arrays.asList(this.skills.split("\\s*,\\s*"));
    }

    public String getExperience() {
        return experience;
    }
    
    public String getLocation() {
        return location;
    }
    
    public void setLocation(String location) {
        this.location = location;
    }

    public void setExperience(String experience) {
        this.experience = experience;
    }
    
    public String getCurrentCompany() {
        return currentCompany;
    }
    
    public void setCurrentCompany(String currentCompany) {
        this.currentCompany = currentCompany;
    }
}
