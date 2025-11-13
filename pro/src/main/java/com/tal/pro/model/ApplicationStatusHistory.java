package com.tal.pro.model;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import java.time.LocalDateTime;

@Data
@Document(collection = "application_status_history")
public class ApplicationStatusHistory {
    
    @Id
    private String id;
    private JobApplication.ApplicationStatus status;
    private String notes;
    private LocalDateTime changedAt = LocalDateTime.now();
    private String changedBy; // Could be candidate ID or system
}
