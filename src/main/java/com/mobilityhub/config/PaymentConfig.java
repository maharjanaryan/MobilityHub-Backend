// com/mobilityhub/config/PaymentConfig.java
package com.mobilityhub.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "payment")
public class PaymentConfig {

    private Esewa esewa = new Esewa();
    private Khalti khalti = new Khalti();

    @Data
    public static class Esewa {
        private String merchantCode = "EPAYTEST";
        private String secretKey = "8gBm/:&EnhH.1/q";
        private String successUrl = "http://localhost:3000/api/payments/esewa/callback";
        private String failureUrl = "http://localhost:3000/payment-failed";
        private String environment = "test";
        private String baseUrl = "https://rc-epay.esewa.com.np";
        private String verifyUrl = "https://rc.esewa.com.np/api/epay/transaction/status/";
    }

    @Data
    public static class Khalti {
        private String secretKey = "7b25ec07b830466092a64eae3224f8e8";
        private String environment = "test";
        private String returnUrl = "http://localhost:3000/payment-success";
        private String baseUrl = "https://a.khalti.com";
        private String verifyUrl = "https://a.khalti.com/api/v2/epayment/lookup/";
        private String websiteUrl = "http://localhost:3000";
    }
}