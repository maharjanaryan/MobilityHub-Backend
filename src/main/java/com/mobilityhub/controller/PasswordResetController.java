// controller/PasswordResetController.java
package com.mobilityhub.controller;

import com.mobilityhub.dto.request.ForgotPasswordRequestDto;
import com.mobilityhub.dto.request.ResetPasswordRequestDto;
import com.mobilityhub.dto.request.VerifyOtpRequestDto;
import com.mobilityhub.dto.response.MessageResponse;
import com.mobilityhub.service.PasswordResetService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@CrossOrigin(origins = "*", maxAge = 3600)
public class PasswordResetController {

    private final PasswordResetService passwordResetService;

    /**
     * Send OTP to email for password reset
     */
    @PostMapping("/forgot-password")
    public ResponseEntity<MessageResponse> forgotPassword(@Valid @RequestBody ForgotPasswordRequestDto request) {
        log.info("Password reset requested for email: {}", request.getEmail());
        MessageResponse response = passwordResetService.sendResetOtp(request);
        return ResponseEntity.ok(response);
    }

    /**
     * Verify OTP
     */
    @PostMapping("/verify-otp")
    public ResponseEntity<MessageResponse> verifyOtp(@Valid @RequestBody VerifyOtpRequestDto request) {
        log.info("OTP verification for email: {}", request.getEmail());
        MessageResponse response = passwordResetService.verifyOtp(request);
        return ResponseEntity.ok(response);
    }

    /**
     * Reset password after OTP verification
     */
    @PostMapping("/reset-password")
    public ResponseEntity<MessageResponse> resetPassword(@Valid @RequestBody ResetPasswordRequestDto request) {
        log.info("Password reset for email: {}", request.getEmail());
        MessageResponse response = passwordResetService.resetPassword(request);
        return ResponseEntity.ok(response);
    }
}