// com/mobilityhub/service/PaymentService.java
package com.mobilityhub.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mobilityhub.config.PaymentConfig;
import com.mobilityhub.dto.response.PaymentInitiateResponse;
import com.mobilityhub.model.Booking;
import com.mobilityhub.model.Notification;
import com.mobilityhub.model.Role;
import com.mobilityhub.model.User;
import com.mobilityhub.model.Vehicle;
import com.mobilityhub.repository.BookingRepository;
import com.mobilityhub.repository.UserRepository;
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
    private final UserRepository userRepository;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final NotificationService notificationService;
    private final WalletService walletService;

    public PaymentInitiateResponse initiateKhaltiPayment(
            Long bookingId,
            Double totalAmount,
            Double serviceFee,
            Double insuranceFee) {

        try {
            Booking booking = bookingRepository.findById(bookingId)
                    .orElseThrow(() -> new RuntimeException("Booking not found"));

            Double rentalAmount = totalAmount - serviceFee - insuranceFee;

            Long amountInPaisa = (long) (totalAmount * 100);

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

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Authorization", "Key " + paymentConfig.getKhalti().getSecretKey());

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

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

                booking.setPaymentId(pidx);
                booking.setPaymentMethod("KHALTI");
                booking.setPaymentStatus(Booking.PaymentStatus.PENDING);
                booking.setTotalAmount(java.math.BigDecimal.valueOf(totalAmount));
                booking.setServiceFee(java.math.BigDecimal.valueOf(serviceFee));
                booking.setInsuranceFee(java.math.BigDecimal.valueOf(insuranceFee));
                booking.setRentalAmount(java.math.BigDecimal.valueOf(rentalAmount));
                bookingRepository.save(booking);

                log.info("💰 Payment split - Booking {}: Total={}, Rental={}, Service={}, Insurance={}",
                        bookingId, totalAmount, rentalAmount, serviceFee, insuranceFee);

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

    public PaymentInitiateResponse initiateEsewaPayment(
            Long bookingId,
            Double totalAmount,
            Double serviceFee,
            Double insuranceFee) {

        try {
            Booking booking = bookingRepository.findById(bookingId)
                    .orElseThrow(() -> new RuntimeException("Booking not found"));

            Double rentalAmount = totalAmount - serviceFee - insuranceFee;

            String transactionUuid = UUID.randomUUID().toString();
            String productCode = paymentConfig.getEsewa().getMerchantCode();

            String signature = generateEsewaSignature(
                    String.valueOf(totalAmount.intValue()),
                    transactionUuid,
                    productCode
            );

            Map<String, String> formData = new HashMap<>();
            formData.put("amount", String.valueOf(totalAmount.intValue()));
            formData.put("tax_amount", "0");
            formData.put("total_amount", String.valueOf(totalAmount.intValue()));
            formData.put("transaction_uuid", transactionUuid);
            formData.put("product_code", productCode);
            formData.put("product_service_charge", "0");
            formData.put("product_delivery_charge", "0");
            formData.put("success_url", paymentConfig.getEsewa().getSuccessUrl());
            formData.put("failure_url", paymentConfig.getEsewa().getFailureUrl());
            formData.put("signed_field_names", "total_amount,transaction_uuid,product_code");
            formData.put("signature", signature);

            String paymentUrl = paymentConfig.getEsewa().getBaseUrl() + "/api/epay/main/v2/form";

            booking.setTransactionId(transactionUuid);
            booking.setPaymentMethod("ESEWA");
            booking.setPaymentStatus(Booking.PaymentStatus.PENDING);
            booking.setTotalAmount(java.math.BigDecimal.valueOf(totalAmount));
            booking.setServiceFee(java.math.BigDecimal.valueOf(serviceFee));
            booking.setInsuranceFee(java.math.BigDecimal.valueOf(insuranceFee));
            booking.setRentalAmount(java.math.BigDecimal.valueOf(rentalAmount));
            bookingRepository.save(booking);

            log.info("✅ eSewa payment initiated: transactionUuid={}", transactionUuid);
            log.info("💰 Payment split - Booking {}: Total={}, Rental={}, Service={}, Insurance={}",
                    bookingId, totalAmount, rentalAmount, serviceFee, insuranceFee);

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

    public PaymentInitiateResponse initiateKhaltiPayment(Long bookingId, Double amount) {
        return initiateKhaltiPayment(bookingId, amount, 0.0, 0.0);
    }

    public PaymentInitiateResponse initiateEsewaPayment(Long bookingId, Double amount) {
        return initiateEsewaPayment(bookingId, amount, 0.0, 0.0);
    }

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

                switch (status) {
                    case "Completed":
                        booking.setTransactionId(transactionId);
                        booking.setPaymentStatus(Booking.PaymentStatus.COMPLETED);
                        booking.setPaymentVerifiedAt(LocalDateTime.now());

                        distributePayments(booking, "BOOKING-" + booking.getId());

                        booking.setOwnerNotes("Payment completed. Awaiting owner confirmation.");
                        bookingRepository.save(booking);
                        log.info("✅ Khalti payment SUCCESSFUL for booking: {} (awaiting owner approval)", booking.getId());

                        notificationService.createNotification(
                                booking.getOwner(),
                                "💰 Payment Received - Awaiting Your Confirmation",
                                "Renter has paid for booking " + booking.getBookingReference() +
                                        ". Please review and confirm the booking.",
                                Notification.NotificationType.PAYMENT_RECEIVED,
                                booking.getId()
                        );

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

                    distributePayments(booking, "BOOKING-" + booking.getId());

                    booking.setOwnerNotes("Payment completed. Awaiting owner confirmation.");
                    bookingRepository.save(booking);

                    log.info("✅ eSewa payment verified: transactionUuid={} (awaiting owner approval)", transactionUuid);

                    notificationService.createNotification(
                            booking.getOwner(),
                            "💰 Payment Received - Awaiting Your Confirmation",
                            "Renter has paid for booking " + booking.getBookingReference() +
                                    ". Please review and confirm the booking.",
                            Notification.NotificationType.PAYMENT_RECEIVED,
                            booking.getId()
                    );

                    notificationService.createNotification(
                            booking.getRenter(),
                            "✅ Payment Successful!",
                            "Your payment for " + booking.getVehicle().getBrand() + " " +
                                    booking.getVehicle().getModel() + " is complete. Waiting for owner to confirm your booking.",
                            Notification.NotificationType.BOOKING_SUBMITTED,
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

                distributePayments(booking, "BOOKING-" + booking.getId());

                booking.setOwnerNotes("Payment completed. Awaiting owner confirmation.");
                bookingRepository.save(booking);

                log.info("✅ eSewa payment successful: transactionUuid={} (awaiting owner approval)", transactionUuid);

                notificationService.createNotification(
                        booking.getOwner(),
                        "💰 Payment Received - Awaiting Your Confirmation",
                        "Renter has paid for booking " + booking.getBookingReference() +
                                ". Please review and confirm the booking.",
                        Notification.NotificationType.PAYMENT_RECEIVED,
                        booking.getId()
                );

                notificationService.createNotification(
                        booking.getRenter(),
                        "✅ Payment Successful!",
                        "Your payment for " + booking.getVehicle().getBrand() + " " +
                                booking.getVehicle().getModel() + " is complete. Waiting for owner to confirm your booking.",
                        Notification.NotificationType.BOOKING_SUBMITTED,
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
     * ✅ CORRECT: Distribute payments between admin and vehicle owner
     * Service Fee → Admin
     * Rental Amount + Insurance Fee → Vehicle Owner
     */
    @Transactional
    private void distributePayments(Booking booking, String referenceId) {
        try {
            Vehicle vehicle = booking.getVehicle();
            User owner = vehicle.getOwner();

            User admin = userRepository.findByRole(Role.ADMIN)
                    .stream()
                    .findFirst()
                    .orElseThrow(() -> new RuntimeException("Admin user not found in database."));

            Double serviceFee = booking.getServiceFee() != null ?
                    booking.getServiceFee().doubleValue() : 0.0;
            Double insuranceFee = booking.getInsuranceFee() != null ?
                    booking.getInsuranceFee().doubleValue() : 0.0;
            Double rentalAmount = booking.getRentalAmount() != null ?
                    booking.getRentalAmount().doubleValue() : 0.0;

            // Admin gets ONLY Service Fee
            Double adminAmount = serviceFee;

            // Owner gets Rental Amount + Insurance Fee
            Double ownerAmount = rentalAmount + insuranceFee;

            if (adminAmount > 0) {
                walletService.addBalance(admin.getId(), adminAmount,
                        "Service fee for booking: " + booking.getBookingReference() +
                                " (ID: " + booking.getId() + ")",
                        referenceId);
                log.info("💰 Added ₹{} to Admin wallet (Service Fee)", adminAmount);
            }

            if (ownerAmount > 0) {
                walletService.addBalance(owner.getId(), ownerAmount,
                        "Rental + Insurance payment for booking: " + booking.getBookingReference() +
                                " (ID: " + booking.getId() + ")",
                        referenceId);
                log.info("💰 Added ₹{} to Owner wallet (Rental: ₹{}, Insurance: ₹{})",
                        ownerAmount, rentalAmount, insuranceFee);
            }

            log.info("💰 Payment distributed - Booking {}: Admin ₹{}, Owner ₹{} (Rental + Insurance)",
                    booking.getId(), adminAmount, ownerAmount);

        } catch (Exception e) {
            log.error("Error distributing payments for booking {}: {}", booking.getId(), e.getMessage(), e);
            throw new RuntimeException("Failed to distribute payments: " + e.getMessage());
        }
    }
}