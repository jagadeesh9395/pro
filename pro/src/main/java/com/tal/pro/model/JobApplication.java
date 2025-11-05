package com.tal.pro.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.DBRef;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

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
    private ApplicationStatus status = ApplicationStatus.PENDING;
    private LocalDateTime appliedAt = LocalDateTime.now();

    // Enums
    public enum ApplicationStatus {
        PENDING, REVIEWING, SHORTLISTED, INTERVIEWING,
        OFFER_EXTENDED, HIRED, REJECTED, WITHDRAWN
    }

}
