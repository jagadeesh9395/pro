package com.tal.pro.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.List;

@Data
@Document(collection = "public_resumes")
public class ResumeDocument {
    @Id
    private String id;

    // Personal Details
    private String fullName;
    private String email;
    private String phone;
    private String address;
    private String linkedinUrl;
    private String summary;

    // Collections
    private List<EducationItem> education;
    private List<String> skills;
    private List<ExperienceItem> experience;
    private List<ProjectItem> projects;
    private List<String> languages;
    private List<String> achievements;
    private List<CertificateItem> certificates;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EducationItem {
        private String school;
        private String degree;
        private String year;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ExperienceItem {
        private String role;
        private String company;
        private String duration;
        private String location;
        private String description;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ProjectItem {
        private String title;
        private String description;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CertificateItem {
        private String name;
        private String issuer;
    }
}
