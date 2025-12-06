package com.tal.pro.model;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.DBRef;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

@Document(collection = "jobs")
@Data
@AllArgsConstructor
@RequiredArgsConstructor
public class Job {

    @Id
    private String id;

    @Indexed
    private String jobTitle;

    @Indexed
    private String companyName;

    private JobType jobType;

    @Indexed
    private String location;

    private Double minSalary;
    private Double maxSalary;

    private String description;
    private String requirements;

    private String skills; // Comma-separated skills

    @DBRef
    private Recruiter postedBy;

    @Field
    @NotNull
    private LocalDateTime postedAt = LocalDateTime.now();

    @Field
    private LocalDateTime lastModifiedAt = LocalDateTime.now();

    private boolean active = true;

    @DBRef
    private Set<JobApplication> applications = new HashSet<>();

    private String experience; // e.g., "3+ years", "Entry Level", "5-7 years"

    private boolean deleted = false;

    // Enums
    public enum JobType {
        FULL_TIME, PART_TIME, CONTRACT, INTERNSHIP, TEMPORARY
    }

}
