// com/mobilityhub/dto/response/EarningsTransactionDto.java
package com.mobilityhub.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EarningsTransactionDto {
    private Long id;
    private Long bookingId;
    private String bookingReference;
    private String vehicleName;
    private String renterName;
    private BigDecimal amount;
    private String type; // "RENTAL", "COMMISSION", "INSURANCE"
    private String status; // "COMPLETED", "PENDING", "REFUNDED"
    private LocalDateTime transactionDate;
    private String description;
}