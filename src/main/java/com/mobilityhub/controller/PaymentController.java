// com/mobilityhub/controller/PaymentController.java
package com.mobilityhub.controller;

import com.mobilityhub.dto.request.PaymentInitiateRequest;
import com.mobilityhub.dto.response.PaymentInitiateResponse;
import com.mobilityhub.security.services.UserDetailsImpl;
import com.mobilityhub.service.PaymentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
@CrossOrigin(origins = "*", maxAge = 3600)
public class PaymentController {

    private final PaymentService paymentService;

    @PostMapping("/initiate")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<PaymentInitiateResponse> initiatePayment(
            @Valid @RequestBody PaymentInitiateRequest request,
            Authentication authentication) {

        UserDetailsImpl userDetails = (UserDetailsImpl) authentication.getPrincipal();
        log.info("User {} initiating {} payment for booking {}",
                userDetails.getEmail(), request.getPaymentMethod(), request.getBookingId());

        if ("KHALTI".equalsIgnoreCase(request.getPaymentMethod())) {
            PaymentInitiateResponse response = paymentService.initiateKhaltiPayment(
                    request.getBookingId(),
                    request.getAmount().doubleValue()
            );
            return ResponseEntity.ok(response);

        } else if ("ESEWA".equalsIgnoreCase(request.getPaymentMethod())) {
            PaymentInitiateResponse response = paymentService.initiateEsewaPayment(
                    request.getBookingId(),
                    request.getAmount().doubleValue()
            );
            return ResponseEntity.ok(response);
        }

        return ResponseEntity.badRequest().build();
    }

    @PostMapping("/khalti/verify")
    public ResponseEntity<Map<String, Object>> verifyKhaltiPayment(@RequestBody Map<String, String> request) {
        String pidx = request.get("pidx");
        log.info("Verifying Khalti payment: pidx={}", pidx);

        boolean verified = paymentService.verifyKhaltiPayment(pidx);

        Map<String, Object> response = new HashMap<>();
        response.put("verified", verified);
        response.put("message", verified ? "Payment verified successfully" : "Payment verification failed");

        return ResponseEntity.ok(response);
    }

    @PostMapping("/esewa/callback")
    public ResponseEntity<Map<String, Object>> handleEsewaCallback(@RequestParam Map<String, String> callbackData) {
        log.info("eSewa callback received: {}", callbackData);

        boolean success = paymentService.handleEsewaCallback(callbackData);

        Map<String, Object> response = new HashMap<>();
        response.put("success", success);
        response.put("message", success ? "Payment verified successfully" : "Payment verification failed");

        return ResponseEntity.ok(response);
    }

    @PostMapping("/esewa/verify")
    public ResponseEntity<Map<String, Object>> verifyEsewaPayment(@RequestBody Map<String, String> request) {
        String transactionUuid = request.get("transaction_uuid");
        log.info("Verifying eSewa payment: transactionUuid={}", transactionUuid);

        boolean verified = paymentService.verifyEsewaPayment(transactionUuid);

        Map<String, Object> response = new HashMap<>();
        response.put("verified", verified);
        response.put("message", verified ? "Payment verified successfully" : "Payment verification failed");

        return ResponseEntity.ok(response);
    }
}