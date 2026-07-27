package com.mobilityhub.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mobilityhub.config.PaymentConfig;
import com.mobilityhub.dto.response.PaymentInitiateResponse;
import com.mobilityhub.model.Booking;
import com.mobilityhub.model.Notification;
import com.mobilityhub.repository.BookingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentConfig paymentConfig;
    private final BookingRepository bookingRepository;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final NotificationService notificationService;

    /**
     * Initiate Khalti Payment
     */
    public PaymentInitiateResponse initiateKhaltiPayment(Long bookingId, Double amount) {
        try {
            Booking booking = bookingRepository.findById(bookingId)
                    .orElseThrow(() -> new RuntimeException("Booking not found"));

            // Convert to paisa (Khalti uses paisa - 1 NPR = 100 paisa)
            Long amountInPaisa = (long) (amount * 100);

            // Build Khalti payment request
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("return_url", paymentConfig.getKhalti().getReturnUrl());
            requestBody.put("website_url", paymentConfig.getKhalti().getWebsiteUrl());
            requestBody.put("amount", amountInPaisa);
            requestBody.put("purchase_order_id", "BOOK-" + bookingId + "-" + System.currentTimeMillis());
            requestBody.put("purchase_order_name", "Booking Payment - " + booking.getBookingReference());

            Map<String, String> customerInfo = new HashMap<>();
            customerInfo.put("name", booking.getRenter().getFullName());
            customerInfo.put("email", booking.getRenter().getEmail());
            customerInfo.put("phone", booking.getRenter().getPhoneNumber() != null ?
                    booking.getRenter().getPhoneNumber() : "9800000000");
            requestBody.put("customer_info", customerInfo);

            // Prepare headers with your secret key
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Authorization", "Key " + paymentConfig.getKhalti().getSecretKey());

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

            // Make API call to Khalti
            String khaltiUrl = paymentConfig.getKhalti().getBaseUrl() + "/api/v2/epayment/initiate/";
            log.info("Calling Khalti API: {}", khaltiUrl);

            ResponseEntity<Map> response = restTemplate.exchange(
                    khaltiUrl,
                    HttpMethod.POST,
                    entity,
                    Map.class
            );

            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                Map<String, Object> responseBody = response.getBody();
                String pidx = (String) responseBody.get("pidx");
                String paymentUrl = (String) responseBody.get("payment_url");

                log.info("✅ Khalti payment initiated: pidx={}, paymentUrl={}", pidx, paymentUrl);

                // Update booking with payment info
                booking.setPaymentId(pidx);
                booking.setPaymentMethod("KHALTI");
                booking.setPaymentStatus(Booking.PaymentStatus.PENDING);
                bookingRepository.save(booking);

                return PaymentInitiateResponse.builder()
                        .success(true)
                        .message("Khalti payment initiated successfully")
                        .pidx(pidx)
                        .paymentUrl(paymentUrl)
                        .build();
            }

            return PaymentInitiateResponse.builder()
                    .success(false)
                    .message("Failed to initiate Khalti payment")
                    .build();

        } catch (Exception e) {
            log.error("Error initiating Khalti payment: {}", e.getMessage(), e);
            return PaymentInitiateResponse.builder()
                    .success(false)
                    .message("Error: " + e.getMessage())
                    .build();
        }
    }

    /**
     * Verify Khalti Payment - FIXED to NOT auto-confirm booking
     */
    @Transactional
    public boolean verifyKhaltiPayment(String pidx) {
        try {
            String lookupUrl = paymentConfig.getKhalti().getVerifyUrl();

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Authorization", "Key " + paymentConfig.getKhalti().getSecretKey());

            Map<String, String> requestBody = new HashMap<>();
            requestBody.put("pidx", pidx);

            HttpEntity<Map<String, String>> entity = new HttpEntity<>(requestBody, headers);

            ResponseEntity<Map> response = restTemplate.exchange(
                    lookupUrl,
                    HttpMethod.POST,
                    entity,
                    Map.class
            );

            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                Map<String, Object> responseBody = response.getBody();
                String status = (String) responseBody.get("status");
                String transactionId = (String) responseBody.get("transaction_id");

                log.info("Khalti Lookup Response - pidx: {}, status: {}, transactionId: {}", pidx, status, transactionId);

                Booking booking = bookingRepository.findByPaymentId(pidx)
                        .orElseThrow(() -> new RuntimeException("Booking not found for pidx: " + pidx));

                // Handle all statuses
                switch (status) {
                    case "Completed":
                        booking.setTransactionId(transactionId);
                        booking.setPaymentStatus(Booking.PaymentStatus.COMPLETED);
                        booking.setPaymentVerifiedAt(LocalDateTime.now());
                        // ✅ FIX: Don't auto-confirm! Keep pending for owner approval
                        // booking.setBookingStatus(Booking.BookingStatus.CONFIRMED); // ← REMOVED

                        // Add note for owner
                        booking.setOwnerNotes("Payment completed. Awaiting owner confirmation.");

                        bookingRepository.save(booking);
                        log.info("✅ Khalti payment SUCCESSFUL for booking: {} (awaiting owner approval)", booking.getId());

                        // Notify owner that payment is complete and needs approval
                        notificationService.createNotification(
                                booking.getOwner(),
                                "💰 Payment Received - Awaiting Your Confirmation",
                                "Renter has paid for booking " + booking.getBookingReference() +
                                        ". Please review and confirm the booking.",
                                Notification.NotificationType.PAYMENT_RECEIVED,
                                booking.getId()
                        );

                        // Notify renter that payment is complete but awaiting owner
                        notificationService.createNotification(
                                booking.getRenter(),
                                "✅ Payment Successful!",
                                "Your payment for " + booking.getVehicle().getBrand() + " " +
                                        booking.getVehicle().getModel() + " is complete. Waiting for owner to confirm your booking.",
                                Notification.NotificationType.BOOKING_SUBMITTED,
                                booking.getId()
                        );

                        return true;

                    case "Pending":
                        log.warn("⏳ Khalti payment is PENDING for booking: {}", booking.getId());
                        booking.setPaymentStatus(Booking.PaymentStatus.PENDING);
                        bookingRepository.save(booking);
                        return false;

                    case "Refunded":
                    case "Partially Refunded":
                        log.warn("💰 Khalti payment was REFUNDED for booking: {}", booking.getId());
                        booking.setPaymentStatus(Booking.PaymentStatus.REFUNDED);
                        booking.setBookingStatus(Booking.BookingStatus.CANCELLED);
                        bookingRepository.save(booking);
                        return false;

                    case "Expired":
                        log.warn("⏰ Khalti payment EXPIRED for booking: {}", booking.getId());
                        booking.setPaymentStatus(Booking.PaymentStatus.FAILED);
                        booking.setBookingStatus(Booking.BookingStatus.CANCELLED);
                        bookingRepository.save(booking);
                        return false;

                    case "User canceled":
                        log.warn("❌ Khalti payment CANCELED by user for booking: {}", booking.getId());
                        booking.setPaymentStatus(Booking.PaymentStatus.FAILED);
                        booking.setBookingStatus(Booking.BookingStatus.CANCELLED);
                        bookingRepository.save(booking);
                        return false;

                    default:
                        log.error("❓ Unknown Khalti payment status '{}' for booking: {}", status, booking.getId());
                        return false;
                }
            }

            return false;

        } catch (Exception e) {
            log.error("Error verifying Khalti payment for pidx {}: {}", pidx, e.getMessage(), e);
            return false;
        }
    }

    /**
     * Initiate eSewa Payment
     */
    public PaymentInitiateResponse initiateEsewaPayment(Long bookingId, Double amount) {
        try {
            Booking booking = bookingRepository.findById(bookingId)
                    .orElseThrow(() -> new RuntimeException("Booking not found"));

            String transactionUuid = UUID.randomUUID().toString();
            String productCode = paymentConfig.getEsewa().getMerchantCode();

            // Generate signature using eSewa secret key
            String signature = generateEsewaSignature(
                    String.valueOf(amount.intValue()),
                    transactionUuid,
                    productCode
            );

            // Build form data
            Map<String, String> formData = new HashMap<>();
            formData.put("amount", String.valueOf(amount.intValue()));
            formData.put("tax_amount", "0");
            formData.put("total_amount", String.valueOf(amount.intValue()));
            formData.put("transaction_uuid", transactionUuid);
            formData.put("product_code", productCode);
            formData.put("product_service_charge", "0");
            formData.put("product_delivery_charge", "0");
            formData.put("success_url", paymentConfig.getEsewa().getSuccessUrl());
            formData.put("failure_url", paymentConfig.getEsewa().getFailureUrl());
            formData.put("signed_field_names", "total_amount,transaction_uuid,product_code");
            formData.put("signature", signature);

            String paymentUrl = paymentConfig.getEsewa().getBaseUrl() + "/api/epay/main/v2/form";

            // Update booking
            booking.setTransactionId(transactionUuid);
            booking.setPaymentMethod("ESEWA");
            booking.setPaymentStatus(Booking.PaymentStatus.PENDING);
            bookingRepository.save(booking);

            log.info("✅ eSewa payment initiated: transactionUuid={}", transactionUuid);

            return PaymentInitiateResponse.builder()
                    .success(true)
                    .message("eSewa payment initiated successfully")
                    .formData(objectMapper.writeValueAsString(formData))
                    .paymentUrl(paymentUrl)
                    .transactionId(transactionUuid)
                    .build();

        } catch (Exception e) {
            log.error("Error initiating eSewa payment: {}", e.getMessage(), e);
            return PaymentInitiateResponse.builder()
                    .success(false)
                    .message("Error: " + e.getMessage())
                    .build();
        }
    }

    /**
     * Generate eSewa Signature (HMAC-SHA256)
     */
    private String generateEsewaSignature(String totalAmount, String transactionUuid, String productCode) {
        try {
            String message = "total_amount=" + totalAmount +
                    ",transaction_uuid=" + transactionUuid +
                    ",product_code=" + productCode;

            Mac hmacSha256 = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKey = new SecretKeySpec(
                    paymentConfig.getEsewa().getSecretKey().getBytes(StandardCharsets.UTF_8),
                    "HmacSHA256"
            );
            hmacSha256.init(secretKey);
            byte[] hmacBytes = hmacSha256.doFinal(message.getBytes(StandardCharsets.UTF_8));

            return Base64.getEncoder().encodeToString(hmacBytes);

        } catch (Exception e) {
            log.error("Error generating eSewa signature: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to generate eSewa signature");
        }
    }

    /**
     * Verify eSewa Payment - FIXED to NOT auto-confirm booking
     */
    @Transactional
    public boolean verifyEsewaPayment(String transactionUuid) {
        try {
            String verifyUrl = paymentConfig.getEsewa().getVerifyUrl();

            Map<String, String> requestBody = new HashMap<>();
            requestBody.put("product_code", paymentConfig.getEsewa().getMerchantCode());
            requestBody.put("transaction_uuid", transactionUuid);
            requestBody.put("total_amount", "0");

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<Map<String, String>> entity = new HttpEntity<>(requestBody, headers);

            ResponseEntity<Map> response = restTemplate.exchange(
                    verifyUrl,
                    HttpMethod.POST,
                    entity,
                    Map.class
            );

            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                Map<String, Object> responseBody = response.getBody();
                String status = (String) responseBody.get("status");

                if ("complete".equalsIgnoreCase(status)) {
                    Booking booking = bookingRepository.findByTransactionId(transactionUuid)
                            .orElseThrow(() -> new RuntimeException("Booking not found for transaction: " + transactionUuid));

                    booking.setPaymentStatus(Booking.PaymentStatus.COMPLETED);
                    booking.setPaymentVerifiedAt(LocalDateTime.now());
                    // ✅ FIX: Don't auto-confirm! Keep pending for owner approval
                    // booking.setBookingStatus(Booking.BookingStatus.CONFIRMED); // ← REMOVED

                    booking.setOwnerNotes("Payment completed. Awaiting owner confirmation.");
                    bookingRepository.save(booking);

                    log.info("✅ eSewa payment verified: transactionUuid={} (awaiting owner approval)", transactionUuid);

                    // Notify owner
                    notificationService.createNotification(
                            booking.getOwner(),
                            "💰 Payment Received - Awaiting Your Confirmation",
                            "Renter has paid for booking " + booking.getBookingReference() +
                                    ". Please review and confirm the booking.",
                            Notification.NotificationType.PAYMENT_RECEIVED,
                            booking.getId()
                    );

                    return true;
                }
            }

            return false;

        } catch (Exception e) {
            log.error("Error verifying eSewa payment: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * Handle eSewa Callback - FIXED to NOT auto-confirm booking
     */
    @Transactional
    public boolean handleEsewaCallback(Map<String, String> callbackData) {
        try {
            String transactionUuid = callbackData.get("transaction_uuid");
            String totalAmount = callbackData.get("total_amount");
            String status = callbackData.get("status");
            String signature = callbackData.get("signature");

            if (transactionUuid == null) {
                log.error("Missing transaction_uuid in eSewa callback");
                return false;
            }

            // Verify signature
            String expectedSignature = generateEsewaSignature(
                    totalAmount,
                    transactionUuid,
                    paymentConfig.getEsewa().getMerchantCode()
            );

            if (!signature.equals(expectedSignature)) {
                log.error("Invalid eSewa signature for transaction: {}", transactionUuid);
                return false;
            }

            if ("COMPLETE".equalsIgnoreCase(status)) {
                Booking booking = bookingRepository.findByTransactionId(transactionUuid)
                        .orElseThrow(() -> new RuntimeException("Booking not found for transaction: " + transactionUuid));

                booking.setPaymentStatus(Booking.PaymentStatus.COMPLETED);
                booking.setPaymentVerifiedAt(LocalDateTime.now());
                // ✅ FIX: Don't auto-confirm! Keep pending for owner approval
                // booking.setBookingStatus(Booking.BookingStatus.CONFIRMED); // ← REMOVED

                booking.setOwnerNotes("Payment completed. Awaiting owner confirmation.");
                bookingRepository.save(booking);

                log.info("✅ eSewa payment successful: transactionUuid={} (awaiting owner approval)", transactionUuid);

                // Notify owner
                notificationService.createNotification(
                        booking.getOwner(),
                        "💰 Payment Received - Awaiting Your Confirmation",
                        "Renter has paid for booking " + booking.getBookingReference() +
                                ". Please review and confirm the booking.",
                        Notification.NotificationType.PAYMENT_RECEIVED,
                        booking.getId()
                );

                return true;
            }

            return false;

        } catch (Exception e) {
            log.error("Error handling eSewa callback: {}", e.getMessage(), e);
            return false;
        }
    }
}