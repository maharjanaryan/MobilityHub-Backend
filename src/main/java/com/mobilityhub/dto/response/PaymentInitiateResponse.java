// com/mobilityhub/dto/response/PaymentInitiateResponse.java
package com.mobilityhub.dto.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class PaymentInitiateResponse {
    private boolean success;
    private String message;
    private String paymentUrl;
    private String pidx; // Khalti payment ID
    private String formData; // eSewa form data as JSON string
    private String transactionId;
}