// dto/request/AdminUpdateUserRequestDto.java
package com.mobilityhub.dto.request;

import lombok.Data;

@Data
public class AdminUpdateUserRequestDto {
    private String fullName;
    private String phoneNumber;
    private String role;  // USER, OWNER, ADMIN
    private Boolean isActive;
    private Boolean emailVerified;
}