package com.tal.pro.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.DBRef;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Document(collection = "job_applications")
@Data
@AllArgsConstructor
@RequiredArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class JobApplication {

    @Id
    private String id;

    @DBRef
    private Job job;

    @DBRef(lazy = true)
    private Candidate candidate;
    
    private String candidateId; // Store candidate ID separately for easier access

    private String fullName;
    private String email;
    private String phone;
    private String resumePath;
    private String coverLetter;
    private String currentCompany;
    private Integer noticePeriod;
    private Double expectedSalary;
    private String additionalInfo;
    private ApplicationStatus status = ApplicationStatus.APPLIED;
    private LocalDateTime appliedAt = LocalDateTime.now();
    private LocalDateTime updatedAt = LocalDateTime.now();
    private String updatedBy;
    private String notes; // For internal recruiter notes
    
    private List<ApplicationStatusHistory> statusHistory = new ArrayList<>();
    
    // Helper method to safely get candidate ID
    public String getCandidateId() {
        return candidate != null ? candidate.getId() : candidateId;
    }
    
    // Helper method to check if application can be withdrawn
    public boolean canWithdraw() {
        return status != ApplicationStatus.WITHDRAWN && 
               status != ApplicationStatus.REJECTED &&
               status != ApplicationStatus.HIRED;
    }

    // Enums with display names
    public enum ApplicationStatus {
        APPLIED("Applied"),
        UNDER_REVIEW("Under Review"),
        SHORTLISTED("Shortlisted"),
        INTERVIEW_SCHEDULED("Interview Scheduled"),
        INTERVIEWING("Interviewing"),
        OFFER_EXTENDED("Offer Extended"),
        HIRED("Hired"),
        REJECTED("Rejected"),
        WITHDRAWN("Withdrawn");

        private final String displayName;

        ApplicationStatus(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }
    
    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class ApplicationStatusHistory {
        private ApplicationStatus status;
        private String notes;
        private String updatedBy;
        private LocalDateTime updatedAt = LocalDateTime.now();
        
        // Custom constructor without updatedAt parameter - will use current time
        public ApplicationStatusHistory(ApplicationStatus status, String notes, String updatedBy) {
            this.status = status;
            this.notes = notes;
            this.updatedBy = updatedBy;
            // updatedAt is automatically set to now
        }
        
        // Ensure updatedAt is never null
        public LocalDateTime getUpdatedAt() {
            return updatedAt != null ? updatedAt : LocalDateTime.now();
        }
    }
}
