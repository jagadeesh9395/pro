package com.tal.pro.model;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.List;

@Document(collection = "experience")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Experience {
    
    public interface CreateValidationGroup {}
    public interface UpdateValidationGroup {}
    
    
    @Id
    @NotBlank(groups = UpdateValidationGroup.class, message = "ID is required for update")
    private String id;
    
    @NotBlank(groups = CreateValidationGroup.class, message = "Candidate ID is required")
    @Field("candidate_id")
    private String candidateId;
    
    @NotBlank(message = "Job title is required")
    @Field("job_title")
    private String jobTitle;
    
    @NotBlank(message = "Employer name is required")
    private String employer;
    
    private String location;
    
    @NotNull(message = "Start date is required")
    @Field("start_date")
    private LocalDate startDate;
    
    @Field("end_date")
    private LocalDate endDate;
    
    @Field("is_current")
    private boolean current = false;
    
    @Field("responsibilities")
    private List<@NotBlank(message = "Responsibility cannot be blank") String> responsibilities;
    // Business logic methods
    public boolean isCurrentPosition() {
        if (current) {
            return true;
        }
        
        if (endDate == null) {
            return false;
        }
        
        LocalDate today = LocalDate.now();
        return !endDate.isBefore(today);
    }
    
    public String getDuration() {
        if (startDate == null) {
            return "";
        }
        
        LocalDate end = (endDate != null) ? endDate : LocalDate.now();
        
        long years = java.time.temporal.ChronoUnit.YEARS.between(startDate, end);
        long months = java.time.temporal.ChronoUnit.MONTHS.between(startDate, end) % 12;
        
        if (years > 0) {
            return String.format("%d yr%s %d mo%s", 
                years, years != 1 ? "s" : "", 
                months, months != 1 ? "s" : "");
        } else {
            return String.format("%d mo%s", months, months != 1 ? "s" : "");
        }
    }


}
