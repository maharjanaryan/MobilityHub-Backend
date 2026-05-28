// repository/RenterKycRepository.java
package com.mobilityhub.repository;

import com.mobilityhub.model.RenterKyc;
import com.mobilityhub.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface RenterKycRepository extends JpaRepository<RenterKyc, Long> {

    // Find by user
    Optional<RenterKyc> findByUser(User user);

    // Find by user ID
    Optional<RenterKyc> findByUserId(Long userId);

    // Check if user has KYC
    boolean existsByUserId(Long userId);

    // Check if user has verified KYC
    boolean existsByUserIdAndKycStatus(Long userId, RenterKyc.KycStatus status);

    // Find all by status (for admin)
    List<RenterKyc> findByKycStatus(RenterKyc.KycStatus status);

    // Find all pending KYC (for admin dashboard)
    List<RenterKyc> findByKycStatusOrderBySubmittedAtAsc(RenterKyc.KycStatus status);

    // Count by status (for admin stats)
    long countByKycStatus(RenterKyc.KycStatus status);

    // Find by verification date range
    List<RenterKyc> findByVerifiedAtBetween(LocalDateTime start, LocalDateTime end);
}