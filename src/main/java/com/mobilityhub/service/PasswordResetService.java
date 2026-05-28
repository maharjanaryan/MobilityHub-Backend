// service/PasswordResetService.java
package com.mobilityhub.service;

import com.mobilityhub.dto.request.ForgotPasswordRequestDto;
import com.mobilityhub.dto.request.ResetPasswordRequestDto;
import com.mobilityhub.dto.request.VerifyOtpRequestDto;
import com.mobilityhub.dto.response.MessageResponse;
import com.mobilityhub.model.User;
import com.mobilityhub.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class PasswordResetService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;

    private static final int OTP_EXPIRY_MINUTES = 10;
    private static final int OTP_LENGTH = 6;

    /**
     * Send OTP to user's email for password reset
     */
    @Transactional
    public MessageResponse sendResetOtp(ForgotPasswordRequestDto request) {
        try {
            Optional<User> userOpt = userRepository.findByEmail(request.getEmail());

            if (userOpt.isEmpty()) {
                // Don't reveal that email doesn't exist for security reasons
                log.warn("Password reset requested for non-existent email: {}", request.getEmail());
                return new MessageResponse(
                        "If your email is registered, you will receive an OTP to reset your password.",
                        true,
                        null
                );
            }

            User user = userOpt.get();

            // Check if user is OAuth user (no password)
            if (user.isOAuthUser() && user.getProvider() != null) {
                return new MessageResponse(
                        "This account uses Google login. Please sign in with Google.",
                        false,
                        null
                );
            }

            // Generate OTP
            String otp = generateOtp();

            // Save OTP to database
            user.setResetPasswordOtp(otp);
            user.setResetPasswordOtpExpiry(LocalDateTime.now().plusMinutes(OTP_EXPIRY_MINUTES));
            userRepository.save(user);

            // Send OTP via email
            emailService.sendPasswordResetOtp(user.getEmail(), user.getUsername(), otp);

            log.info("Password reset OTP sent to: {}", request.getEmail());

            return new MessageResponse(
                    "OTP sent to your email address. It will expire in " + OTP_EXPIRY_MINUTES + " minutes.",
                    true,
                    null
            );

        } catch (Exception e) {
            log.error("Failed to send reset OTP: {}", e.getMessage());
            return new MessageResponse("Failed to send OTP. Please try again.", false, null);
        }
    }

    /**
     * Verify OTP
     */
    @Transactional
    public MessageResponse verifyOtp(VerifyOtpRequestDto request) {
        try {
            Optional<User> userOpt = userRepository.findByEmail(request.getEmail());

            if (userOpt.isEmpty()) {
                return new MessageResponse("Invalid request.", false, null);
            }

            User user = userOpt.get();

            // Check if OTP exists
            if (user.getResetPasswordOtp() == null) {
                return new MessageResponse("No OTP request found. Please request a new OTP.", false, null);
            }

            // Check if OTP is expired
            if (user.getResetPasswordOtpExpiry().isBefore(LocalDateTime.now())) {
                return new MessageResponse("OTP has expired. Please request a new OTP.", false, null);
            }

            // Verify OTP
            if (!user.getResetPasswordOtp().equals(request.getOtp())) {
                return new MessageResponse("Invalid OTP. Please try again.", false, null);
            }

            log.info("OTP verified successfully for: {}", request.getEmail());

            return new MessageResponse("OTP verified successfully. You can now reset your password.", true, null);

        } catch (Exception e) {
            log.error("OTP verification failed: {}", e.getMessage());
            return new MessageResponse("Failed to verify OTP. Please try again.", false, null);
        }
    }

    /**
     * Reset password after OTP verification
     */
    @Transactional
    public MessageResponse resetPassword(ResetPasswordRequestDto request) {
        try {
            // Validate passwords match
            if (!request.getNewPassword().equals(request.getConfirmPassword())) {
                return new MessageResponse("Passwords do not match.", false, null);
            }

            Optional<User> userOpt = userRepository.findByEmail(request.getEmail());

            if (userOpt.isEmpty()) {
                return new MessageResponse("Invalid request.", false, null);
            }

            User user = userOpt.get();

            // Check if OTP exists and is valid
            if (user.getResetPasswordOtp() == null) {
                return new MessageResponse("No OTP request found. Please request a new OTP.", false, null);
            }

            // Check if OTP is expired
            if (user.getResetPasswordOtpExpiry().isBefore(LocalDateTime.now())) {
                return new MessageResponse("OTP has expired. Please request a new OTP.", false, null);
            }

            // Verify OTP
            if (!user.getResetPasswordOtp().equals(request.getOtp())) {
                return new MessageResponse("Invalid OTP.", false, null);
            }

            // Check if new password is different from old password
            if (passwordEncoder.matches(request.getNewPassword(), user.getPassword())) {
                return new MessageResponse("New password cannot be the same as the old password.", false, null);
            }

            // Update password
            user.setPassword(passwordEncoder.encode(request.getNewPassword()));

            // Clear OTP fields
            user.setResetPasswordOtp(null);
            user.setResetPasswordOtpExpiry(null);

            userRepository.save(user);

            log.info("Password reset successfully for: {}", request.getEmail());

            // Send confirmation email
            emailService.sendPasswordResetConfirmation(user.getEmail(), user.getUsername());

            return new MessageResponse("Password reset successfully! You can now login with your new password.", true, null);

        } catch (Exception e) {
            log.error("Password reset failed: {}", e.getMessage());
            return new MessageResponse("Failed to reset password. Please try again.", false, null);
        }
    }

    /**
     * Generate 6-digit OTP
     */
    private String generateOtp() {
        SecureRandom random = new SecureRandom();
        int otp = 100000 + random.nextInt(900000);
        return String.valueOf(otp);
    }
}