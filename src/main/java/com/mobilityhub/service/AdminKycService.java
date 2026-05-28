// service/AdminKycService.java
package com.mobilityhub.service;

import com.mobilityhub.dto.response.AdminKycResponseDto;
import com.mobilityhub.dto.response.KycDetailsResponseDto;
import com.mobilityhub.dto.response.KycResponseDto;
import com.mobilityhub.dto.response.KycStatisticsResponseDto;
import com.mobilityhub.model.*;
import com.mobilityhub.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminKycService {

    private final RenterKycRepository renterKycRepository;
    private final OwnerKycRepository ownerKycRepository;
    private final UserRepository userRepository;
    private final KycEmailService kycEmailService;
    private final NotificationService notificationService;  // Add this

    /**
     * Get all pending renter KYC requests
     */
    public List<AdminKycResponseDto> getPendingRenterKyc() {
        List<RenterKyc> pendingKyc = renterKycRepository.findByKycStatus(RenterKyc.KycStatus.SUBMITTED);
        List<AdminKycResponseDto> response = new ArrayList<>();

        for (RenterKyc kyc : pendingKyc) {
            response.add(mapToAdminResponse(kyc.getUser(), "RENTER", kyc));
        }
        return response;
    }

    /**
     * Get all pending owner KYC requests
     */
    public List<AdminKycResponseDto> getPendingOwnerKyc() {
        List<OwnerKyc> pendingKyc = ownerKycRepository.findByKycStatus(OwnerKyc.KycStatus.SUBMITTED);
        List<AdminKycResponseDto> response = new ArrayList<>();

        for (OwnerKyc kyc : pendingKyc) {
            response.add(mapToAdminResponse(kyc.getUser(), "OWNER", kyc));
        }
        return response;
    }

    /**
     * Get all pending KYC (both types)
     */
    public List<AdminKycResponseDto> getAllPendingKyc() {
        List<AdminKycResponseDto> allPending = new ArrayList<>();
        allPending.addAll(getPendingRenterKyc());
        allPending.addAll(getPendingOwnerKyc());
        return allPending;
    }

    /**
     * Get KYC details by ID - Returns KycDetailsResponseDto
     */
    public KycDetailsResponseDto getKycDetails(Long kycId) {
        // Try to find in renter KYC first
        RenterKyc renterKyc = renterKycRepository.findById(kycId).orElse(null);
        if (renterKyc != null) {
            return mapToKycDetailsResponse(renterKyc);
        }

        // Try owner KYC
        OwnerKyc ownerKyc = ownerKycRepository.findById(kycId).orElse(null);
        if (ownerKyc != null) {
            return mapToKycDetailsResponse(ownerKyc);
        }

        throw new RuntimeException("KYC not found with ID: " + kycId);
    }

    /**
     * Get KYC by user ID
     */
    public Map<String, Object> getKycByUserId(Long userId) {
        RenterKyc renterKyc = renterKycRepository.findByUserId(userId).orElse(null);
        OwnerKyc ownerKyc = ownerKycRepository.findByUserId(userId).orElse(null);

        Map<String, Object> response = new HashMap<>();
        response.put("userId", userId);

        if (renterKyc != null) {
            response.put("renterKyc", mapToKycDetailsResponse(renterKyc));
        }
        if (ownerKyc != null) {
            response.put("ownerKyc", mapToKycDetailsResponse(ownerKyc));
        }

        return response;
    }

    /**
     * Verify Renter KYC
     */
    @Transactional
    public KycResponseDto verifyRenterKyc(Long kycId, Long adminId, boolean approved,
                                          String rejectionReason, String adminNotes) {
        try {
            RenterKyc kyc = renterKycRepository.findById(kycId)
                    .orElseThrow(() -> new RuntimeException("Renter KYC not found"));

            User user = kyc.getUser();
            User admin = userRepository.findById(adminId)
                    .orElseThrow(() -> new RuntimeException("Admin not found"));

            if (approved) {
                // Approve KYC
                kyc.setKycStatus(RenterKyc.KycStatus.VERIFIED);
                kyc.setVerifiedAt(LocalDateTime.now());
                kyc.setVerifiedBy(adminId);
                kyc.setRejectedReason(null);

                renterKycRepository.save(kyc);

                // Send approval email using KycEmailService
                kycEmailService.sendKycApprovalEmail(user.getEmail(), user.getFullName(), "RENTER");

                // ✅ NOTIFICATION: To user - KYC Approved
                notificationService.createNotification(
                        user,
                        "KYC Approved! 🎉",
                        "Congratulations! Your renter KYC has been approved. You can now book vehicles on MobilityHub.",
                        Notification.NotificationType.KYC_APPROVED,
                        kyc.getId()
                );

                log.info("Admin {} approved renter KYC for user: {}", admin.getUsername(), user.getUsername());

                return KycResponseDto.builder()
                        .success(true)
                        .message("Renter KYC approved successfully! User can now book vehicles.")
                        .kycStatus("VERIFIED")
                        .kycType("RENTER")
                        .userId(user.getId())
                        .userFullName(user.getFullName())
                        .userEmail(user.getEmail())
                        .kycVerifiedAt(kyc.getVerifiedAt())
                        .canBook(true)
                        .build();
            } else {
                // Reject KYC
                kyc.setKycStatus(RenterKyc.KycStatus.REJECTED);
                kyc.setRejectedReason(rejectionReason);
                kyc.setVerifiedAt(LocalDateTime.now());
                kyc.setVerifiedBy(adminId);

                renterKycRepository.save(kyc);

                // Send rejection email using KycEmailService
                kycEmailService.sendKycRejectionEmail(user.getEmail(), user.getFullName(), "RENTER", rejectionReason);

                // ✅ NOTIFICATION: To user - KYC Rejected
                notificationService.createNotification(
                        user,
                        "KYC Rejected ❌",
                        String.format("Your renter KYC has been rejected. Reason: %s. Please resubmit with correct documents.", rejectionReason),
                        Notification.NotificationType.KYC_REJECTED,
                        kyc.getId()
                );

                log.info("Admin {} rejected renter KYC for user: {} Reason: {}",
                        admin.getUsername(), user.getUsername(), rejectionReason);

                return KycResponseDto.builder()
                        .success(false)
                        .message("Renter KYC rejected: " + rejectionReason)
                        .kycStatus("REJECTED")
                        .kycType("RENTER")
                        .userId(user.getId())
                        .userFullName(user.getFullName())
                        .userEmail(user.getEmail())
                        .rejectionReason(rejectionReason)
                        .build();
            }
        } catch (Exception e) {
            log.error("Renter KYC verification failed: {}", e.getMessage());
            return KycResponseDto.builder()
                    .success(false)
                    .message("Verification failed: " + e.getMessage())
                    .build();
        }
    }

    /**
     * Verify Owner KYC
     */
    @Transactional
    public KycResponseDto verifyOwnerKyc(Long kycId, Long adminId, boolean approved,
                                         String rejectionReason, String adminNotes) {
        try {
            OwnerKyc kyc = ownerKycRepository.findById(kycId)
                    .orElseThrow(() -> new RuntimeException("Owner KYC not found"));

            User user = kyc.getUser();
            User admin = userRepository.findById(adminId)
                    .orElseThrow(() -> new RuntimeException("Admin not found"));

            if (approved) {
                // Approve KYC
                kyc.setKycStatus(OwnerKyc.KycStatus.VERIFIED);
                kyc.setVerifiedAt(LocalDateTime.now());
                kyc.setVerifiedBy(adminId);
                kyc.setRejectedReason(null);
                kyc.setOwnershipProofVerified(true);

                ownerKycRepository.save(kyc);

                // Send approval email using KycEmailService
                kycEmailService.sendKycApprovalEmail(user.getEmail(), user.getFullName(), "OWNER");

                // ✅ NOTIFICATION: To user - Owner KYC Approved
                notificationService.createNotification(
                        user,
                        "Owner KYC Approved! 🎉",
                        "Congratulations! Your owner KYC has been approved. You can now list your vehicles on MobilityHub.",
                        Notification.NotificationType.KYC_APPROVED,
                        kyc.getId()
                );

                log.info("Admin {} approved owner KYC for user: {}", admin.getUsername(), user.getUsername());

                return KycResponseDto.builder()
                        .success(true)
                        .message("Owner KYC approved successfully! User can now list vehicles.")
                        .kycStatus("VERIFIED")
                        .kycType("OWNER")
                        .userId(user.getId())
                        .userFullName(user.getFullName())
                        .userEmail(user.getEmail())
                        .kycVerifiedAt(kyc.getVerifiedAt())
                        .canList(true)
                        .build();
            } else {
                // Reject KYC
                kyc.setKycStatus(OwnerKyc.KycStatus.REJECTED);
                kyc.setRejectedReason(rejectionReason);
                kyc.setVerifiedAt(LocalDateTime.now());
                kyc.setVerifiedBy(adminId);
                kyc.setOwnershipProofVerified(false);

                ownerKycRepository.save(kyc);

                // Send rejection email using KycEmailService
                kycEmailService.sendKycRejectionEmail(user.getEmail(), user.getFullName(), "OWNER", rejectionReason);

                // ✅ NOTIFICATION: To user - Owner KYC Rejected
                notificationService.createNotification(
                        user,
                        "Owner KYC Rejected ❌",
                        String.format("Your owner KYC has been rejected. Reason: %s. Please resubmit with correct documents (Bluebook, Citizenship, Driving License).", rejectionReason),
                        Notification.NotificationType.KYC_REJECTED,
                        kyc.getId()
                );

                log.info("Admin {} rejected owner KYC for user: {} Reason: {}",
                        admin.getUsername(), user.getUsername(), rejectionReason);

                return KycResponseDto.builder()
                        .success(false)
                        .message("Owner KYC rejected: " + rejectionReason)
                        .kycStatus("REJECTED")
                        .kycType("OWNER")
                        .userId(user.getId())
                        .userFullName(user.getFullName())
                        .userEmail(user.getEmail())
                        .rejectionReason(rejectionReason)
                        .build();
            }
        } catch (Exception e) {
            log.error("Owner KYC verification failed: {}", e.getMessage());
            return KycResponseDto.builder()
                    .success(false)
                    .message("Verification failed: " + e.getMessage())
                    .build();
        }
    }

    /**
     * Get KYC statistics for admin dashboard - Returns KycStatisticsResponseDto
     */
    public KycStatisticsResponseDto getKycStatistics() {
        long pendingRenter = renterKycRepository.countByKycStatus(RenterKyc.KycStatus.SUBMITTED);
        long verifiedRenter = renterKycRepository.countByKycStatus(RenterKyc.KycStatus.VERIFIED);
        long rejectedRenter = renterKycRepository.countByKycStatus(RenterKyc.KycStatus.REJECTED);

        long pendingOwner = ownerKycRepository.countByKycStatus(OwnerKyc.KycStatus.SUBMITTED);
        long verifiedOwner = ownerKycRepository.countByKycStatus(OwnerKyc.KycStatus.VERIFIED);
        long rejectedOwner = ownerKycRepository.countByKycStatus(OwnerKyc.KycStatus.REJECTED);

        return KycStatisticsResponseDto.builder()
                .pendingRenterKyc(pendingRenter)
                .verifiedRenterKyc(verifiedRenter)
                .rejectedRenterKyc(rejectedRenter)
                .pendingOwnerKyc(pendingOwner)
                .verifiedOwnerKyc(verifiedOwner)
                .rejectedOwnerKyc(rejectedOwner)
                .totalPending(pendingRenter + pendingOwner)
                .totalVerified(verifiedRenter + verifiedOwner)
                .totalRejected(rejectedRenter + rejectedOwner)
                .totalKycSubmissions(pendingRenter + verifiedRenter + rejectedRenter +
                        pendingOwner + verifiedOwner + rejectedOwner)
                .build();
    }

    /**
     * Get verified users list
     */
    public List<AdminKycResponseDto> getVerifiedUsers() {
        List<AdminKycResponseDto> verifiedUsers = new ArrayList<>();

        List<RenterKyc> verifiedRenters = renterKycRepository.findByKycStatus(RenterKyc.KycStatus.VERIFIED);
        for (RenterKyc kyc : verifiedRenters) {
            verifiedUsers.add(mapToAdminResponse(kyc.getUser(), "RENTER", kyc));
        }

        List<OwnerKyc> verifiedOwners = ownerKycRepository.findByKycStatus(OwnerKyc.KycStatus.VERIFIED);
        for (OwnerKyc kyc : verifiedOwners) {
            verifiedUsers.add(mapToAdminResponse(kyc.getUser(), "OWNER", kyc));
        }

        return verifiedUsers;
    }

    /**
     * Get rejected KYC list
     */
    public List<AdminKycResponseDto> getRejectedKyc() {
        List<AdminKycResponseDto> rejectedKyc = new ArrayList<>();

        List<RenterKyc> rejectedRenters = renterKycRepository.findByKycStatus(RenterKyc.KycStatus.REJECTED);
        for (RenterKyc kyc : rejectedRenters) {
            rejectedKyc.add(mapToAdminResponse(kyc.getUser(), "RENTER", kyc));
        }

        List<OwnerKyc> rejectedOwners = ownerKycRepository.findByKycStatus(OwnerKyc.KycStatus.REJECTED);
        for (OwnerKyc kyc : rejectedOwners) {
            rejectedKyc.add(mapToAdminResponse(kyc.getUser(), "OWNER", kyc));
        }

        return rejectedKyc;
    }

    // ==================== PRIVATE HELPER METHODS ====================

    private AdminKycResponseDto mapToAdminResponse(User user, String kycType, RenterKyc kyc) {
        return AdminKycResponseDto.builder()
                .id(kyc.getId())
                .userId(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .fullName(kyc.getFullName())
                .phoneNumber(kyc.getPhoneNumber())
                .kycStatus(kyc.getKycStatus().name())
                .kycType(kycType)
                .submittedAt(kyc.getSubmittedAt() != null ? kyc.getSubmittedAt().toString() : null)
                .action(kyc.getKycStatus().name())
                .build();
    }

    private AdminKycResponseDto mapToAdminResponse(User user, String kycType, OwnerKyc kyc) {
        return AdminKycResponseDto.builder()
                .id(kyc.getId())
                .userId(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .fullName(kyc.getFullName())
                .phoneNumber(kyc.getPhoneNumber())
                .kycStatus(kyc.getKycStatus().name())
                .kycType(kycType)
                .submittedAt(kyc.getSubmittedAt() != null ? kyc.getSubmittedAt().toString() : null)
                .action(kyc.getKycStatus().name())
                .build();
    }

    /**
     * Map RenterKyc to KycDetailsResponseDto
     */
    private KycDetailsResponseDto mapToKycDetailsResponse(RenterKyc kyc) {
        User user = kyc.getUser();

        return KycDetailsResponseDto.builder()
                .id(kyc.getId())
                .userId(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .kycType("RENTER")
                .kycStatus(kyc.getKycStatus().name())
                .fullName(kyc.getFullName())
                .dateOfBirth(kyc.getDateOfBirth())
                .gender(kyc.getGender())
                .phoneNumber(kyc.getPhoneNumber())
                .permanentAddress(kyc.getPermanentAddress())
                .temporaryAddress(kyc.getTemporaryAddress())
                .citizenshipNumber(kyc.getCitizenshipNumber())
                .citizenshipFrontImage(kyc.getCitizenshipFrontImage())
                .citizenshipBackImage(kyc.getCitizenshipBackImage())
                .drivingLicenseNumber(kyc.getDrivingLicenseNumber())
                .drivingLicenseIssueDate(kyc.getDrivingLicenseIssueDate())
                .drivingLicenseExpiryDate(kyc.getDrivingLicenseExpiryDate())
                .drivingLicenseImage(kyc.getDrivingLicenseImage())
                .submittedAt(kyc.getSubmittedAt())
                .verifiedAt(kyc.getVerifiedAt())
                .verifiedBy(kyc.getVerifiedBy())
                .rejectionReason(kyc.getRejectedReason())
                .build();
    }

    /**
     * Map OwnerKyc to KycDetailsResponseDto
     */
    private KycDetailsResponseDto mapToKycDetailsResponse(OwnerKyc kyc) {
        User user = kyc.getUser();

        return KycDetailsResponseDto.builder()
                .id(kyc.getId())
                .userId(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .kycType("OWNER")
                .kycStatus(kyc.getKycStatus().name())
                .fullName(kyc.getFullName())
                .dateOfBirth(kyc.getDateOfBirth())
                .phoneNumber(kyc.getPhoneNumber())
                .permanentAddress(kyc.getPermanentAddress())
                .citizenshipNumber(kyc.getCitizenshipNumber())
                .citizenshipFrontImage(kyc.getCitizenshipFrontImage())
                .citizenshipBackImage(kyc.getCitizenshipBackImage())
                .drivingLicenseNumber(kyc.getDrivingLicenseNumber())
                .drivingLicenseExpiryDate(kyc.getDrivingLicenseExpiryDate())
                .drivingLicenseImage(kyc.getDrivingLicenseImage())
                .vehicleBluebookNumber(kyc.getVehicleBluebookNumber())
                .vehicleBluebookImage(kyc.getVehicleBluebookImage())
                .ownershipProofVerified(kyc.getOwnershipProofVerified())
                .panNumber(kyc.getPanNumber())
                .bankAccountNumber(kyc.getBankAccountNumber())
                .bankName(kyc.getBankName())
                .bankAccountHolderName(kyc.getBankAccountHolderName())
                .submittedAt(kyc.getSubmittedAt())
                .verifiedAt(kyc.getVerifiedAt())
                .verifiedBy(kyc.getVerifiedBy())
                .rejectionReason(kyc.getRejectedReason())
                .build();
    }
}