package com.mobilityhub.repository;

import com.mobilityhub.model.LocationTracking;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface LocationTrackingRepository extends JpaRepository<LocationTracking, Long> {

    Optional<LocationTracking> findTopByBookingIdOrderByRecordedAtDesc(Long bookingId);

    List<LocationTracking> findByBookingIdOrderByRecordedAtAsc(Long bookingId, Pageable pageable);

    List<LocationTracking> findByRenterIdOrderByRecordedAtDesc(Long renterId, Pageable pageable);

    void deleteByBookingId(Long bookingId);
}