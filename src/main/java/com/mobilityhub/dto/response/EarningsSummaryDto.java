// com/mobilityhub/dto/response/EarningsSummaryDto.java
package com.mobilityhub.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EarningsSummaryDto {
    private BigDecimal totalEarnings;
    private BigDecimal currentMonthEarnings;
    private BigDecimal currentWeekEarnings;
    private BigDecimal pendingPayout;
    private BigDecimal totalWithdrawn;
    private Integer totalBookings;
    private Integer completedBookings;
    private Double averageRating;
    @Builder.Default
    private BigDecimal availableBalance = BigDecimal.ZERO;
}