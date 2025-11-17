package com.tal.pro.dto;

import lombok.Data;

import java.time.LocalDate;

@Data
public class EducationDto {
    private String id;
    private String institution;
    private String degree;
    private String fieldOfStudy;
    private LocalDate startDate;
    private LocalDate endDate;
    private boolean currentlyStudying;
    private String description;
}
