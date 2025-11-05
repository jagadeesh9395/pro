package com.tal.pro.payload.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;

import java.util.List;

@Data
@RequiredArgsConstructor
@AllArgsConstructor
public class UserInfoResponse {
    private String id;
    private String username;
    private String email;
    private String fullName;
    private List<String> roles;

}
