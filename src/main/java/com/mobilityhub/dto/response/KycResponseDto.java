// dto/response/KycResponseDto.java
package com.mobilityhub.dto.response;

import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Builder
public class KycResponseDto {

    // Basic response fields
    private Boolean success;
    private String message;

    // KYC Status fields
    private String kycStatus;        // PENDING, SUBMITTED, VERIFIED, REJECTED
    private String kycLevel;         // LEVEL_0, LEVEL_1, LEVEL_2
    private String kycType;          // RENTER, OWNER, BOTH

    // Separate status for both roles (when user does both)
    private String renterKycStatus;  // For users who want to book
    private String ownerKycStatus;   // For users who want to list vehicles

    // Permission flags
    private Boolean canBook;         // Can rent vehicles (renter KYC verified)
    private Boolean canList;         // Can list vehicles (owner KYC verified)

    // User info
    private Long userId;
    private String userFullName;
    private String userEmail;

    // Limits & Stats
    private Double dailyLimit;
    private Double monthlyLimit;
    private Double perTransactionLimit;

    // Timestamps
    private LocalDateTime kycVerifiedAt;
    private LocalDateTime kycSubmittedAt;

    // For admin rejection
    private String rejectionReason;

    // Helper method for successful response
    public static KycResponseDto success(String message, String kycStatus, String kycLevel) {
        return KycResponseDto.builder()
                .success(true)
                .message(message)
                .kycStatus(kycStatus)
                .kycLevel(kycLevel)
                .build();
    }

    // Helper method for error response
    public static KycResponseDto error(String message) {
        return KycResponseDto.builder()
                .success(false)
                .message(message)
                .build();
    }

    // Helper method for complete status response
    public static KycResponseDto completeStatus(
            String renterStatus,
            String ownerStatus,
            boolean canBook,
            boolean canList,
            String message) {
        return KycResponseDto.builder()
                .success(true)
                .renterKycStatus(renterStatus)
                .ownerKycStatus(ownerStatus)
                .canBook(canBook)
                .canList(canList)
                .message(message)
                .build();
    }
}