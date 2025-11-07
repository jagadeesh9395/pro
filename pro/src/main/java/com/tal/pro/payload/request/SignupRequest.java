package com.tal.pro.payload.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class SignupRequest {
    @NotBlank
    @Size(min = 3, max = 20)
    private String username;

    @NotBlank
    @Size(max = 50)
    @Email
    private String email;

    @NotBlank
    @Size(min = 6, max = 40)
    private String password;

    @NotBlank
    private String firstName;

    @NotBlank
    private String lastName;

    // Common fields
    private String phoneNumber;
    private String currentAddress;
    
    // For candidates
    private String resumeFileId;
    private String resumeUrl;
    private List<String> skills;
    private List<String> certifications;
    private String coverLetter;
    
    // For recruiters
    private String company;
    private String position;

    @NotBlank
    private String role;
}
