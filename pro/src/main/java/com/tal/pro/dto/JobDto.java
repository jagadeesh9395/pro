package com.tal.pro.dto;

import com.tal.pro.model.Job;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Data;

@Data
public class JobDto {

    private String id;

    @NotBlank(message = "Job title is required")
    private String jobTitle;

    @NotBlank(message = "Company name is required")
    private String companyName;

    @NotNull(message = "Job type is required")
    private Job.JobType jobType;

    @NotBlank(message = "Location is required")
    private String location;

    @PositiveOrZero(message = "Minimum salary must be a positive number")
    private Double minSalary;

    @PositiveOrZero(message = "Maximum salary must be a positive number")
    private Double maxSalary;

    @NotBlank(message = "Job description is required")
    private String description;

    @NotBlank(message = "Requirements are required")
    private String requirements;

    @NotBlank(message = "Experience is required")
    private String experience;

    private String skills; // Comma-separated skills

    // For form submission
    public static JobDto fromJob(Job job) {
        if (job == null) {
            return null;
        }

        JobDto dto = new JobDto();
        dto.setId(job.getId() != null ? job.getId() : null);
        dto.setJobTitle(job.getJobTitle());
        dto.setCompanyName(job.getCompanyName());
        dto.setJobType(job.getJobType());
        dto.setLocation(job.getLocation());
        dto.setMinSalary(job.getMinSalary());
        dto.setMaxSalary(job.getMaxSalary());
        dto.setDescription(job.getDescription());
        dto.setRequirements(job.getRequirements());
        dto.setExperience(job.getExperience());
        dto.setSkills(job.getSkills());
        return dto;
    }

    // Convert DTO to Entity
    public Job toJob() {
        Job job = new Job();
        if (this.getId() != null && !this.getId().isEmpty()) {
            job.setId(this.getId());
        }
        job.setJobTitle(this.jobTitle);
        job.setCompanyName(this.companyName);
        job.setJobType(this.jobType);
        job.setLocation(this.location);
        job.setMinSalary(this.minSalary);
        job.setDescription(this.description);
        job.setRequirements(this.requirements);
        job.setExperience(this.experience);
        job.setSkills(this.skills);
        return job;
    }
}
