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

    // ─────────────────────────────────────────────
    // DROPOFF REMINDER EMAIL
    // ─────────────────────────────────────────────

    public void sendDropoffReminder(String to, String renterName, String vehicleName,
                                    String licensePlate, String pickupDate, String dropoffDate,
                                    String ownerName, String ownerPhone, Long bookingId) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(fromEmail);
            helper.setTo(to);
            helper.setSubject("🔔 Time to Return Your Vehicle - " + vehicleName);

            String htmlContent = buildDropoffReminderEmail(renterName, vehicleName, licensePlate,
                    pickupDate, dropoffDate, ownerName,
                    ownerPhone, bookingId);
            helper.setText(htmlContent, true);

            mailSender.send(message);
            log.info("Dropoff reminder email sent to: {}", to);

        } catch (MessagingException e) {
            log.error("Failed to send dropoff reminder email to: {}", to, e);
            throw new RuntimeException("Failed to send dropoff reminder email");
        }
    }

    // ─────────────────────────────────────────────
    // EMAIL BUILDERS
    // ─────────────────────────────────────────────

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

    private String buildDropoffReminderEmail(String renterName, String vehicleName,
                                             String licensePlate, String pickupDate,
                                             String dropoffDate, String ownerName,
                                             String ownerPhone, Long bookingId) {
        return "<!DOCTYPE html>" +
                "<html>" +
                "<head>" +
                "<meta charset='UTF-8'>" +
                "<style>" +
                "body { font-family: 'Segoe UI', Arial, sans-serif; background-color: #f4f4f4; margin: 0; padding: 0; }" +
                ".container { max-width: 600px; margin: 20px auto; padding: 0; background-color: #ffffff; border-radius: 12px; box-shadow: 0 4px 20px rgba(0,0,0,0.1); overflow: hidden; }" +
                ".header { background: linear-gradient(135deg, #4CAF50, #2E7D32); color: white; padding: 35px 20px; text-align: center; }" +
                ".header h1 { margin: 0; font-size: 28px; font-weight: 700; }" +
                ".header p { margin: 10px 0 0; font-size: 16px; opacity: 0.9; }" +
                ".content { padding: 35px 30px; }" +
                ".content h2 { color: #2E7D32; margin-top: 0; font-weight: 600; }" +
                ".greeting { font-size: 16px; color: #333; line-height: 1.6; }" +
                ".details { background: linear-gradient(135deg, #f8fff8, #f0f7f0); border-radius: 10px; padding: 25px; margin: 25px 0; border: 1px solid #c8e6c9; }" +
                ".details h3 { color: #2E7D32; margin-top: 0; margin-bottom: 15px; font-size: 18px; }" +
                ".detail-row { display: flex; justify-content: space-between; padding: 8px 0; border-bottom: 1px solid #e8f5e9; }" +
                ".detail-row:last-child { border-bottom: none; }" +
                ".detail-label { font-weight: 600; color: #555; }" +
                ".detail-value { color: #333; }" +
                ".highlight { color: #2E7D32; font-weight: bold; }" +
                ".info-box { background-color: #e8f5e9; border-left: 4px solid #4CAF50; padding: 15px 20px; margin: 20px 0; border-radius: 4px; }" +
                ".info-box p { margin: 5px 0; color: #2E7D32; }" +
                ".button { display: inline-block; background-color: #4CAF50; color: white; padding: 12px 30px; text-decoration: none; border-radius: 6px; font-weight: 600; margin: 10px 0; }" +
                ".footer { text-align: center; padding: 25px; color: #888; font-size: 12px; border-top: 1px solid #eee; background-color: #fafafa; }" +
                ".footer p { margin: 5px 0; }" +
                ".emoji-large { font-size: 24px; }" +
                "</style>" +
                "</head>" +
                "<body>" +
                "<div class='container'>" +
                "<div class='header'>" +
                "<h1>🚗 Time to Return Your Vehicle</h1>" +
                "<p>Today is the day! Your rental journey ends today</p>" +
                "</div>" +
                "<div class='content'>" +
                "<h2>Hello " + renterName + "! 👋</h2>" +
                "<p class='greeting'>" +
                "We hope you've had a wonderful experience with your rental vehicle. " +
                "This is a friendly reminder that <strong>today is the return day</strong> for your vehicle." +
                "</p>" +
                "<div class='details'>" +
                "<h3>📋 Your Booking Details</h3>" +
                "<div class='detail-row'>" +
                "<span class='detail-label'>Booking Reference:</span>" +
                "<span class='detail-value'>#" + bookingId + "</span>" +
                "</div>" +
                "<div class='detail-row'>" +
                "<span class='detail-label'>🚗 Vehicle:</span>" +
                "<span class='detail-value'><strong>" + vehicleName + "</strong></span>" +
                "</div>" +
                "<div class='detail-row'>" +
                "<span class='detail-label'>🔢 License Plate:</span>" +
                "<span class='detail-value'>" + licensePlate + "</span>" +
                "</div>" +
                "<div class='detail-row'>" +
                "<span class='detail-label'>📅 Pickup Date:</span>" +
                "<span class='detail-value'>" + pickupDate + "</span>" +
                "</div>" +
                "<div class='detail-row'>" +
                "<span class='detail-label'>📅 Return Date:</span>" +
                "<span class='detail-value highlight'>TODAY - " + dropoffDate + "</span>" +
                "</div>" +
                "<div class='detail-row'>" +
                "<span class='detail-label'>👤 Vehicle Owner:</span>" +
                "<span class='detail-value'>" + ownerName + "</span>" +
                "</div>" +
                "<div class='detail-row'>" +
                "<span class='detail-label'>📞 Owner Contact:</span>" +
                "<span class='detail-value'>" + ownerPhone + "</span>" +
                "</div>" +
                "</div>" +
                "<div class='info-box'>" +
                "<p>💡 <strong>Need to extend your rental?</strong></p>" +
                "<p>Please contact the vehicle owner directly at <strong>" + ownerPhone + "</strong> to discuss extending your booking.</p>" +
                "</div>" +
                "<div style='text-align: center; margin: 25px 0; padding: 20px; background-color: #f5f5f5; border-radius: 8px;'>" +
                "<p style='font-size: 15px; color: #333; margin: 0;'>" +
                "✨ Please return the vehicle to the designated location with the same fuel level and condition as when you picked it up. ✨" +
                "</p>" +
                "</div>" +
                "<div style='text-align: center; margin: 10px 0 20px;'>" +
                "<p style='font-size: 14px; color: #555;'>" +
                "Thank you for choosing MobilityHub! We hope to serve you again soon. 🚀" +
                "</p>" +
                "</div>" +
                "</div>" +
                "<div class='footer'>" +
                "<p>© 2024 MobilityHub. All rights reserved.</p>" +
                "<p>This is an automated message, please do not reply.</p>" +
                "<p style='margin-top: 8px; color: #aaa; font-size: 11px;'>MobilityHub - Connecting travelers with the perfect vehicle</p>" +
                "</div>" +
                "</div>" +
                "</body>" +
                "</html>";
    }
}