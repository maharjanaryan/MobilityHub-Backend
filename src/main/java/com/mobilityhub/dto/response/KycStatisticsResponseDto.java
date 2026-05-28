// dto/response/KycStatisticsResponseDto.java
package com.mobilityhub.dto.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class KycStatisticsResponseDto {

    // Renter KYC Stats
    private Long pendingRenterKyc;
    private Long verifiedRenterKyc;
    private Long rejectedRenterKyc;

    // Owner KYC Stats
    private Long pendingOwnerKyc;
    private Long verifiedOwnerKyc;
    private Long rejectedOwnerKyc;

    // Total Stats
    private Long totalPending;
    private Long totalVerified;
    private Long totalRejected;
    private Long totalKycSubmissions;

    // Recent Activity
    private Long last24HoursSubmissions;
    private Long last7DaysSubmissions;
    private Long last30DaysSubmissions;
}