// service/KycService.java
package com.mobilityhub.service;

import com.mobilityhub.dto.request.OwnerKycRequestDto;
import com.mobilityhub.dto.request.RenterKycRequestDto;
import com.mobilityhub.dto.response.KycResponseDto;
import com.mobilityhub.model.*;
import com.mobilityhub.repository.OwnerKycRepository;
import com.mobilityhub.repository.RenterKycRepository;
import com.mobilityhub.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Period;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class KycService {

    private final RenterKycRepository renterKycRepository;
    private final OwnerKycRepository ownerKycRepository;
    private final UserRepository userRepository;
    private final EmailService emailService;
    private final NotificationService notificationService;  // Add this

    private static final int MIN_RENTAL_AGE = 21;
    private static final int MIN_OWNER_AGE = 18;

    /**
     * Submit Renter KYC - For users who want to BOOK vehicles
     */
    @Transactional
    public KycResponseDto submitRenterKyc(RenterKycRequestDto request, Long userId) {
        try {
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new RuntimeException("User not found"));

            // Age validation for renting
            if (!isValidAgeForRenting(request.getDateOfBirth())) {
                return KycResponseDto.builder()
                        .success(false)
                        .message("You must be at least 21 years old to rent vehicles in Nepal")
                        .build();
            }

            // Validate driving license expiry
            if (request.getDrivingLicenseExpiryDate().isBefore(LocalDate.now())) {
                return KycResponseDto.builder()
                        .success(false)
                        .message("Driving license is expired. Please renew your license.")
                        .build();
            }

            // Check if renter KYC already exists
            RenterKyc renterKyc = renterKycRepository.findByUserId(userId).orElse(null);

            if (renterKyc == null) {
                renterKyc = new RenterKyc();
                renterKyc.setUser(user);
            }

            // Fill renter KYC details
            renterKyc.setFullName(request.getFullName());
            renterKyc.setDateOfBirth(request.getDateOfBirth());
            renterKyc.setGender(request.getGender());
            renterKyc.setPhoneNumber(request.getPhoneNumber());
            renterKyc.setPermanentAddress(request.getPermanentAddress());
            renterKyc.setTemporaryAddress(request.getTemporaryAddress());

            // Citizenship
            renterKyc.setCitizenshipNumber(request.getCitizenshipNumber());
            renterKyc.setCitizenshipFrontImage(request.getCitizenshipFrontImage());
            renterKyc.setCitizenshipBackImage(request.getCitizenshipBackImage());

            // Driving License
            renterKyc.setDrivingLicenseNumber(request.getDrivingLicenseNumber());
            renterKyc.setDrivingLicenseIssueDate(request.getDrivingLicenseIssueDate());
            renterKyc.setDrivingLicenseExpiryDate(request.getDrivingLicenseExpiryDate());
            renterKyc.setDrivingLicenseImage(request.getDrivingLicenseImage());

            renterKyc.setKycStatus(RenterKyc.KycStatus.SUBMITTED);
            renterKyc.setSubmittedAt(LocalDateTime.now());

            RenterKyc savedKyc = renterKycRepository.save(renterKyc);

            log.info("Renter KYC submitted for user: {}", user.getUsername());

            // ✅ NOTIFICATION: To user - KYC submitted successfully
            notificationService.createNotification(
                    user,
                    "KYC Submitted Successfully",
                    "Your renter KYC documents have been submitted successfully. Our team will review them within 24-48 hours.",
                    Notification.NotificationType.KYC_SUBMITTED,
                    savedKyc.getId()
            );

            // ✅ NOTIFICATION: To all admins - New KYC pending review
            List<User> admins = userRepository.findByRole(Role.ADMIN);
            for (User admin : admins) {
                notificationService.createNotification(
                        admin,
                        "New KYC Pending Review",
                        String.format("User %s has submitted renter KYC. Please review the documents.", user.getUsername()),
                        Notification.NotificationType.KYC_PENDING_ADMIN,
                        savedKyc.getId()
                );
            }

            return KycResponseDto.builder()
                    .success(true)
                    .message("Renter KYC submitted successfully! Once verified, you can start booking vehicles.")
                    .kycStatus(savedKyc.getKycStatus().name())
                    .kycType("RENTER")
                    .build();

        } catch (Exception e) {
            log.error("Renter KYC submission failed: {}", e.getMessage());
            return KycResponseDto.builder()
                    .success(false)
                    .message("KYC submission failed: " + e.getMessage())
                    .build();
        }
    }

    /**
     * Submit Owner KYC - For users who want to SHARE/ LIST their vehicles
     * REQUIRES: Vehicle Bluebook and ownership documents
     */
    @Transactional
    public KycResponseDto submitOwnerKyc(OwnerKycRequestDto request, Long userId) {
        try {
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new RuntimeException("User not found"));

            // Age validation
            if (!isValidAgeForOwning(request.getDateOfBirth())) {
                return KycResponseDto.builder()
                        .success(false)
                        .message("You must be at least 18 years old to list vehicles for rent")
                        .build();
            }

            // Validate driving license
            if (request.getDrivingLicenseExpiryDate().isBefore(LocalDate.now())) {
                return KycResponseDto.builder()
                        .success(false)
                        .message("Driving license is expired. Please renew your license.")
                        .build();
            }

            // CRITICAL: Vehicle Bluebook is MANDATORY for owners
            if (request.getVehicleBluebookNumber() == null || request.getVehicleBluebookNumber().isEmpty()) {
                return KycResponseDto.builder()
                        .success(false)
                        .message("Vehicle Bluebook number is REQUIRED for vehicle owners")
                        .build();
            }

            if (request.getVehicleBluebookImage() == null || request.getVehicleBluebookImage().isEmpty()) {
                return KycResponseDto.builder()
                        .success(false)
                        .message("Vehicle Bluebook image is REQUIRED for vehicle owners")
                        .build();
            }

            // Check if owner KYC already exists
            OwnerKyc ownerKyc = ownerKycRepository.findByUserId(userId).orElse(null);

            if (ownerKyc == null) {
                ownerKyc = new OwnerKyc();
                ownerKyc.setUser(user);
            }

            // Fill owner KYC details
            ownerKyc.setFullName(request.getFullName());
            ownerKyc.setDateOfBirth(request.getDateOfBirth());
            ownerKyc.setPhoneNumber(request.getPhoneNumber());
            ownerKyc.setPermanentAddress(request.getPermanentAddress());

            // Citizenship
            ownerKyc.setCitizenshipNumber(request.getCitizenshipNumber());
            ownerKyc.setCitizenshipFrontImage(request.getCitizenshipFrontImage());
            ownerKyc.setCitizenshipBackImage(request.getCitizenshipBackImage());

            // Driving License
            ownerKyc.setDrivingLicenseNumber(request.getDrivingLicenseNumber());
            ownerKyc.setDrivingLicenseExpiryDate(request.getDrivingLicenseExpiryDate());
            ownerKyc.setDrivingLicenseImage(request.getDrivingLicenseImage());

            // Vehicle Ownership Documents (MANDATORY)
            ownerKyc.setVehicleBluebookNumber(request.getVehicleBluebookNumber());
            ownerKyc.setVehicleBluebookImage(request.getVehicleBluebookImage());

            // Optional vehicle ownership certificate
            if (request.getVehicleOwnershipCertificate() != null) {
                ownerKyc.setVehicleOwnershipCertificate(request.getVehicleOwnershipCertificate());
            }

            // Payment Information
            ownerKyc.setBankAccountNumber(request.getBankAccountNumber());
            ownerKyc.setBankName(request.getBankName());
            ownerKyc.setBankAccountHolderName(request.getBankAccountHolderName());
            ownerKyc.setPanNumber(request.getPanNumber());

            ownerKyc.setKycStatus(OwnerKyc.KycStatus.SUBMITTED);
            ownerKyc.setSubmittedAt(LocalDateTime.now());

            OwnerKyc savedKyc = ownerKycRepository.save(ownerKyc);

            log.info("Owner KYC submitted for user: {} with Bluebook: {}",
                    user.getUsername(), request.getVehicleBluebookNumber());

            // ✅ NOTIFICATION: To user - Owner KYC submitted successfully
            notificationService.createNotification(
                    user,
                    "Owner KYC Submitted Successfully",
                    "Your owner KYC documents have been submitted successfully. Our team will verify your Bluebook and documents within 24-48 hours.",
                    Notification.NotificationType.KYC_SUBMITTED,
                    savedKyc.getId()
            );

            // ✅ NOTIFICATION: To all admins - New Owner KYC pending review
            List<User> admins = userRepository.findByRole(Role.ADMIN);
            for (User admin : admins) {
                notificationService.createNotification(
                        admin,
                        "New Owner KYC Pending Review",
                        String.format("User %s has submitted owner KYC with Bluebook: %s. Please review the documents.",
                                user.getUsername(), request.getVehicleBluebookNumber()),
                        Notification.NotificationType.KYC_PENDING_ADMIN,
                        savedKyc.getId()
                );
            }

            return KycResponseDto.builder()
                    .success(true)
                    .message("Owner KYC submitted successfully! Our team will verify your Bluebook and documents within 24-48 hours. Once verified, you can list your vehicles.")
                    .kycStatus(savedKyc.getKycStatus().name())
                    .kycType("OWNER")
                    .build();

        } catch (Exception e) {
            log.error("Owner KYC submission failed: {}", e.getMessage());
            return KycResponseDto.builder()
                    .success(false)
                    .message("KYC submission failed: " + e.getMessage())
                    .build();
        }
    }

    /**
     * Check if user can book a vehicle (Renter)
     */
    public boolean canBookVehicle(Long userId) {
        return renterKycRepository.existsByUserIdAndKycStatus(
                userId, RenterKyc.KycStatus.VERIFIED);
    }

    /**
     * Check if user can list vehicles (Owner)
     */
    public boolean canListVehicle(Long userId) {
        return ownerKycRepository.existsByUserIdAndKycStatus(
                userId, OwnerKyc.KycStatus.VERIFIED);
    }

    /**
     * Get user's KYC status for both roles
     */
    public KycResponseDto getCompleteKycStatus(Long userId) {
        RenterKyc renterKyc = renterKycRepository.findByUserId(userId).orElse(null);
        OwnerKyc ownerKyc = ownerKycRepository.findByUserId(userId).orElse(null);

        return KycResponseDto.builder()
                .success(true)
                .renterKycStatus(renterKyc != null ? renterKyc.getKycStatus().name() : "NOT_SUBMITTED")
                .ownerKycStatus(ownerKyc != null ? ownerKyc.getKycStatus().name() : "NOT_SUBMITTED")
                .canBook(canBookVehicle(userId))
                .canList(canListVehicle(userId))
                .message(getKycStatusMessage(renterKyc, ownerKyc))
                .build();
    }

    // Helper methods
    private boolean isValidAgeForRenting(LocalDate dateOfBirth) {
        if (dateOfBirth == null) return false;
        return Period.between(dateOfBirth, LocalDate.now()).getYears() >= MIN_RENTAL_AGE;
    }

    private boolean isValidAgeForOwning(LocalDate dateOfBirth) {
        if (dateOfBirth == null) return false;
        return Period.between(dateOfBirth, LocalDate.now()).getYears() >= MIN_OWNER_AGE;
    }

    private String getKycStatusMessage(RenterKyc renterKyc, OwnerKyc ownerKyc) {
        // Logic to generate appropriate message
        if (renterKyc != null && renterKyc.getKycStatus() == RenterKyc.KycStatus.VERIFIED &&
                ownerKyc != null && ownerKyc.getKycStatus() == OwnerKyc.KycStatus.VERIFIED) {
            return "You can both book vehicles and list your vehicles for rent!";
        } else if (renterKyc != null && renterKyc.getKycStatus() == RenterKyc.KycStatus.VERIFIED) {
            return "You can book vehicles. Complete Owner KYC to list your vehicles.";
        } else if (ownerKyc != null && ownerKyc.getKycStatus() == OwnerKyc.KycStatus.VERIFIED) {
            return "You can list your vehicles. Complete Renter KYC to book vehicles.";
        } else if (renterKyc != null && renterKyc.getKycStatus() == RenterKyc.KycStatus.SUBMITTED) {
            return "Your renter KYC is under review. You'll be notified once verified.";
        } else if (ownerKyc != null && ownerKyc.getKycStatus() == OwnerKyc.KycStatus.SUBMITTED) {
            return "Your owner KYC is under review. You'll be notified once verified.";
        } else {
            return "Complete KYC to start using MobilityHub!";
        }
    }
}