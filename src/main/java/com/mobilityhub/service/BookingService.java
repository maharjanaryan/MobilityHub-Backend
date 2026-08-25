// com/mobilityhub/service/BookingService.java
package com.mobilityhub.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mobilityhub.dto.request.BookingActionRequestDto;
import com.mobilityhub.dto.request.BookingRequestDto;
import com.mobilityhub.dto.request.ConfirmReturnRequestDto;
import com.mobilityhub.dto.response.BookingResponseDto;
import com.mobilityhub.dto.response.BookingSummaryDto;
import com.mobilityhub.dto.response.VehicleAvailabilityStatusDto;
import com.mobilityhub.dto.response.VehicleBookingStatusDto;
import com.mobilityhub.model.*;
import com.mobilityhub.repository.BookingRepository;
import com.mobilityhub.repository.OwnerKycRepository;
import com.mobilityhub.repository.UserRepository;
import com.mobilityhub.repository.VehicleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class BookingService {

    private final BookingRepository bookingRepository;
    private final UserRepository userRepository;
    private final VehicleRepository vehicleRepository;
    private final OwnerKycRepository ownerKycRepository;
    private final NotificationService notificationService;
    private final ObjectMapper objectMapper;

    private static final BigDecimal PREMIUM_INSURANCE_COST = BigDecimal.valueOf(45);
    private static final BigDecimal STANDARD_INSURANCE_COST = BigDecimal.valueOf(22);

    private boolean hasVerifiedOwnerKyc(Long userId) {
        return ownerKycRepository.existsByUserIdAndKycStatus(userId, OwnerKyc.KycStatus.VERIFIED);
    }

    // ─────────────────────────────────────────────
    // CREATE BOOKING
    // ─────────────────────────────────────────────

    @Transactional
    public BookingResponseDto createBooking(Long renterId, BookingRequestDto request) {

        User renter = userRepository.findById(renterId)
                .orElseThrow(() -> new RuntimeException("User not found"));
        Vehicle vehicle = vehicleRepository.findById(request.getVehicleId())
                .orElseThrow(() -> new RuntimeException("Vehicle not found"));

        if (!vehicle.getIsAvailable()) {
            throw new RuntimeException("Vehicle is not available for booking");
        }

        LocalDate pickupLocalDate;
        LocalDate dropoffLocalDate;

        try {
            String pickupDateStr = request.getPickupDate().toString().substring(0, 10);
            String dropoffDateStr = request.getDropoffDate().toString().substring(0, 10);
            pickupLocalDate = LocalDate.parse(pickupDateStr);
            dropoffLocalDate = LocalDate.parse(dropoffDateStr);
        } catch (Exception e) {
            log.error("Failed to parse dates: pickup={}, dropoff={}",
                    request.getPickupDate(), request.getDropoffDate(), e);
            throw new RuntimeException("Invalid date format. Please select valid dates.");
        }

        LocalDateTime pickupDate = pickupLocalDate.atStartOfDay();
        LocalDateTime dropoffDate = dropoffLocalDate.atStartOfDay();

        ZoneId nepalZone = ZoneId.of("Asia/Kathmandu");
        LocalDate todayInNepal = LocalDate.now(nepalZone);
        LocalDate tomorrowInNepal = todayInNepal.plusDays(1);

        if (pickupLocalDate.isBefore(tomorrowInNepal)) {
            throw new RuntimeException("Pickup date must be at least tomorrow (Nepal time). Selected: " + pickupLocalDate);
        }
        if (!dropoffLocalDate.isAfter(pickupLocalDate)) {
            throw new RuntimeException("Dropoff date must be after pickup date");
        }
        if (bookingRepository.isVehicleBooked(vehicle.getId(), pickupDate, dropoffDate)) {
            throw new RuntimeException("Selected dates are not available. Please choose different dates.");
        }

        long days = ChronoUnit.DAYS.between(pickupDate, dropoffDate);

        if (vehicle.getMinRentalDays() != null && days < vehicle.getMinRentalDays()) {
            throw new RuntimeException("Minimum rental period is " + vehicle.getMinRentalDays() + " day(s)");
        }
        if (vehicle.getMaxRentalDays() != null && days > vehicle.getMaxRentalDays()) {
            throw new RuntimeException("Maximum rental period is " + vehicle.getMaxRentalDays() + " day(s)");
        }

        BigDecimal dailyRate = vehicle.getPricePerDay();
        BigDecimal rentalAmount = dailyRate.multiply(BigDecimal.valueOf(days));

        BigDecimal insuranceCost = BigDecimal.ZERO;
        if ("premium".equalsIgnoreCase(request.getInsuranceType())) {
            insuranceCost = PREMIUM_INSURANCE_COST.multiply(BigDecimal.valueOf(days));
        } else if ("standard".equalsIgnoreCase(request.getInsuranceType())) {
            insuranceCost = STANDARD_INSURANCE_COST.multiply(BigDecimal.valueOf(days));
        }

        BigDecimal totalAmount = rentalAmount.add(insuranceCost);

        boolean duplicatePending = bookingRepository
                .findByVehicleIdAndBookingStatusIn(vehicle.getId(), List.of(Booking.BookingStatus.PENDING))
                .stream()
                .anyMatch(b -> b.getRenter().getId().equals(renterId)
                        && b.getPickupDate().toLocalDate().equals(pickupLocalDate)
                        && b.getDropoffDate().toLocalDate().equals(dropoffLocalDate));

        if (duplicatePending) {
            throw new RuntimeException("You already have a pending booking request for these dates");
        }

        String bookingReference = generateBookingReference();

        Booking booking = Booking.builder()
                .bookingReference(bookingReference)
                .renter(renter)
                .vehicle(vehicle)
                .owner(vehicle.getOwner())
                .pickupDate(pickupDate)
                .dropoffDate(dropoffDate)
                .totalDays((int) days)
                .dailyRate(dailyRate)
                .totalAmount(totalAmount)
                .securityDeposit(vehicle.getSecurityDeposit())
                .insuranceType(request.getInsuranceType() == null ? "standard" : request.getInsuranceType().toLowerCase())
                .insuranceCost(insuranceCost)
                .paymentStatus(Booking.PaymentStatus.PENDING)
                .bookingStatus(Booking.BookingStatus.PENDING)
                .paymentMethod(request.getPaymentMethod())
                .driverName(request.getDriverName())
                .driverLicenseNumber(request.getDriverLicenseNumber())
                .renterLocation(request.getRenterLocation())
                .renterLatitude(request.getRenterLatitude())
                .renterLongitude(request.getRenterLongitude())
                .build();

        Booking saved = bookingRepository.save(booking);
        log.info("Booking created: {} with renter location: {}", bookingReference, request.getRenterLocation());

        notificationService.createNotification(
                vehicle.getOwner(),
                "New Booking Request",
                renter.getFullName() + " has requested to book your vehicle " + vehicle.getBrand() + " " + vehicle.getModel(),
                Notification.NotificationType.BOOKING_REQUEST,
                saved.getId()
        );

        notificationService.createNotification(
                renter,
                "Booking Request Submitted",
                "Your booking request for " + vehicle.getBrand() + " " + vehicle.getModel() + " has been submitted.",
                Notification.NotificationType.BOOKING_SUBMITTED,
                saved.getId()
        );

        return mapToResponseDto(saved);
    }

    // ─────────────────────────────────────────────
    // APPROVE / REJECT / CANCEL BOOKING
    // ─────────────────────────────────────────────

    @Transactional
    public BookingResponseDto approveBooking(Long bookingId, Long ownerId, BookingActionRequestDto request) {
        if (!hasVerifiedOwnerKyc(ownerId)) {
            throw new RuntimeException("Owner KYC verification required to approve bookings");
        }

        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new RuntimeException("Booking not found"));

        if (!booking.getOwner().getId().equals(ownerId)) {
            throw new RuntimeException("Only the vehicle owner can approve this booking");
        }

        if (booking.getPaymentStatus() != Booking.PaymentStatus.COMPLETED) {
            throw new RuntimeException("Cannot approve booking. Payment has not been completed yet.");
        }

        if (booking.getBookingStatus() != Booking.BookingStatus.PENDING) {
            throw new RuntimeException("Booking has already been " + booking.getBookingStatus().name().toLowerCase());
        }

        if (bookingRepository.isVehicleBooked(
                booking.getVehicle().getId(),
                booking.getPickupDate(),
                booking.getDropoffDate())) {
            booking.setBookingStatus(Booking.BookingStatus.REJECTED);
            booking.setRejectionReason("Vehicle became unavailable for the selected dates");
            bookingRepository.save(booking);
            throw new RuntimeException("Vehicle is no longer available for the selected dates");
        }

        booking.setBookingStatus(Booking.BookingStatus.CONFIRMED);
        booking.setApprovedBy(ownerId);
        booking.setApprovedAt(LocalDateTime.now());
        booking.setOwnerNotes(request != null ? request.getOwnerNotes() : null);

        Booking saved = bookingRepository.save(booking);

        log.info("✅ Booking {} approved by owner {} after payment confirmation", bookingId, ownerId);

        notificationService.createNotification(
                booking.getRenter(),
                "🎉 Booking Confirmed!",
                "Your booking for " + booking.getVehicle().getBrand() + " " + booking.getVehicle().getModel() + " has been confirmed by the owner.",
                Notification.NotificationType.BOOKING_CONFIRMED,
                booking.getId()
        );

        return mapToResponseDto(saved);
    }

    @Transactional
    public BookingResponseDto rejectBooking(Long bookingId, Long ownerId, BookingActionRequestDto request) {
        if (!hasVerifiedOwnerKyc(ownerId)) {
            throw new RuntimeException("Owner KYC verification required to reject bookings");
        }

        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new RuntimeException("Booking not found"));

        if (!booking.getOwner().getId().equals(ownerId)) {
            throw new RuntimeException("Only the vehicle owner can reject this booking");
        }
        if (booking.getBookingStatus() != Booking.BookingStatus.PENDING) {
            throw new RuntimeException("Booking has already been " + booking.getBookingStatus().name().toLowerCase());
        }

        booking.setBookingStatus(Booking.BookingStatus.REJECTED);
        booking.setRejectionReason(request.getRejectionReason());

        Booking saved = bookingRepository.save(booking);

        if (booking.getPaymentStatus() == Booking.PaymentStatus.COMPLETED) {
            booking.setPaymentStatus(Booking.PaymentStatus.REFUNDED);
            bookingRepository.save(booking);

            notificationService.createNotification(
                    booking.getRenter(),
                    "⚠️ Booking Rejected - Refund Initiated",
                    "Your booking for " + booking.getVehicle().getBrand() + " " + booking.getVehicle().getModel() +
                            " was rejected. Your payment will be refunded.",
                    Notification.NotificationType.BOOKING_REJECTED,
                    booking.getId()
            );
        }

        notificationService.createNotification(
                booking.getRenter(),
                "Booking Rejected",
                "Your booking request for " + booking.getVehicle().getBrand() + " " + booking.getVehicle().getModel() + " has been rejected.",
                Notification.NotificationType.BOOKING_REJECTED,
                booking.getId()
        );

        return mapToResponseDto(saved);
    }

    @Transactional
    public BookingResponseDto cancelBooking(Long bookingId, Long renterId) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new RuntimeException("Booking not found"));

        if (!booking.getRenter().getId().equals(renterId)) {
            throw new RuntimeException("Only the renter can cancel this booking");
        }
        if (booking.getBookingStatus() == Booking.BookingStatus.COMPLETED) {
            throw new RuntimeException("Cannot cancel a completed booking");
        }
        if (booking.getBookingStatus() == Booking.BookingStatus.CANCELLED) {
            throw new RuntimeException("Booking is already cancelled");
        }

        booking.setBookingStatus(Booking.BookingStatus.CANCELLED);

        if (booking.getPaymentStatus() == Booking.PaymentStatus.COMPLETED) {
            booking.setPaymentStatus(Booking.PaymentStatus.REFUNDED);
        }

        Booking saved = bookingRepository.save(booking);

        notificationService.createNotification(
                booking.getRenter(),
                "Booking Cancelled",
                "Your booking has been cancelled successfully.",
                Notification.NotificationType.BOOKING_CANCELLED,
                booking.getId()
        );

        return mapToResponseDto(saved);
    }

    // ─────────────────────────────────────────────
    // TRIP MANAGEMENT (START / END TRIP)
    // ─────────────────────────────────────────────

    @Transactional
    public BookingResponseDto startTrip(Long bookingId, Long userId) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new RuntimeException("Booking not found"));

        boolean isRenter = booking.getRenter().getId().equals(userId);
        boolean isOwner = booking.getOwner().getId().equals(userId);

        if (!isRenter && !isOwner) {
            throw new RuntimeException("You don't have permission to start this trip");
        }

        if (booking.getBookingStatus() != Booking.BookingStatus.CONFIRMED) {
            throw new RuntimeException("Only confirmed bookings can be started. Current status: " + booking.getBookingStatus());
        }

        LocalDateTime now = LocalDateTime.now();
        if (now.isBefore(booking.getPickupDate())) {
            throw new RuntimeException("Cannot start trip before pickup date: " + booking.getPickupDate());
        }

        booking.setBookingStatus(Booking.BookingStatus.ACTIVE);
        booking.setTripStartedAt(now);
        Booking saved = bookingRepository.save(booking);

        log.info("🚗 Trip started for booking {} by user {}", bookingId, userId);

        notificationService.createNotification(
                booking.getRenter(),
                "Trip Started! 🚗",
                "Your trip with " + booking.getVehicle().getBrand() + " " + booking.getVehicle().getModel() + " has started. Drive safe!",
                Notification.NotificationType.BOOKING_CONFIRMED,
                booking.getId()
        );

        return mapToResponseDto(saved);
    }

    @Transactional
    public BookingResponseDto endTrip(Long bookingId, Long userId) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new RuntimeException("Booking not found"));

        boolean isRenter = booking.getRenter().getId().equals(userId);
        boolean isOwner = booking.getOwner().getId().equals(userId);

        if (!isRenter && !isOwner) {
            throw new RuntimeException("You don't have permission to end this trip");
        }

        if (booking.getBookingStatus() != Booking.BookingStatus.ACTIVE) {
            throw new RuntimeException("Only active trips can be ended. Current status: " + booking.getBookingStatus());
        }

        // Set to AWAITING_RETURN_CONFIRMATION instead of COMPLETED
        booking.setBookingStatus(Booking.BookingStatus.AWAITING_RETURN_CONFIRMATION);
        booking.setTripEndedAt(LocalDateTime.now());
        Booking saved = bookingRepository.save(booking);

        log.info("🏁 Trip ended for booking {} by user {}, awaiting owner confirmation", bookingId, userId);

        // Notify owner - they need to confirm return
        notificationService.createNotification(
                booking.getOwner(),
                "🚗 Trip Ended - Confirm Vehicle Return",
                String.format(
                        "%s has ended the trip with your vehicle %s %s (%s). Please inspect the vehicle and confirm return to release the security deposit.",
                        booking.getRenter().getFullName(),
                        booking.getVehicle().getBrand(),
                        booking.getVehicle().getModel(),
                        booking.getVehicle().getLicensePlate()
                ),
                Notification.NotificationType.TRIP_ENDED_AWAITING_CONFIRMATION,
                booking.getId()
        );

        // Notify renter - waiting for owner confirmation
        notificationService.createNotification(
                booking.getRenter(),
                "Trip Ended - Awaiting Owner Confirmation",
                String.format(
                        "You have ended your trip with %s %s. The owner needs to inspect and confirm the vehicle return. You'll receive your security deposit once confirmed.",
                        booking.getVehicle().getBrand(),
                        booking.getVehicle().getModel()
                ),
                Notification.NotificationType.TRIP_ENDED_AWAITING_CONFIRMATION,
                booking.getId()
        );

        return mapToResponseDto(saved);
    }

    // ─────────────────────────────────────────────
    // CONFIRM VEHICLE RETURN
    // ─────────────────────────────────────────────

    @Transactional
    public BookingResponseDto confirmVehicleReturn(Long bookingId, Long ownerId, ConfirmReturnRequestDto request) {
        if (!hasVerifiedOwnerKyc(ownerId)) {
            throw new RuntimeException("Owner KYC verification required to confirm vehicle return");
        }

        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new RuntimeException("Booking not found"));

        if (!booking.getOwner().getId().equals(ownerId)) {
            throw new RuntimeException("Only the vehicle owner can confirm vehicle return");
        }

        if (booking.getBookingStatus() != Booking.BookingStatus.AWAITING_RETURN_CONFIRMATION) {
            throw new RuntimeException("Cannot confirm return. Booking status is: " + booking.getBookingStatus());
        }

        // Mark as completed
        booking.setBookingStatus(Booking.BookingStatus.COMPLETED);
        booking.setVehicleReturnedAt(LocalDateTime.now());

        // Handle damage report
        Boolean isDamaged = request.getVehicleDamaged() != null && request.getVehicleDamaged();
        booking.setVehicleDamaged(isDamaged);
        booking.setDamageNotes(request.getDamageNotes());

        // Security deposit handling
        if (!isDamaged) {
            // Full security deposit returned
            booking.setSecurityDepositReturned(true);
            booking.setSecurityDepositReturnedAt(LocalDateTime.now());
            booking.setSecurityDepositReturnedAmount(booking.getSecurityDeposit());

            log.info("✅ Security deposit of Rs. {} released for booking {}", booking.getSecurityDeposit(), bookingId);
        } else {
            // Damaged - deposit not returned (or partial)
            booking.setSecurityDepositReturned(false);
            log.info("⚠️ Security deposit held for booking {} due to damage: {}", bookingId, request.getDamageNotes());
        }

        Booking saved = bookingRepository.save(booking);

        log.info("✅ Vehicle return confirmed for booking {} by owner {}", bookingId, ownerId);

        // Notify renter
        if (!isDamaged) {
            notificationService.createNotification(
                    booking.getRenter(),
                    "✅ Vehicle Return Confirmed - Deposit Released",
                    String.format(
                            "The owner has confirmed the return of %s %s. Your security deposit of Rs. %.2f has been released to your account.",
                            booking.getVehicle().getBrand(),
                            booking.getVehicle().getModel(),
                            booking.getSecurityDeposit()
                    ),
                    Notification.NotificationType.VEHICLE_RETURN_CONFIRMED,
                    booking.getId()
            );
        } else {
            notificationService.createNotification(
                    booking.getRenter(),
                    "⚠️ Vehicle Return Confirmed - Deposit On Hold",
                    String.format(
                            "The owner has confirmed the return of %s %s but reported damage. Your security deposit will be held for damage assessment. Reason: %s",
                            booking.getVehicle().getBrand(),
                            booking.getVehicle().getModel(),
                            request.getDamageNotes() != null ? request.getDamageNotes() : "No details provided"
                    ),
                    Notification.NotificationType.VEHICLE_RETURN_CONFIRMED,
                    booking.getId()
            );
        }

        // Also notify owner that confirmation is complete
        notificationService.createNotification(
                booking.getOwner(),
                "✅ Vehicle Return Confirmed",
                String.format(
                        "You have successfully confirmed the return of %s %s. The booking is now completed.",
                        booking.getVehicle().getBrand(),
                        booking.getVehicle().getModel()
                ),
                Notification.NotificationType.VEHICLE_RETURN_CONFIRMED,
                booking.getId()
        );

        return mapToResponseDto(saved);
    }

    // ─────────────────────────────────────────────
    // READ METHODS
    // ─────────────────────────────────────────────

    public BookingResponseDto getBookingById(Long bookingId, Long userId, String userRole) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new RuntimeException("Booking not found"));

        boolean isAdmin = "ROLE_ADMIN".equals(userRole) || "ADMIN".equals(userRole);
        boolean isRenter = booking.getRenter().getId().equals(userId);
        boolean isOwner = booking.getOwner().getId().equals(userId);

        if (!isAdmin && !isRenter && !isOwner) {
            throw new RuntimeException("You don't have permission to view this booking");
        }
        return mapToResponseDto(booking);
    }

    public Page<BookingResponseDto> getBookingsByRenter(Long renterId, String status, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<Booking> bookings = (status != null && !status.equalsIgnoreCase("all"))
                ? bookingRepository.findByRenterIdAndBookingStatus(
                renterId, Booking.BookingStatus.valueOf(status.toUpperCase()), pageable)
                : bookingRepository.findByRenterId(renterId, pageable);
        return bookings.map(this::mapToResponseDto);
    }

    public Page<BookingResponseDto> getBookingsByOwner(Long ownerId, String status, int page, int size) {
        if (!hasVerifiedOwnerKyc(ownerId)) {
            throw new RuntimeException("Owner KYC verification required to manage bookings");
        }
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<Booking> bookings = (status != null && !status.equalsIgnoreCase("all"))
                ? bookingRepository.findByOwnerIdAndBookingStatus(
                ownerId, Booking.BookingStatus.valueOf(status.toUpperCase()), pageable)
                : bookingRepository.findByOwnerId(ownerId, pageable);
        return bookings.map(this::mapToResponseDto);
    }

    public List<BookingResponseDto> getPendingBookingsForOwner(Long ownerId) {
        if (!hasVerifiedOwnerKyc(ownerId)) {
            throw new RuntimeException("Owner KYC verification required to view pending bookings");
        }
        return bookingRepository
                .findByOwnerIdAndBookingStatusOrderByCreatedAtAsc(ownerId, Booking.BookingStatus.PENDING)
                .stream().map(this::mapToResponseDto).collect(Collectors.toList());
    }

    public boolean checkAvailability(Long vehicleId, LocalDateTime pickupDate, LocalDateTime dropoffDate) {
        LocalDateTime p = pickupDate.toLocalDate().atStartOfDay();
        LocalDateTime d = dropoffDate.toLocalDate().atStartOfDay();
        return !bookingRepository.isVehicleBooked(vehicleId, p, d);
    }

    public List<LocalDateTime> getBookedDatesForVehicle(Long vehicleId) {
        List<Booking> bookings = bookingRepository.findByVehicleIdAndBookingStatusIn(
                vehicleId,
                List.of(Booking.BookingStatus.CONFIRMED, Booking.BookingStatus.ACTIVE)
        );

        ZoneId nepalZone = ZoneId.of("Asia/Kathmandu");
        LocalDate today = LocalDate.now(nepalZone);
        Set<LocalDateTime> bookedDates = new HashSet<>();

        for (Booking b : bookings) {
            LocalDate start = b.getPickupDate().toLocalDate();
            LocalDate end = b.getDropoffDate().toLocalDate();
            LocalDate cur = start.isBefore(today) ? today : start;
            while (cur.isBefore(end)) {
                bookedDates.add(cur.atStartOfDay());
                cur = cur.plusDays(1);
            }
        }
        return new ArrayList<>(bookedDates);
    }

    // ─────────────────────────────────────────────
    // OWNER VEHICLE STATUS METHODS
    // ─────────────────────────────────────────────

    public List<VehicleBookingStatusDto> getOwnersBookedVehicles(Long ownerId) {
        log.info("Fetching booking status for all vehicles owned by user: {}", ownerId);

        if (!hasVerifiedOwnerKyc(ownerId)) {
            throw new RuntimeException("Owner KYC verification required to view vehicle booking status");
        }

        List<Vehicle> ownerVehicles = vehicleRepository.findByOwnerId(ownerId);

        if (ownerVehicles.isEmpty()) {
            log.info("No vehicles found for owner: {}", ownerId);
            return Collections.emptyList();
        }

        LocalDateTime now = LocalDateTime.now();
        List<Booking.BookingStatus> activeStatuses = List.of(
                Booking.BookingStatus.CONFIRMED,
                Booking.BookingStatus.ACTIVE,
                Booking.BookingStatus.AWAITING_RETURN_CONFIRMATION
        );

        return ownerVehicles.stream()
                .map(vehicle -> {
                    List<Booking> activeBookings = bookingRepository.findByVehicleIdAndBookingStatusIn(
                            vehicle.getId(),
                            activeStatuses
                    );

                    List<Booking> currentActiveBookings = activeBookings.stream()
                            .filter(b ->
                                    b.getPickupDate().isBefore(now) &&
                                            b.getDropoffDate().isAfter(now)
                            )
                            .collect(Collectors.toList());

                    LocalDateTime nextAvailableDate = null;
                    if (!activeBookings.isEmpty()) {
                        LocalDateTime lastDropoff = activeBookings.stream()
                                .filter(b -> b.getDropoffDate().isAfter(now))
                                .map(Booking::getDropoffDate)
                                .max(LocalDateTime::compareTo)
                                .orElse(null);
                        nextAvailableDate = lastDropoff;
                    }

                    boolean isCurrentlyBooked = !currentActiveBookings.isEmpty();

                    return VehicleBookingStatusDto.builder()
                            .vehicleId(vehicle.getId())
                            .vehicleName(vehicle.getBrand() + " " + vehicle.getModel())
                            .licensePlate(vehicle.getLicensePlate())
                            .brand(vehicle.getBrand())
                            .model(vehicle.getModel())
                            .year(vehicle.getYear())
                            .isCurrentlyBooked(isCurrentlyBooked)
                            .activeBookings(currentActiveBookings.stream()
                                    .map(this::mapToBookingSummaryDto)
                                    .collect(Collectors.toList()))
                            .nextAvailableDate(nextAvailableDate)
                            .totalActiveBookings(currentActiveBookings.size())
                            .build();
                })
                .collect(Collectors.toList());
    }

    public List<VehicleAvailabilityStatusDto> getOwnersVehicleAvailability(Long ownerId) {
        log.info("Fetching availability status for all vehicles owned by user: {}", ownerId);

        if (!hasVerifiedOwnerKyc(ownerId)) {
            throw new RuntimeException("Owner KYC verification required to view vehicle availability");
        }

        List<Vehicle> ownerVehicles = vehicleRepository.findByOwnerId(ownerId);

        if (ownerVehicles.isEmpty()) {
            log.info("No vehicles found for owner: {}", ownerId);
            return Collections.emptyList();
        }

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime thirtyDaysFromNow = now.plusDays(30);
        List<Booking.BookingStatus> relevantStatuses = List.of(
                Booking.BookingStatus.PENDING,
                Booking.BookingStatus.CONFIRMED,
                Booking.BookingStatus.ACTIVE,
                Booking.BookingStatus.AWAITING_RETURN_CONFIRMATION
        );

        return ownerVehicles.stream()
                .map(vehicle -> {
                    List<Booking> upcomingBookings = bookingRepository
                            .findActiveBookingsForVehicleInDateRange(
                                    vehicle.getId(),
                                    relevantStatuses,
                                    now,
                                    thirtyDaysFromNow
                            );

                    double availabilityPercentage = calculateAvailabilityPercentage(
                            upcomingBookings,
                            now,
                            thirtyDaysFromNow
                    );

                    boolean isAvailable = upcomingBookings.isEmpty() ||
                            upcomingBookings.stream().noneMatch(b ->
                                    b.getPickupDate().isBefore(now) &&
                                            b.getDropoffDate().isAfter(now)
                            );

                    LocalDateTime nextBookingDate = bookingRepository.findNextBookingDate(
                            vehicle.getId(),
                            relevantStatuses,
                            now
                    );

                    return VehicleAvailabilityStatusDto.builder()
                            .vehicleId(vehicle.getId())
                            .vehicleName(vehicle.getBrand() + " " + vehicle.getModel())
                            .licensePlate(vehicle.getLicensePlate())
                            .brand(vehicle.getBrand())
                            .model(vehicle.getModel())
                            .year(vehicle.getYear())
                            .isAvailable(isAvailable)
                            .availabilityPercentage(availabilityPercentage)
                            .totalUpcomingBookings(upcomingBookings.size())
                            .upcomingBookings(upcomingBookings.stream()
                                    .map(this::mapToBookingSummaryDto)
                                    .collect(Collectors.toList()))
                            .nextBookingDate(nextBookingDate)
                            .build();
                })
                .collect(Collectors.toList());
    }

    public Page<BookingResponseDto> getBookingsByOwnerAndVehicle(
            Long ownerId,
            Long vehicleId,
            String status,
            int page,
            int size) {

        log.info("Fetching bookings for owner: {}, vehicle: {}, status: {}", ownerId, vehicleId, status);

        if (!hasVerifiedOwnerKyc(ownerId)) {
            throw new RuntimeException("Owner KYC verification required to view vehicle bookings");
        }

        Vehicle vehicle = vehicleRepository.findById(vehicleId)
                .orElseThrow(() -> new RuntimeException("Vehicle not found"));

        if (!vehicle.getOwner().getId().equals(ownerId)) {
            throw new RuntimeException("You don't have permission to view bookings for this vehicle");
        }

        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));

        Page<Booking> bookings;
        if (status != null && !status.equalsIgnoreCase("all")) {
            bookings = bookingRepository.findByOwnerIdAndVehicleIdAndBookingStatus(
                    ownerId,
                    vehicleId,
                    Booking.BookingStatus.valueOf(status.toUpperCase()),
                    pageable
            );
        } else {
            bookings = bookingRepository.findByOwnerIdAndVehicleId(
                    ownerId,
                    vehicleId,
                    pageable
            );
        }

        return bookings.map(this::mapToResponseDto);
    }

    public Map<String, Object> getOwnerBookingSummary(Long ownerId) {
        log.info("Getting booking summary for owner: {}", ownerId);

        if (!hasVerifiedOwnerKyc(ownerId)) {
            throw new RuntimeException("Owner KYC verification required to view booking summary");
        }

        List<Vehicle> ownerVehicles = vehicleRepository.findByOwnerId(ownerId);

        long totalVehicles = ownerVehicles.size();
        long availableVehicles = ownerVehicles.stream()
                .filter(Vehicle::getIsAvailable)
                .count();

        LocalDateTime now = LocalDateTime.now();
        List<Booking.BookingStatus> activeStatuses = List.of(
                Booking.BookingStatus.CONFIRMED,
                Booking.BookingStatus.ACTIVE,
                Booking.BookingStatus.AWAITING_RETURN_CONFIRMATION
        );

        long currentlyBookedVehicles = ownerVehicles.stream()
                .filter(vehicle -> {
                    Integer count = bookingRepository.countActiveBookingsForVehicle(
                            vehicle.getId(),
                            activeStatuses,
                            now
                    );
                    return count != null && count > 0;
                })
                .count();

        long pendingBookings = bookingRepository.countByOwnerIdAndBookingStatus(
                ownerId, Booking.BookingStatus.PENDING
        );
        long confirmedBookings = bookingRepository.countByOwnerIdAndBookingStatus(
                ownerId, Booking.BookingStatus.CONFIRMED
        );
        long activeBookings = bookingRepository.countByOwnerIdAndBookingStatus(
                ownerId, Booking.BookingStatus.ACTIVE
        );
        long awaitingReturnBookings = bookingRepository.countByOwnerIdAndBookingStatus(
                ownerId, Booking.BookingStatus.AWAITING_RETURN_CONFIRMATION
        );
        long completedBookings = bookingRepository.countByOwnerIdAndBookingStatus(
                ownerId, Booking.BookingStatus.COMPLETED
        );
        long cancelledBookings = bookingRepository.countByOwnerIdAndBookingStatus(
                ownerId, Booking.BookingStatus.CANCELLED
        );
        long rejectedBookings = bookingRepository.countByOwnerIdAndBookingStatus(
                ownerId, Booking.BookingStatus.REJECTED
        );

        Double totalRevenue = bookingRepository.sumTotalAmountByOwnerIdAndBookingStatusIn(
                ownerId,
                List.of(Booking.BookingStatus.COMPLETED, Booking.BookingStatus.ACTIVE)
        );

        Double upcomingRevenue = bookingRepository.sumTotalAmountByOwnerIdAndBookingStatusIn(
                ownerId,
                List.of(Booking.BookingStatus.CONFIRMED)
        );

        Map<String, Object> summary = new HashMap<>();
        summary.put("totalVehicles", totalVehicles);
        summary.put("availableVehicles", availableVehicles);
        summary.put("currentlyBookedVehicles", currentlyBookedVehicles);
        summary.put("pendingBookings", pendingBookings);
        summary.put("confirmedBookings", confirmedBookings);
        summary.put("activeBookings", activeBookings);
        summary.put("awaitingReturnBookings", awaitingReturnBookings);
        summary.put("completedBookings", completedBookings);
        summary.put("cancelledBookings", cancelledBookings);
        summary.put("rejectedBookings", rejectedBookings);
        summary.put("totalRevenue", totalRevenue != null ? totalRevenue : 0.0);
        summary.put("upcomingRevenue", upcomingRevenue != null ? upcomingRevenue : 0.0);

        return summary;
    }

    // ─────────────────────────────────────────────
    // ADMIN METHODS
    // ─────────────────────────────────────────────

    public Page<BookingResponseDto> getAllBookingsForAdmin(String status, String paymentStatus, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));

        Page<Booking> bookingPage;

        if (status != null && !status.isEmpty() && !status.equalsIgnoreCase("all")) {
            Booking.BookingStatus bookingStatus = Booking.BookingStatus.valueOf(status.toUpperCase());
            if (paymentStatus != null && !paymentStatus.isEmpty() && !paymentStatus.equalsIgnoreCase("all")) {
                Booking.PaymentStatus paymentStatusEnum = Booking.PaymentStatus.valueOf(paymentStatus.toUpperCase());
                bookingPage = bookingRepository.findByBookingStatusAndPaymentStatus(bookingStatus, paymentStatusEnum, pageable);
            } else {
                bookingPage = bookingRepository.findByBookingStatus(bookingStatus, pageable);
            }
        } else if (paymentStatus != null && !paymentStatus.isEmpty() && !paymentStatus.equalsIgnoreCase("all")) {
            Booking.PaymentStatus paymentStatusEnum = Booking.PaymentStatus.valueOf(paymentStatus.toUpperCase());
            bookingPage = bookingRepository.findByPaymentStatus(paymentStatusEnum, pageable);
        } else {
            bookingPage = bookingRepository.findAll(pageable);
        }

        return bookingPage.map(this::mapToResponseDto);
    }

    public Map<String, Object> getBookingStats() {
        long totalBookings = bookingRepository.count();
        long pendingBookings = bookingRepository.countByBookingStatus(Booking.BookingStatus.PENDING);
        long confirmedBookings = bookingRepository.countByBookingStatus(Booking.BookingStatus.CONFIRMED);
        long activeBookings = bookingRepository.countByBookingStatus(Booking.BookingStatus.ACTIVE);
        long awaitingReturnBookings = bookingRepository.countByBookingStatus(Booking.BookingStatus.AWAITING_RETURN_CONFIRMATION);
        long completedBookings = bookingRepository.countByBookingStatus(Booking.BookingStatus.COMPLETED);
        long cancelledBookings = bookingRepository.countByBookingStatus(Booking.BookingStatus.CANCELLED);
        long rejectedBookings = bookingRepository.countByBookingStatus(Booking.BookingStatus.REJECTED);

        Double totalRevenue = bookingRepository.sumTotalAmountByBookingStatusIn(
                List.of(Booking.BookingStatus.COMPLETED, Booking.BookingStatus.ACTIVE)
        );

        Map<String, Object> stats = new HashMap<>();
        stats.put("total", totalBookings);
        stats.put("pending", pendingBookings);
        stats.put("confirmed", confirmedBookings);
        stats.put("ongoing", activeBookings);
        stats.put("awaitingReturn", awaitingReturnBookings);
        stats.put("completed", completedBookings);
        stats.put("cancelled", cancelledBookings);
        stats.put("rejected", rejectedBookings);
        stats.put("totalRevenue", totalRevenue != null ? totalRevenue : 0.0);

        return stats;
    }

    @Transactional
    public BookingResponseDto adminApproveBooking(Long bookingId) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new RuntimeException("Booking not found"));

        if (booking.getBookingStatus() != Booking.BookingStatus.PENDING) {
            throw new RuntimeException("Only pending bookings can be approved");
        }

        if (booking.getPaymentStatus() != Booking.PaymentStatus.COMPLETED) {
            throw new RuntimeException("Cannot approve booking. Payment not completed.");
        }

        booking.setBookingStatus(Booking.BookingStatus.CONFIRMED);
        booking.setApprovedAt(LocalDateTime.now());

        Booking savedBooking = bookingRepository.save(booking);

        notificationService.createNotification(
                booking.getRenter(),
                "Booking Approved by Admin",
                "Your booking for " + booking.getVehicle().getBrand() + " " + booking.getVehicle().getModel() + " has been approved by admin.",
                Notification.NotificationType.BOOKING_CONFIRMED,
                booking.getId()
        );

        return mapToResponseDto(savedBooking);
    }

    @Transactional
    public BookingResponseDto adminRejectBooking(Long bookingId, String reason) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new RuntimeException("Booking not found"));

        if (booking.getBookingStatus() != Booking.BookingStatus.PENDING) {
            throw new RuntimeException("Only pending bookings can be rejected");
        }

        booking.setBookingStatus(Booking.BookingStatus.REJECTED);
        if (reason != null && !reason.isEmpty()) {
            booking.setRejectionReason(reason);
        }

        if (booking.getPaymentStatus() == Booking.PaymentStatus.COMPLETED) {
            booking.setPaymentStatus(Booking.PaymentStatus.REFUNDED);
        }

        Booking savedBooking = bookingRepository.save(booking);

        notificationService.createNotification(
                booking.getRenter(),
                "Booking Rejected by Admin",
                "Your booking for " + booking.getVehicle().getBrand() + " " + booking.getVehicle().getModel() + " has been rejected by admin.",
                Notification.NotificationType.BOOKING_REJECTED,
                booking.getId()
        );

        return mapToResponseDto(savedBooking);
    }

    @Transactional
    public BookingResponseDto adminCancelBooking(Long bookingId, String reason) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new RuntimeException("Booking not found"));

        if (booking.getBookingStatus() == Booking.BookingStatus.COMPLETED) {
            throw new RuntimeException("Cannot cancel a completed booking");
        }
        if (booking.getBookingStatus() == Booking.BookingStatus.CANCELLED) {
            throw new RuntimeException("Booking is already cancelled");
        }

        booking.setBookingStatus(Booking.BookingStatus.CANCELLED);
        if (reason != null && !reason.isEmpty()) {
            booking.setRejectionReason(reason);
        }

        if (booking.getPaymentStatus() == Booking.PaymentStatus.COMPLETED) {
            booking.setPaymentStatus(Booking.PaymentStatus.REFUNDED);
        }

        Booking savedBooking = bookingRepository.save(booking);

        notificationService.createNotification(
                booking.getRenter(),
                "Booking Cancelled by Admin",
                "Your booking for " + booking.getVehicle().getBrand() + " " + booking.getVehicle().getModel() + " has been cancelled by admin.",
                Notification.NotificationType.BOOKING_CANCELLED,
                booking.getId()
        );

        return mapToResponseDto(savedBooking);
    }

    @Transactional
    public BookingResponseDto adminMarkAsOngoing(Long bookingId) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new RuntimeException("Booking not found"));

        if (booking.getBookingStatus() != Booking.BookingStatus.CONFIRMED) {
            throw new RuntimeException("Only confirmed bookings can be marked as ongoing");
        }

        booking.setBookingStatus(Booking.BookingStatus.ACTIVE);
        Booking savedBooking = bookingRepository.save(booking);

        notificationService.createNotification(
                booking.getRenter(),
                "Trip Started!",
                "Your trip with " + booking.getVehicle().getBrand() + " " + booking.getVehicle().getModel() + " has started.",
                Notification.NotificationType.BOOKING_CONFIRMED,
                booking.getId()
        );

        return mapToResponseDto(savedBooking);
    }

    @Transactional
    public BookingResponseDto adminMarkAsCompleted(Long bookingId) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new RuntimeException("Booking not found"));

        if (booking.getBookingStatus() != Booking.BookingStatus.ACTIVE) {
            throw new RuntimeException("Only active bookings can be marked as completed");
        }

        booking.setBookingStatus(Booking.BookingStatus.COMPLETED);
        Booking savedBooking = bookingRepository.save(booking);

        notificationService.createNotification(
                booking.getRenter(),
                "Trip Completed!",
                "Your trip with " + booking.getVehicle().getBrand() + " " + booking.getVehicle().getModel() + " has been completed.",
                Notification.NotificationType.BOOKING_COMPLETED,
                booking.getId()
        );

        return mapToResponseDto(savedBooking);
    }

    // ─────────────────────────────────────────────
    // HELPERS
    // ─────────────────────────────────────────────

    private String generateBookingReference() {
        return "BK" + System.currentTimeMillis()
                + UUID.randomUUID().toString().substring(0, 6).toUpperCase();
    }

    private String getFirstPhotoUrl(Vehicle vehicle) {
        if (vehicle.getPhotos() == null || vehicle.getPhotos().isEmpty()) return null;
        try {
            List<String> photos = objectMapper.readValue(
                    vehicle.getPhotos(), new TypeReference<List<String>>() {});
            return photos.isEmpty() ? null : photos.get(0);
        } catch (Exception e) {
            return vehicle.getPhotos();
        }
    }

    private double calculateAvailabilityPercentage(
            List<Booking> bookings,
            LocalDateTime startDate,
            LocalDateTime endDate) {

        if (bookings.isEmpty()) {
            return 100.0;
        }

        long totalDays = ChronoUnit.DAYS.between(startDate, endDate);
        if (totalDays <= 0) {
            return 0.0;
        }

        Set<LocalDate> bookedDays = new HashSet<>();
        for (Booking booking : bookings) {
            LocalDate bookingStart = booking.getPickupDate().toLocalDate();
            LocalDate bookingEnd = booking.getDropoffDate().toLocalDate();

            LocalDate start = bookingStart.isBefore(startDate.toLocalDate()) ?
                    startDate.toLocalDate() : bookingStart;
            LocalDate end = bookingEnd.isAfter(endDate.toLocalDate()) ?
                    endDate.toLocalDate() : bookingEnd;

            LocalDate current = start;
            while (current.isBefore(end) || current.equals(end)) {
                bookedDays.add(current);
                current = current.plusDays(1);
            }
        }

        long bookedDaysCount = bookedDays.size();
        long availableDays = totalDays - bookedDaysCount;

        return (double) availableDays / totalDays * 100;
    }

    private BookingSummaryDto mapToBookingSummaryDto(Booking booking) {
        return BookingSummaryDto.builder()
                .bookingId(booking.getId())
                .bookingReference(booking.getBookingReference())
                .renterName(booking.getRenter().getFullName())
                .renterEmail(booking.getRenter().getEmail())
                .renterPhone(booking.getRenter().getPhoneNumber())
                .pickupDate(booking.getPickupDate())
                .dropoffDate(booking.getDropoffDate())
                .status(booking.getBookingStatus().name())
                .totalAmount(booking.getTotalAmount())
                .totalDays(booking.getTotalDays())
                .paymentStatus(booking.getPaymentStatus().name())
                .paymentMethod(booking.getPaymentMethod())
                .build();
    }

    // ─────────────────────────────────────────────
    // MAP TO RESPONSE DTO
    // ─────────────────────────────────────────────

    private BookingResponseDto mapToResponseDto(Booking b) {
        Vehicle v = b.getVehicle();

        List<String> bluebookDocuments = new ArrayList<>();
        if (v.getBluebookDocument() != null && !v.getBluebookDocument().isEmpty()) {
            try {
                bluebookDocuments = objectMapper.readValue(
                        v.getBluebookDocument(),
                        new TypeReference<List<String>>() {}
                );
            } catch (Exception e) {
                log.warn("Failed to parse bluebook documents for vehicle: {}", v.getId());
            }
        }

        return BookingResponseDto.builder()
                .id(b.getId())
                .bookingReference(b.getBookingReference())
                .vehicleId(v.getId())
                .vehicleName(v.getBrand() + " " + v.getModel())
                .vehicleBrand(v.getBrand())
                .vehicleModel(v.getModel())
                .vehicleImage(getFirstPhotoUrl(v))
                .vehicleBluebookDocuments(bluebookDocuments)
                .ownerId(b.getOwner().getId())
                .ownerName(b.getOwner().getFullName())
                .ownerEmail(b.getOwner().getEmail())
                .ownerPhone(b.getOwner().getPhoneNumber())
                .renterId(b.getRenter().getId())
                .renterName(b.getRenter().getFullName())
                .renterEmail(b.getRenter().getEmail())
                .renterPhone(b.getRenter().getPhoneNumber())
                .renterLocation(b.getRenterLocation())
                .renterLatitude(b.getRenterLatitude())
                .renterLongitude(b.getRenterLongitude())
                .pickupDate(b.getPickupDate())
                .dropoffDate(b.getDropoffDate())
                .totalDays(b.getTotalDays())
                .dailyRate(b.getDailyRate())
                .totalAmount(b.getTotalAmount())
                .securityDeposit(b.getSecurityDeposit())
                .insuranceType(b.getInsuranceType())
                .insuranceCost(b.getInsuranceCost())
                .bookingStatus(b.getBookingStatus().name())
                .paymentStatus(b.getPaymentStatus().name())
                .paymentMethod(b.getPaymentMethod())
                .rejectionReason(b.getRejectionReason())
                .ownerNotes(b.getOwnerNotes())
                .tripStartedAt(b.getTripStartedAt())
                .tripEndedAt(b.getTripEndedAt())
                .vehicleReturnedAt(b.getVehicleReturnedAt())
                .vehicleDamaged(b.getVehicleDamaged())
                .damageNotes(b.getDamageNotes())
                .securityDepositReturned(b.getSecurityDepositReturned())
                .securityDepositReturnedAt(b.getSecurityDepositReturnedAt())
                .securityDepositReturnedAmount(b.getSecurityDepositReturnedAmount())
                .createdAt(b.getCreatedAt())
                .approvedAt(b.getApprovedAt())
                .updatedAt(b.getUpdatedAt())
                .build();
    }
}