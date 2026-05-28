// service/KycEmailService.java
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
public class KycEmailService {

    private final JavaMailSender mailSender;

    @Value("${spring.mail.username}")
    private String fromEmail;

    @Value("${app.email.enabled:true}")
    private boolean emailEnabled;

    /**
     * Send KYC approval email
     */
    public void sendKycApprovalEmail(String toEmail, String username, String kycType) {
        if (!emailEnabled) {
            log.info("Email disabled. KYC approval email would be sent to: {}", toEmail);
            log.info("KYC Type: {} approved for user: {}", kycType, username);
            return;
        }

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(fromEmail);
            helper.setTo(toEmail);
            helper.setSubject("MobilityHub - KYC Verification Approved ✅");

            String htmlContent = buildKycApprovalEmail(username, kycType);
            helper.setText(htmlContent, true);

            mailSender.send(message);
            log.info("KYC approval email sent to: {}", toEmail);

        } catch (MessagingException e) {
            log.error("Failed to send KYC approval email to: {}", toEmail, e);
        }
    }

    /**
     * Send KYC rejection email
     */
    public void sendKycRejectionEmail(String toEmail, String username, String kycType, String rejectionReason) {
        if (!emailEnabled) {
            log.info("Email disabled. KYC rejection email would be sent to: {}", toEmail);
            log.info("KYC Type: {} rejected for user: {} Reason: {}", kycType, username, rejectionReason);
            return;
        }

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(fromEmail);
            helper.setTo(toEmail);
            helper.setSubject("MobilityHub - KYC Verification Update 📋");

            String htmlContent = buildKycRejectionEmail(username, kycType, rejectionReason);
            helper.setText(htmlContent, true);

            mailSender.send(message);
            log.info("KYC rejection email sent to: {}", toEmail);

        } catch (MessagingException e) {
            log.error("Failed to send KYC rejection email to: {}", toEmail, e);
        }
    }

    /**
     * Send KYC submission confirmation email
     */
    public void sendKycSubmissionConfirmation(String toEmail, String username, String kycType) {
        if (!emailEnabled) {
            log.info("Email disabled. KYC submission confirmation would be sent to: {}", toEmail);
            return;
        }

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(fromEmail);
            helper.setTo(toEmail);
            helper.setSubject("MobilityHub - KYC Documents Received");

            String htmlContent = buildKycSubmissionEmail(username, kycType);
            helper.setText(htmlContent, true);

            mailSender.send(message);
            log.info("KYC submission confirmation sent to: {}", toEmail);

        } catch (MessagingException e) {
            log.error("Failed to send KYC submission confirmation to: {}", toEmail, e);
        }
    }

    /**
     * Send admin notification for pending KYC
     */
    public void sendAdminNotification(String adminEmail, String username, String kycType) {
        if (!emailEnabled) {
            log.info("Email disabled. Admin notification would be sent to: {}", adminEmail);
            return;
        }

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(fromEmail);
            helper.setTo(adminEmail);
            helper.setSubject("MobilityHub - New KYC Pending Verification");

            String htmlContent = buildAdminNotificationEmail(username, kycType);
            helper.setText(htmlContent, true);

            mailSender.send(message);
            log.info("Admin notification sent to: {}", adminEmail);

        } catch (MessagingException e) {
            log.error("Failed to send admin notification to: {}", adminEmail, e);
        }
    }

    // ==================== HTML Email Builders ====================

    private String buildKycApprovalEmail(String username, String kycType) {
        String actionMessage = kycType.equals("RENTER")
                ? "You can now book vehicles on MobilityHub!"
                : "You can now list your vehicles for rent on MobilityHub!";

        return "<!DOCTYPE html>" +
                "<html>" +
                "<head>" +
                "<style>" +
                "body { font-family: Arial, sans-serif; }" +
                ".container { max-width: 600px; margin: 0 auto; padding: 20px; }" +
                ".header { background-color: #4CAF50; color: white; padding: 20px; text-align: center; border-radius: 5px 5px 0 0; }" +
                ".content { padding: 30px; background-color: #f9f9f9; }" +
                ".button { background-color: #4CAF50; color: white; padding: 12px 24px; text-decoration: none; border-radius: 5px; display: inline-block; }" +
                ".footer { text-align: center; padding: 20px; color: #666; font-size: 12px; }" +
                ".success-icon { font-size: 48px; text-align: center; color: #4CAF50; }" +
                "</style>" +
                "</head>" +
                "<body>" +
                "<div class='container'>" +
                "<div class='header'>" +
                "<h1>✅ KYC Verification Approved!</h1>" +
                "</div>" +
                "<div class='content'>" +
                "<div class='success-icon'>✓</div>" +
                "<h2>Dear " + username + ",</h2>" +
                "<p>Congratulations! Your <strong>" + kycType + " KYC</strong> has been successfully verified.</p>" +
                "<p>" + actionMessage + "</p>" +
                "<p>Thank you for choosing MobilityHub. We look forward to serving you!</p>" +
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

    private String buildKycRejectionEmail(String username, String kycType, String rejectionReason) {
        return "<!DOCTYPE html>" +
                "<html>" +
                "<head>" +
                "<style>" +
                "body { font-family: Arial, sans-serif; }" +
                ".container { max-width: 600px; margin: 0 auto; padding: 20px; }" +
                ".header { background-color: #f44336; color: white; padding: 20px; text-align: center; border-radius: 5px 5px 0 0; }" +
                ".content { padding: 30px; background-color: #f9f9f9; }" +
                ".rejection-box { background-color: #fff3f3; border-left: 4px solid #f44336; padding: 15px; margin: 20px 0; }" +
                ".button { background-color: #4CAF50; color: white; padding: 12px 24px; text-decoration: none; border-radius: 5px; display: inline-block; }" +
                ".footer { text-align: center; padding: 20px; color: #666; font-size: 12px; }" +
                "</style>" +
                "</head>" +
                "<body>" +
                "<div class='container'>" +
                "<div class='header'>" +
                "<h1>❌ KYC Verification Update</h1>" +
                "</div>" +
                "<div class='content'>" +
                "<h2>Dear " + username + ",</h2>" +
                "<p>We have reviewed your <strong>" + kycType + " KYC</strong> documents.</p>" +
                "<div class='rejection-box'>" +
                "<p><strong>Status:</strong> Rejected</p>" +
                "<p><strong>Reason:</strong> " + rejectionReason + "</p>" +
                "</div>" +
                "<p>Please submit corrected documents and try again.</p>" +
                "<p>If you have any questions, please contact our support team.</p>" +
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

    private String buildKycSubmissionEmail(String username, String kycType) {
        return "<!DOCTYPE html>" +
                "<html>" +
                "<head>" +
                "<style>" +
                "body { font-family: Arial, sans-serif; }" +
                ".container { max-width: 600px; margin: 0 auto; padding: 20px; }" +
                ".header { background-color: #2196F3; color: white; padding: 20px; text-align: center; border-radius: 5px 5px 0 0; }" +
                ".content { padding: 30px; background-color: #f9f9f9; }" +
                ".footer { text-align: center; padding: 20px; color: #666; font-size: 12px; }" +
                "</style>" +
                "</head>" +
                "<body>" +
                "<div class='container'>" +
                "<div class='header'>" +
                "<h1>📋 KYC Documents Received</h1>" +
                "</div>" +
                "<div class='content'>" +
                "<h2>Dear " + username + ",</h2>" +
                "<p>Thank you for submitting your <strong>" + kycType + " KYC</strong> documents.</p>" +
                "<p>Our team will review your documents within <strong>24-48 hours</strong>.</p>" +
                "<p>You will receive an email notification once your KYC is verified.</p>" +
                "<p>Thank you for choosing MobilityHub!</p>" +
                "<p>Best regards,<br><strong>MobilityHub Team</strong></p>" +
                "</div>" +
                "<div class='footer'>" +
                "<p>© 2024 MobilityHub. All rights reserved.</p>" +
                "</div>" +
                "</div>" +
                "</body>" +
                "</html>";
    }

    private String buildAdminNotificationEmail(String username, String kycType) {
        return "<!DOCTYPE html>" +
                "<html>" +
                "<head>" +
                "<style>" +
                "body { font-family: Arial, sans-serif; }" +
                ".container { max-width: 600px; margin: 0 auto; padding: 20px; }" +
                ".header { background-color: #FF9800; color: white; padding: 20px; text-align: center; border-radius: 5px 5px 0 0; }" +
                ".content { padding: 30px; background-color: #f9f9f9; }" +
                ".footer { text-align: center; padding: 20px; color: #666; font-size: 12px; }" +
                "</style>" +
                "</head>" +
                "<body>" +
                "<div class='container'>" +
                "<div class='header'>" +
                "<h1>🔔 New KYC Pending</h1>" +
                "</div>" +
                "<div class='content'>" +
                "<p>A new <strong>" + kycType + " KYC</strong> submission requires your attention.</p>" +
                "<p><strong>User:</strong> " + username + "</p>" +
                "<p>Please log in to the admin panel to review and verify the documents.</p>" +
                "<p>Best regards,<br><strong>MobilityHub System</strong></p>" +
                "</div>" +
                "<div class='footer'>" +
                "<p>© 2024 MobilityHub. All rights reserved.</p>" +
                "</div>" +
                "</div>" +
                "</body>" +
                "</html>";
    }
}