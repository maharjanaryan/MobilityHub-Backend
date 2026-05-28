// dto/response/AdminKycResponseDto.java
package com.mobilityhub.dto.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AdminKycResponseDto {
    private Long id;
    private Long userId;
    private String username;
    private String email;
    private String fullName;
    private String phoneNumber;
    private String kycStatus;
    private String kycType;  // RENTER or OWNER
    private String submittedAt;
    private String verifiedAt;
    private String documentImages;  // For admin to view
    private String action;  // APPROVED, REJECTED, PENDING
    private String rejectionReason;
}