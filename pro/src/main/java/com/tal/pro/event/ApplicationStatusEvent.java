package com.tal.pro.event;

import com.tal.pro.model.JobApplication;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ApplicationStatusEvent {
    private String applicationId;
    private String jobId;
    private String candidateId;
    private JobApplication.ApplicationStatus oldStatus;
    private JobApplication.ApplicationStatus newStatus;
    private String updatedBy;
    private String notes;
    private String jobTitle;
    private LocalDateTime timestamp = LocalDateTime.now();
}
