// repository/OwnerKycRepository.java - Simplified version
package com.mobilityhub.repository;

import com.mobilityhub.model.OwnerKyc;
import com.mobilityhub.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface OwnerKycRepository extends JpaRepository<OwnerKyc, Long> {

    // Find by user
    Optional<OwnerKyc> findByUser(User user);

    // Find by user ID
    Optional<OwnerKyc> findByUserId(Long userId);

    // Check if user has KYC
    boolean existsByUserId(Long userId);

    // Check if user has verified KYC
    boolean existsByUserIdAndKycStatus(Long userId, OwnerKyc.KycStatus status);

    // Find all by status (for admin)
    List<OwnerKyc> findByKycStatus(OwnerKyc.KycStatus status);

    // Find all pending KYC (for admin dashboard)
    List<OwnerKyc> findByKycStatusOrderBySubmittedAtAsc(OwnerKyc.KycStatus status);

    // Count by status (for admin stats)
    long countByKycStatus(OwnerKyc.KycStatus status);

    // Find by Bluebook number (for duplicate check)
    Optional<OwnerKyc> findByVehicleBluebookNumber(String bluebookNumber);

    // REMOVE this line if it exists:
    // List<OwnerKyc> findByOwnershipProofVerifiedFalse();
}