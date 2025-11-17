package com.tal.pro.model;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.LocalDate;

@Data
@Document(collection = "work_experiences")
public class WorkExperience {
    @Id
    private String id;
    
    @Field("company")
    private String company;
    
    @Field("position")
    private String position;
    
    @Field("employment_type")
    private EmploymentType employmentType;
    
    @Field("location")
    private String location;
    
    @Field("start_date")
    private LocalDate startDate;
    
    @Field("end_date")
    private LocalDate endDate;
    
    @Field("currently_working")
    private boolean currentlyWorking;
    
    @Field("description")
    private String description;
    
    @Field("candidate_id")
    private String candidateId;
    
    public enum EmploymentType {
        FULL_TIME,
        PART_TIME,
        CONTRACT,
        FREELANCE,
        INTERNSHIP,
        APPRENTICESHIP
    }
}
