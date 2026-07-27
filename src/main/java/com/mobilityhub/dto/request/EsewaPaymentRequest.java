// com/mobilityhub/dto/request/EsewaPaymentRequest.java
package com.mobilityhub.dto.request;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class EsewaPaymentRequest {
    private Long amount;
    private String taxAmount;
    private String totalAmount;
    private String transactionUuid;
    private String productCode;
    private String productServiceCharge;
    private String productDeliveryCharge;
    private String successUrl;
    private String failureUrl;
    private String signedFieldNames;
    private String signature;
}