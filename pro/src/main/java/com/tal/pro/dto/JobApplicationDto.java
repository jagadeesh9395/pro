package com.tal.pro.dto;

import com.tal.pro.model.JobApplication;
import jakarta.validation.constraints.*;
import lombok.Data;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;

@Data
public class JobApplicationDto {
    private String id;
    private String jobId;

    @NotBlank(message = "Full name is required")
    @Size(max = 100, message = "Full name must be less than 100 characters")
    private String fullName;

    @NotBlank(message = "Email is required")
    @Email(message = "Please provide a valid email address")
    private String email;

    @NotBlank(message = "Phone number is required")
    @Pattern(regexp = "^[0-9]{10,15}$", message = "Please provide a valid phone number")
    private String phone;

    @Size(max = 200, message = "Current company must be less than 200 characters")
    private String currentCompany;

    private MultipartFile resumeFile;
    private String resumePath;

    @Size(max = 2000, message = "Cover letter must be less than 2000 characters")
    private String coverLetter;

    @Min(value = 0, message = "Notice period cannot be negative")
    private Integer noticePeriod;

    @Min(value = 0, message = "Expected salary cannot be negative")
    private Double expectedSalary;

    @Size(max = 1000, message = "Additional information must be less than 1000 characters")
    private String additionalInfo;

    // For internal use
    private JobApplication.ApplicationStatus status;
    private LocalDateTime appliedAt;
    private LocalDateTime updatedAt;
    private String updatedBy;
    private String notes;
}
