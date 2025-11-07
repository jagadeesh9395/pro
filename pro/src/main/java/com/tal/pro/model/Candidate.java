package com.tal.pro.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import org.springframework.data.mongodb.core.mapping.DBRef;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Document(collection = "candidates")
@Data
@AllArgsConstructor
public class Candidate extends User {

    @Field("current_address")
    private String currentAddress;

    @Field("resume_file_id")
    private String resumeFileId;

    @Field("resume_url")
    private String resumeUrl;

    @Field("skills")
    private List<String> skills = new ArrayList<>();

    @Field("certifications")
    private List<String> certifications = new ArrayList<>();

    @Field("cover_letter")
    private String coverLetter;

    @DBRef
    private List<Education> educations = new ArrayList<>();

    @DBRef
    private List<Experience> workExperiences = new ArrayList<>();

    @DBRef
    private List<Reference> references = new ArrayList<>();

    @Field("created_at")
    private LocalDate createdAt;

    @Field("updated_at")
    private LocalDate updatedAt;

    public Candidate() {
        this.createdAt = LocalDate.now();
        this.updatedAt = LocalDate.now();
    }

    public Candidate(String username, String email, String password, String firstName, String lastName) {
        super(username, email, password, firstName, lastName);
    }

    // For backward compatibility
    public Candidate(String username, String email, String password) {
        super(username, email, password);
        this.createdAt = LocalDate.now();
        this.updatedAt = LocalDate.now();
    }
}
