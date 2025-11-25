package com.tal.pro.dto;

import com.tal.pro.model.ApplicationStatusHistory;
import com.tal.pro.model.Job;
import com.tal.pro.model.JobApplication;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApplicationDetailsDto {
    private String applicationId;
    private String status;
    private String statusDisplayName;
    private String appliedDate;
    private String lastUpdated;
    private String noticePeriod;
    private String expectedSalary;
    private Job job;
    private List<ApplicationStatusHistory> statusHistory;

    public static ApplicationDetailsDto fromJobApplication(JobApplication application) {
        if (application == null) {
            return null;
        }

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MMM d, yyyy hh:mm a");
        
        return ApplicationDetailsDto.builder()
                .applicationId(application.getId())
                .status(application.getStatus() != null ? application.getStatus().name() : "UNKNOWN")
                .statusDisplayName(application.getStatus() != null ? application.getStatus().getDisplayName() : "Unknown")
                .appliedDate(application.getAppliedAt() != null ? 
                        application.getAppliedAt().format(formatter) : "Not available")
                .lastUpdated(application.getUpdatedAt() != null ? 
                        application.getUpdatedAt().format(formatter) : "Not available")
                .noticePeriod(application.getNoticePeriod() != null ? 
                        application.getNoticePeriod() + " days" : "Not specified")
                .expectedSalary(application.getExpectedSalary() != null ? 
                        String.format("$%,.2f", application.getExpectedSalary()) : "Not specified")
                .job(application.getJob())
                .statusHistory(application.getStatusHistory())
                .build();
    }
}
