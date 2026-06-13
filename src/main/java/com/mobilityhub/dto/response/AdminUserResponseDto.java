// dto/response/AdminUserResponseDto.java
package com.mobilityhub.dto.response;

import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Builder
public class AdminUserResponseDto {
    private Long id;
    private String username;
    private String email;
    private String fullName;
    private String phoneNumber;
    private String role;
    private boolean isActive;
    private boolean emailVerified;
    private String provider;  // GOOGLE, null for normal users
    private boolean isOAuthUser;
    private LocalDateTime createdAt;
    private LocalDateTime lastLogin;

    // KYC Status
    private String renterKycStatus;
    private String ownerKycStatus;

    // Statistics
    private Long totalRentals;
    private Long totalVehicles;
}

