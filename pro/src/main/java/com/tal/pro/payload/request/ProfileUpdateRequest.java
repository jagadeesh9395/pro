package com.tal.pro.payload.request;

import lombok.Data;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Data
public class ProfileUpdateRequest {
    private String fullName;
    private String email;
    private String phoneNumber;
    private String currentAddress;
    private MultipartFile resumeFile;
    private List<String> skills;
    private List<String> certifications;
    private String coverLetter;
}
