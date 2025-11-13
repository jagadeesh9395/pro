package com.tal.pro.model;

import lombok.AllArgsConstructor;
import lombok.Data;
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
public class JobApplication {

    @Id
    private String id;

    @DBRef
    private Job job;

    @DBRef
    private Candidate candidate;

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
}
