package com.tal.pro.model;

import lombok.Data;
import java.util.List;

@Data
public class ProfileUpdateRequest {
    private String fullName;
    private String phoneNumber;
    private String currentAddress;
    private List<String> skills;
}
