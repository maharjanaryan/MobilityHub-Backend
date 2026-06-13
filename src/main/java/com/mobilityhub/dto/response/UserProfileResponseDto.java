// dto/response/UserProfileResponseDto.java
package com.mobilityhub.dto.response;

import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Builder
public class UserProfileResponseDto {
    private Long id;
    private String username;
    private String email;
    private String fullName;
    private String firstName;
    private String lastName;
    private String phoneNumber;
    private String avatarUrl;
    private String role;
    private boolean isActive;
    private boolean emailVerified;
    private LocalDateTime createdAt;
    private LocalDateTime lastLogin;

    // KYC Status
    private String renterKycStatus;
    private String ownerKycStatus;
    private boolean canBook;
    private boolean canList;

    // Statistics
    private Integer totalRentals;
    private Integer totalListings;
    private Double rating;
}