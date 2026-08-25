// com/mobilityhub/dto/request/PaymentInitiateRequest.java
package com.mobilityhub.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class PaymentInitiateRequest {
    @NotNull
    private Long bookingId;

    @NotNull
    @Positive
    private BigDecimal amount;

    @NotNull
    @Positive
    private BigDecimal serviceFee;

    @NotNull
    @Positive
    private BigDecimal insuranceFee;

    @NotNull
    private String paymentMethod; // "ESEWA" or "KHALTI"
}