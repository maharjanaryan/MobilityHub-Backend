// com/mobilityhub/dto/response/MonthlyEarningsDto.java
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
public class MonthlyEarningsDto {
    private String month;
    private Integer year;
    private BigDecimal earnings;
    private Integer bookingCount;
}