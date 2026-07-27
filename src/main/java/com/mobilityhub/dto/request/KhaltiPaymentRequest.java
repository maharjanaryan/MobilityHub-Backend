// com/mobilityhub/dto/request/KhaltiPaymentRequest.java
package com.mobilityhub.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class KhaltiPaymentRequest {
    @JsonProperty("return_url")
    private String returnUrl;

    @JsonProperty("website_url")
    private String websiteUrl;

    private Long amount; // in paisa

    @JsonProperty("purchase_order_id")
    private String purchaseOrderId;

    @JsonProperty("purchase_order_name")
    private String purchaseOrderName;

    @JsonProperty("customer_info")
    private CustomerInfo customerInfo;

    @Data
    @Builder
    public static class CustomerInfo {
        private String name;
        private String email;
        private String phone;
    }
}