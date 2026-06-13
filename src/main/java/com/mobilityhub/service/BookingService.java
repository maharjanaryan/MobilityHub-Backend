package com.mobilityhub.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mobilityhub.dto.request.BookingActionRequestDto;
import com.mobilityhub.dto.request.BookingRequestDto;
import com.mobilityhub.dto.response.BookingResponseDto;
import com.mobilityhub.model.*;
import com.mobilityhub.repository.BookingRepository;
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
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class BookingService {

    private final BookingRepository bookingRepository;
    private final UserRepository    userRepository;
    private final VehicleRepository vehicleRepository;
    private final ObjectMapper      objectMapper;

    private static final BigDecimal PREMIUM_INSURANCE_COST  = BigDecimal.valueOf(45);
    private static final BigDecimal STANDARD_INSURANCE_COST = BigDecimal.valueOf(22);

    @Transactional
    public BookingResponseDto createBooking(Long renterId, BookingRequestDto request) {

        User    renter  = userRepository.findById(renterId)
                .orElseThrow(() -> new RuntimeException("User not found"));
        Vehicle vehicle = vehicleRepository.findById(request.getVehicleId())
                .orElseThrow(() -> new RuntimeException("Vehicle not found"));

        if (!vehicle.getIsAvailable()) {
            throw new RuntimeException("Vehicle is not available for booking");
        }

        // ── Parse date from ISO string correctly ──────────────────────────────
        // Frontend sends: "2026-06-13T00:00:00.000Z"
        // We extract the date part and use Nepal timezone for comparison
        LocalDate pickupLocalDate;
        LocalDate dropoffLocalDate;

        try {
            // Extract date part from ISO string (first 10 characters)
            String pickupDateStr = request.getPickupDate().toString().substring(0, 10);
            String dropoffDateStr = request.getDropoffDate().toString().substring(0, 10);

            pickupLocalDate = LocalDate.parse(pickupDateStr);
            dropoffLocalDate = LocalDate.parse(dropoffDateStr);

            log.debug("Parsed dates - Pickup: {}, Dropoff: {}", pickupLocalDate, dropoffLocalDate);
        } catch (Exception e) {
            log.error("Failed to parse dates: pickup={}, dropoff={}",
                    request.getPickupDate(), request.getDropoffDate(), e);
            throw new RuntimeException("Invalid date format. Please select valid dates.");
        }

        LocalDateTime pickupDate  = pickupLocalDate.atStartOfDay();
        LocalDateTime dropoffDate = dropoffLocalDate.atStartOfDay();

        // ── Date validation using Nepal timezone ──────────────────────────────
        // Get current date in Nepal timezone (UTC+5:45)
        ZoneId nepalZone = ZoneId.of("Asia/Kathmandu");
        LocalDate todayInNepal = LocalDate.now(nepalZone);
        LocalDate tomorrowInNepal = todayInNepal.plusDays(1);

        log.debug("Today in Nepal: {}, Tomorrow in Nepal: {}", todayInNepal, tomorrowInNepal);
        log.debug("Pickup date: {}", pickupLocalDate);

        if (pickupLocalDate.isBefore(tomorrowInNepal)) {
            throw new RuntimeException("Pickup date must be at least tomorrow (Nepal time). Selected: " + pickupLocalDate);
        }

        if (!dropoffLocalDate.isAfter(pickupLocalDate)) {
            throw new RuntimeException("Dropoff date must be after pickup date");
        }

        // ── Availability check ────────────────────────────────────────────────
        if (bookingRepository.isVehicleBooked(vehicle.getId(), pickupDate, dropoffDate)) {
            throw new RuntimeException("Selected dates are not available. Please choose different dates.");
        }

        // ── Rental length validation ──────────────────────────────────────────
        long days = ChronoUnit.DAYS.between(pickupDate, dropoffDate);

        log.debug("Number of days: {}", days);

        if (vehicle.getMinRentalDays() != null && days < vehicle.getMinRentalDays()) {
            throw new RuntimeException("Minimum rental period is " + vehicle.getMinRentalDays() + " day(s)");
        }
        if (vehicle.getMaxRentalDays() != null && days > vehicle.getMaxRentalDays()) {
            throw new RuntimeException("Maximum rental period is " + vehicle.getMaxRentalDays() + " day(s)");
        }

        // ── Pricing calculation ────────────────────────────────────────────────
        BigDecimal dailyRate    = vehicle.getPricePerDay();
        BigDecimal rentalAmount = dailyRate.multiply(BigDecimal.valueOf(days));

        BigDecimal insuranceCost = BigDecimal.ZERO;
        if ("premium".equalsIgnoreCase(request.getInsuranceType())) {
            insuranceCost = PREMIUM_INSURANCE_COST.multiply(BigDecimal.valueOf(days));
        } else if ("standard".equalsIgnoreCase(request.getInsuranceType())) {
            insuranceCost = STANDARD_INSURANCE_COST.multiply(BigDecimal.valueOf(days));
        }

        BigDecimal totalAmount = rentalAmount.add(insuranceCost);

        // ── Check for duplicate pending booking ───────────────────────────────
        boolean duplicatePending = bookingRepository
                .findByVehicleIdAndBookingStatusIn(vehicle.getId(), List.of(Booking.BookingStatus.PENDING))
                .stream()
                .anyMatch(b -> b.getRenter().getId().equals(renterId)
                        && b.getPickupDate().toLocalDate().equals(pickupLocalDate)
                        && b.getDropoffDate().toLocalDate().equals(dropoffLocalDate));

        if (duplicatePending) {
            throw new RuntimeException("You already have a pending booking request for these dates");
        }

        // ── Create and save booking ───────────────────────────────────────────
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
                .insuranceType(request.getInsuranceType() == null
                        ? "standard" : request.getInsuranceType().toLowerCase())
                .insuranceCost(insuranceCost)
                .paymentStatus(Booking.PaymentStatus.PENDING)
                .bookingStatus(Booking.BookingStatus.PENDING)
                .paymentMethod(request.getPaymentMethod())
                .driverName(request.getDriverName())
                .driverLicenseNumber(request.getDriverLicenseNumber())
                .build();

        Booking saved = bookingRepository.save(booking);
        log.info("Booking created: {} | vehicle {} | renter {} | pickup: {}, dropoff: {}",
                bookingReference, vehicle.getId(), renter.getUsername(), pickupLocalDate, dropoffLocalDate);

        return mapToResponseDto(saved);
    }

    public BookingResponseDto getBookingById(Long bookingId, Long userId, String userRole) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new RuntimeException("Booking not found"));

        boolean isAdmin  = "ROLE_ADMIN".equals(userRole) || "ADMIN".equals(userRole);
        boolean isRenter = booking.getRenter().getId().equals(userId);
        boolean isOwner  = booking.getOwner().getId().equals(userId);

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
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<Booking> bookings = (status != null && !status.equalsIgnoreCase("all"))
                ? bookingRepository.findByOwnerIdAndBookingStatus(
                ownerId, Booking.BookingStatus.valueOf(status.toUpperCase()), pageable)
                : bookingRepository.findByOwnerId(ownerId, pageable);
        return bookings.map(this::mapToResponseDto);
    }

    public List<BookingResponseDto> getPendingBookingsForOwner(Long ownerId) {
        return bookingRepository
                .findByOwnerIdAndBookingStatusOrderByCreatedAtAsc(ownerId, Booking.BookingStatus.PENDING)
                .stream().map(this::mapToResponseDto).collect(Collectors.toList());
    }

    @Transactional
    public BookingResponseDto approveBooking(Long bookingId, Long ownerId, BookingActionRequestDto request) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new RuntimeException("Booking not found"));

        if (!booking.getOwner().getId().equals(ownerId)) {
            throw new RuntimeException("Only the vehicle owner can approve this booking");
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
        booking.setOwnerNotes(request.getOwnerNotes());

        log.info("Booking {} approved by owner {}", booking.getBookingReference(), ownerId);
        return mapToResponseDto(bookingRepository.save(booking));
    }

    @Transactional
    public BookingResponseDto rejectBooking(Long bookingId, Long ownerId, BookingActionRequestDto request) {
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
        booking.setOwnerNotes(request.getOwnerNotes());

        log.info("Booking {} rejected by owner {}. Reason: {}",
                booking.getBookingReference(), ownerId, request.getRejectionReason());
        return mapToResponseDto(bookingRepository.save(booking));
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

        // Use Nepal timezone for cancellation check
        ZoneId nepalZone = ZoneId.of("Asia/Kathmandu");
        LocalDate todayInNepal = LocalDate.now(nepalZone);
        LocalDate pickupDateInNepal = booking.getPickupDate().atZone(nepalZone).toLocalDate();

        if (pickupDateInNepal.isBefore(todayInNepal.plusDays(1))) {
            throw new RuntimeException("Bookings can only be cancelled at least 24 hours before pickup (Nepal time)");
        }

        booking.setBookingStatus(Booking.BookingStatus.CANCELLED);
        log.info("Booking {} cancelled by renter {}", booking.getBookingReference(), renterId);
        return mapToResponseDto(bookingRepository.save(booking));
    }

    public boolean checkAvailability(Long vehicleId, LocalDateTime pickupDate, LocalDateTime dropoffDate) {
        // Normalize to date-only
        LocalDateTime p = pickupDate.toLocalDate().atStartOfDay();
        LocalDateTime d = dropoffDate.toLocalDate().atStartOfDay();
        boolean booked = bookingRepository.isVehicleBooked(vehicleId, p, d);
        log.debug("Availability check vehicle={} {} → {} available={}", vehicleId, p, d, !booked);
        return !booked;
    }

    public List<LocalDateTime> getBookedDatesForVehicle(Long vehicleId) {
        List<Booking> bookings = bookingRepository.findByVehicleIdAndBookingStatusIn(
                vehicleId,
                List.of(Booking.BookingStatus.CONFIRMED, Booking.BookingStatus.ACTIVE)
        );

        // Use Nepal timezone for today's date
        ZoneId nepalZone = ZoneId.of("Asia/Kathmandu");
        LocalDate today = LocalDate.now(nepalZone);
        Set<LocalDateTime> bookedDates = new HashSet<>();

        for (Booking b : bookings) {
            LocalDate start = b.getPickupDate().toLocalDate();
            LocalDate end   = b.getDropoffDate().toLocalDate();
            LocalDate cur   = start.isBefore(today) ? today : start;

            while (cur.isBefore(end)) {
                bookedDates.add(cur.atStartOfDay());
                cur = cur.plusDays(1);
            }
        }
        return new ArrayList<>(bookedDates);
    }

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

    private BookingResponseDto mapToResponseDto(Booking b) {
        Vehicle v = b.getVehicle();
        return BookingResponseDto.builder()
                .id(b.getId())
                .bookingReference(b.getBookingReference())
                .vehicleId(v.getId())
                .vehicleName(v.getBrand() + " " + v.getModel())
                .vehicleImage(getFirstPhotoUrl(v))
                .ownerId(b.getOwner().getId())
                .ownerName(b.getOwner().getFullName())
                .renterId(b.getRenter().getId())
                .renterName(b.getRenter().getFullName())
                .renterEmail(b.getRenter().getEmail())
                .renterPhone(b.getRenter().getPhoneNumber())
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
                .rejectionReason(b.getRejectionReason())
                .createdAt(b.getCreatedAt())
                .approvedAt(b.getApprovedAt())
                .build();
    }
}