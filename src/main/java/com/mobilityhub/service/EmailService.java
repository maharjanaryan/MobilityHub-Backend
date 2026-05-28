// service/EmailService.java
package com.mobilityhub.service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailService {

    private final JavaMailSender mailSender;

    @Value("${spring.mail.username}")
    private String fromEmail;

    // Existing method
    public void sendVerificationCode(String to, String username, String verificationCode) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(fromEmail);
            helper.setTo(to);
            helper.setSubject("MobilityHub - Email Verification");

            String htmlContent = buildVerificationEmail(username, verificationCode);
            helper.setText(htmlContent, true);

            mailSender.send(message);
            log.info("Verification email sent to: {}", to);

        } catch (MessagingException e) {
            log.error("Failed to send email to: {}", to, e);
            throw new RuntimeException("Failed to send verification email");
        }
    }

    // ✅ NEW: Send Password Reset OTP
    public void sendPasswordResetOtp(String to, String username, String otp) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(fromEmail);
            helper.setTo(to);
            helper.setSubject("MobilityHub - Password Reset OTP");

            String htmlContent = buildPasswordResetOtpEmail(username, otp);
            helper.setText(htmlContent, true);

            mailSender.send(message);
            log.info("Password reset OTP sent to: {}", to);

        } catch (MessagingException e) {
            log.error("Failed to send password reset OTP to: {}", to, e);
            throw new RuntimeException("Failed to send password reset OTP");
        }
    }

    // ✅ NEW: Send Password Reset Confirmation
    public void sendPasswordResetConfirmation(String to, String username) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(fromEmail);
            helper.setTo(to);
            helper.setSubject("MobilityHub - Password Reset Successful");

            String htmlContent = buildPasswordResetConfirmationEmail(username);
            helper.setText(htmlContent, true);

            mailSender.send(message);
            log.info("Password reset confirmation sent to: {}", to);

        } catch (MessagingException e) {
            log.error("Failed to send password reset confirmation to: {}", to, e);
            throw new RuntimeException("Failed to send password reset confirmation");
        }
    }

    // Existing verification email builder
    private String buildVerificationEmail(String username, String verificationCode) {
        return "<!DOCTYPE html>" +
                "<html>" +
                "<head>" +
                "<style>" +
                "body { font-family: Arial, sans-serif; }" +
                ".container { max-width: 600px; margin: 0 auto; padding: 20px; }" +
                ".header { background-color: #4CAF50; color: white; padding: 20px; text-align: center; }" +
                ".content { padding: 20px; background-color: #f9f9f9; }" +
                ".code { font-size: 32px; font-weight: bold; color: #4CAF50; text-align: center; padding: 20px; letter-spacing: 5px; }" +
                ".footer { text-align: center; padding: 20px; color: #666; }" +
                "</style>" +
                "</head>" +
                "<body>" +
                "<div class='container'>" +
                "<div class='header'>" +
                "<h1>MobilityHub</h1>" +
                "</div>" +
                "<div class='content'>" +
                "<h2>Hello " + username + "!</h2>" +
                "<p>Thank you for registering with MobilityHub. Please use the verification code below to complete your registration:</p>" +
                "<div class='code'>" + verificationCode + "</div>" +
                "<p>This code will expire in <strong>10 minutes</strong>.</p>" +
                "<p>If you didn't create an account with MobilityHub, please ignore this email.</p>" +
                "<hr>" +
                "<p style='font-size: 12px; color: #666;'>For security reasons, never share this code with anyone.</p>" +
                "</div>" +
                "<div class='footer'>" +
                "<p>&copy; 2024 MobilityHub. All rights reserved.</p>" +
                "<p>This is an automated message, please do not reply.</p>" +
                "</div>" +
                "</div>" +
                "</body>" +
                "</html>";
    }

    // ✅ NEW: Password Reset OTP email builder
    private String buildPasswordResetOtpEmail(String username, String otp) {
        return "<!DOCTYPE html>" +
                "<html>" +
                "<head>" +
                "<style>" +
                "body { font-family: Arial, sans-serif; }" +
                ".container { max-width: 600px; margin: 0 auto; padding: 20px; }" +
                ".header { background-color: #4CAF50; color: white; padding: 20px; text-align: center; }" +
                ".content { padding: 30px; background-color: #f9f9f9; }" +
                ".otp { font-size: 36px; font-weight: bold; color: #4CAF50; text-align: center; padding: 20px; letter-spacing: 8px; }" +
                ".warning { background-color: #fff3cd; border-left: 4px solid #ffc107; padding: 15px; margin: 20px 0; }" +
                ".footer { text-align: center; padding: 20px; color: #666; font-size: 12px; }" +
                "</style>" +
                "</head>" +
                "<body>" +
                "<div class='container'>" +
                "<div class='header'>" +
                "<h1>🔐 Password Reset Request</h1>" +
                "</div>" +
                "<div class='content'>" +
                "<h2>Hello " + username + ",</h2>" +
                "<p>We received a request to reset your password for your MobilityHub account.</p>" +
                "<p>Use the following OTP to reset your password:</p>" +
                "<div class='otp'>" + otp + "</div>" +
                "<div class='warning'>" +
                "<p>⚠️ This OTP will expire in <strong>10 minutes</strong>.</p>" +
                "<p>If you did not request this, please ignore this email and your password will remain unchanged.</p>" +
                "</div>" +
                "<p>Best regards,<br><strong>MobilityHub Team</strong></p>" +
                "</div>" +
                "<div class='footer'>" +
                "<p>© 2024 MobilityHub. All rights reserved.</p>" +
                "<p>This is an automated message, please do not reply.</p>" +
                "</div>" +
                "</div>" +
                "</body>" +
                "</html>";
    }

    // ✅ NEW: Password Reset Confirmation email builder
    private String buildPasswordResetConfirmationEmail(String username) {
        return "<!DOCTYPE html>" +
                "<html>" +
                "<head>" +
                "<style>" +
                "body { font-family: Arial, sans-serif; }" +
                ".container { max-width: 600px; margin: 0 auto; padding: 20px; }" +
                ".header { background-color: #4CAF50; color: white; padding: 20px; text-align: center; }" +
                ".content { padding: 30px; background-color: #f9f9f9; }" +
                ".footer { text-align: center; padding: 20px; color: #666; font-size: 12px; }" +
                "</style>" +
                "</head>" +
                "<body>" +
                "<div class='container'>" +
                "<div class='header'>" +
                "<h1>✅ Password Changed Successfully</h1>" +
                "</div>" +
                "<div class='content'>" +
                "<h2>Hello " + username + ",</h2>" +
                "<p>Your MobilityHub account password has been successfully changed.</p>" +
                "<p>If you made this change, you can now login with your new password.</p>" +
                "<p>If you did not make this change, please contact our support team immediately.</p>" +
                "<p>Best regards,<br><strong>MobilityHub Team</strong></p>" +
                "</div>" +
                "<div class='footer'>" +
                "<p>© 2024 MobilityHub. All rights reserved.</p>" +
                "<p>This is an automated message, please do not reply.</p>" +
                "</div>" +
                "</div>" +
                "</body>" +
                "</html>";
    }
}