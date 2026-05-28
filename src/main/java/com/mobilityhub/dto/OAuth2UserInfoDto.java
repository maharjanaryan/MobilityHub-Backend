// dto/OAuth2UserInfoDto.java
package com.mobilityhub.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class OAuth2UserInfoDto {
    private String email;
    private String firstName;
    private String lastName;
    private String fullName;
    private String provider;
    private String providerId;
    private String avatarUrl;
}