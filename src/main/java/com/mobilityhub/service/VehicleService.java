package com.mobilityhub.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mobilityhub.dto.request.VehicleRequestDto;
import com.mobilityhub.dto.request.VehicleSearchRequestDto;
import com.mobilityhub.dto.response.VehicleResponseDto;
import com.mobilityhub.model.*;
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

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class VehicleService {

    private final VehicleRepository vehicleRepository;
    private final UserRepository userRepository;
    private final OwnerKycRepository ownerKycRepository;
    private final ObjectMapper objectMapper;
    private final NotificationService notificationService;

    /**
     * Check if user can add vehicle (has verified Owner KYC)
     */
    public boolean canAddVehicle(Long userId) {
        return ownerKycRepository.existsByUserIdAndKycStatus(userId, OwnerKyc.KycStatus.VERIFIED);
    }

    /**
     * Add new vehicle listing
     */
    @Transactional
    public VehicleResponseDto addVehicle(Long ownerId, VehicleRequestDto request) {
        User owner = userRepository.findById(ownerId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        // Check if license plate already exists
        if (vehicleRepository.existsByLicensePlate(request.getLicensePlate())) {
            throw new RuntimeException("Vehicle with this license plate already exists");
        }

        Vehicle vehicle = Vehicle.builder()
                .owner(owner)
                .brand(request.getBrand())
                .model(request.getModel())
                .year(request.getYear())
                .color(request.getColor())
                .licensePlate(request.getLicensePlate())
                .vin(request.getVin())
                .fuelType(request.getFuelType())
                .transmission(request.getTransmission())
                .seats(request.getSeats())
                .doors(request.getDoors())
                .luggageCapacity(request.getLuggageCapacity())
                .features(convertListToString(request.getFeatures()))
                .pricePerDay(request.getPricePerDay())
                .pricePerWeek(request.getPricePerWeek())
                .pricePerMonth(request.getPricePerMonth())
                .securityDeposit(request.getSecurityDeposit())
                .address(request.getAddress())
                .city(request.getCity())
                .state(request.getState())
                .zipCode(request.getZipCode())
                .latitude(request.getLatitude())
                .longitude(request.getLongitude())
                .availableFrom(request.getAvailableFrom())
                .availableTo(request.getAvailableTo())
                .minRentalDays(request.getMinRentalDays() != null ? request.getMinRentalDays() : 1)
                .maxRentalDays(request.getMaxRentalDays() != null ? request.getMaxRentalDays() : 30)
                .description(request.getDescription())
                .terms(request.getTerms())
                .photos(convertListToString(request.getPhotos()))
                .bluebookDocument(convertListToString(request.getBluebookDocuments()))
                .isAvailable(true)
                .isVerified(false)
                .rejectionReason(null)
                .totalRentals(0)
                .averageRating(0.0)
                .totalRatings(0)
                .viewCount(0)
                .build();

        Vehicle savedVehicle = vehicleRepository.save(vehicle);
        log.info("New vehicle added by user: {} - {} {}", owner.getUsername(), vehicle.getBrand(), vehicle.getModel());

        // Create notification for vehicle owner
        notificationService.createNotification(
                owner,
                "Vehicle Submitted for Verification",
                String.format("Your vehicle '%s %s (%s)' has been submitted for verification. We'll notify you once it's reviewed.",
                        vehicle.getBrand(), vehicle.getModel(), vehicle.getLicensePlate()),
                Notification.NotificationType.VEHICLE_SUBMITTED,
                savedVehicle.getId()
        );

        // Notify all admins about new vehicle submission
        List<User> admins = userRepository.findByRole(Role.ADMIN);
        if (!admins.isEmpty()) {
            notificationService.createNotificationForUsers(
                    admins,
                    "New Vehicle Pending Verification",
                    String.format("A new vehicle '%s %s' has been submitted for verification by %s",
                            vehicle.getBrand(), vehicle.getModel(), owner.getFullName()),
                    Notification.NotificationType.VEHICLE_SUBMITTED,
                    savedVehicle.getId()
            );
        }

        return convertToResponseDto(savedVehicle);
    }

    /**
     * Get vehicle by ID
     */
    @Transactional
    public VehicleResponseDto getVehicleById(Long vehicleId) {
        Vehicle vehicle = vehicleRepository.findById(vehicleId)
                .orElseThrow(() -> new RuntimeException("Vehicle not found"));

        // Increment view count
        vehicle.setViewCount(vehicle.getViewCount() + 1);
        vehicleRepository.save(vehicle);

        return convertToResponseDto(vehicle);
    }

    /**
     * Get all vehicles by owner
     */
    public List<VehicleResponseDto> getVehiclesByOwner(Long ownerId) {
        return vehicleRepository.findByOwnerId(ownerId)
                .stream()
                .map(this::convertToResponseDto)
                .collect(Collectors.toList());
    }

    /**
     * Get paginated vehicles by owner
     */
    public Page<VehicleResponseDto> getVehiclesByOwner(Long ownerId, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        return vehicleRepository.findByOwnerId(ownerId, pageable)
                .map(this::convertToResponseDto);
    }

    /**
     * Update vehicle
     */
    @Transactional
    public VehicleResponseDto updateVehicle(Long vehicleId, Long ownerId, VehicleRequestDto request) {
        Vehicle vehicle = vehicleRepository.findById(vehicleId)
                .orElseThrow(() -> new RuntimeException("Vehicle not found"));

        if (!vehicle.getOwner().getId().equals(ownerId)) {
            throw new RuntimeException("You don't have permission to update this vehicle");
        }

        vehicle.setBrand(request.getBrand());
        vehicle.setModel(request.getModel());
        vehicle.setYear(request.getYear());
        vehicle.setColor(request.getColor());
        vehicle.setLicensePlate(request.getLicensePlate());
        vehicle.setVin(request.getVin());
        vehicle.setFuelType(request.getFuelType());
        vehicle.setTransmission(request.getTransmission());
        vehicle.setSeats(request.getSeats());
        vehicle.setDoors(request.getDoors());
        vehicle.setLuggageCapacity(request.getLuggageCapacity());
        vehicle.setFeatures(convertListToString(request.getFeatures()));
        vehicle.setPricePerDay(request.getPricePerDay());
        vehicle.setPricePerWeek(request.getPricePerWeek());
        vehicle.setPricePerMonth(request.getPricePerMonth());
        vehicle.setSecurityDeposit(request.getSecurityDeposit());
        vehicle.setAddress(request.getAddress());
        vehicle.setCity(request.getCity());
        vehicle.setState(request.getState());
        vehicle.setZipCode(request.getZipCode());
        vehicle.setLatitude(request.getLatitude());
        vehicle.setLongitude(request.getLongitude());
        vehicle.setAvailableFrom(request.getAvailableFrom());
        vehicle.setAvailableTo(request.getAvailableTo());
        vehicle.setMinRentalDays(request.getMinRentalDays());
        vehicle.setMaxRentalDays(request.getMaxRentalDays());
        vehicle.setDescription(request.getDescription());
        vehicle.setTerms(request.getTerms());
        vehicle.setPhotos(convertListToString(request.getPhotos()));
        vehicle.setBluebookDocument(convertListToString(request.getBluebookDocuments()));

        // Reset rejection reason and verification status if vehicle is being updated
        if (vehicle.getRejectionReason() != null) {
            vehicle.setRejectionReason(null);
            vehicle.setIsVerified(false);
        }

        Vehicle updatedVehicle = vehicleRepository.save(vehicle);
        log.info("Vehicle updated: {} {}", vehicle.getBrand(), vehicle.getModel());

        // Notify owner about update
        notificationService.createNotification(
                vehicle.getOwner(),
                "Vehicle Updated",
                String.format("Your vehicle '%s %s (%s)' has been updated.",
                        vehicle.getBrand(), vehicle.getModel(), vehicle.getLicensePlate()),
                Notification.NotificationType.VEHICLE_UPDATED,
                vehicleId
        );

        return convertToResponseDto(updatedVehicle);
    }

    /**
     * Delete vehicle
     */
    @Transactional
    public void deleteVehicle(Long vehicleId, Long ownerId) {
        Vehicle vehicle = vehicleRepository.findById(vehicleId)
                .orElseThrow(() -> new RuntimeException("Vehicle not found"));

        if (!vehicle.getOwner().getId().equals(ownerId)) {
            throw new RuntimeException("You don't have permission to delete this vehicle");
        }

        String vehicleInfo = vehicle.getBrand() + " " + vehicle.getModel();
        vehicleRepository.delete(vehicle);
        log.info("Vehicle deleted: {}", vehicleInfo);
    }

    /**
     * Toggle vehicle availability
     */
    @Transactional
    public VehicleResponseDto toggleAvailability(Long vehicleId, Long ownerId) {
        Vehicle vehicle = vehicleRepository.findById(vehicleId)
                .orElseThrow(() -> new RuntimeException("Vehicle not found"));

        if (!vehicle.getOwner().getId().equals(ownerId)) {
            throw new RuntimeException("You don't have permission to modify this vehicle");
        }

        vehicle.setIsAvailable(!vehicle.getIsAvailable());
        Vehicle updatedVehicle = vehicleRepository.save(vehicle);

        log.info("Vehicle availability toggled to {} for: {} {}",
                vehicle.getIsAvailable(), vehicle.getBrand(), vehicle.getModel());

        String status = vehicle.getIsAvailable() ? "available" : "unavailable";
        notificationService.createNotification(
                vehicle.getOwner(),
                "Vehicle Availability Updated",
                String.format("Your vehicle '%s %s (%s)' is now %s for booking.",
                        vehicle.getBrand(), vehicle.getModel(), vehicle.getLicensePlate(), status),
                Notification.NotificationType.VEHICLE_UPDATED,
                vehicleId
        );

        return convertToResponseDto(updatedVehicle);
    }

    /**
     * Search vehicles
     */
    public Page<VehicleResponseDto> searchVehicles(VehicleSearchRequestDto request) {
        Sort sort = getSortOrder(request.getSortBy());
        Pageable pageable = PageRequest.of(request.getPage(), request.getSize(), sort);

        Page<Vehicle> vehicles = vehicleRepository.searchVehicles(
                request.getBrand(),
                request.getModel(),
                request.getCity(),
                request.getFuelType(),
                request.getTransmission(),
                request.getMinSeats(),
                request.getMaxSeats(),
                request.getMinPrice(),
                request.getMaxPrice(),
                pageable
        );

        return vehicles.map(this::convertToResponseDto);
    }

    /**
     * Get featured vehicles
     */
    public Page<VehicleResponseDto> getFeaturedVehicles(int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        return vehicleRepository.findFeaturedVehicles(pageable)
                .map(this::convertToResponseDto);
    }

    /**
     * Get recent vehicles
     */
    public Page<VehicleResponseDto> getRecentVehicles(int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        return vehicleRepository.findRecentVehicles(pageable)
                .map(this::convertToResponseDto);
    }

    /**
     * Admin - Verify vehicle
     */
    @Transactional
    public VehicleResponseDto verifyVehicle(Long vehicleId, Long adminId, boolean approved, String rejectionReason) {
        Vehicle vehicle = vehicleRepository.findById(vehicleId)
                .orElseThrow(() -> new RuntimeException("Vehicle not found"));

        if (approved) {
            vehicle.setIsVerified(true);
            vehicle.setVerifiedBy(adminId);
            vehicle.setVerifiedAt(LocalDateTime.now());
            vehicle.setRejectionReason(null);
            vehicle.setIsAvailable(true);
            log.info("Vehicle verified by admin: {} {}", vehicle.getBrand(), vehicle.getModel());

            // Notify owner about approval
            notificationService.createNotification(
                    vehicle.getOwner(),
                    "Vehicle Approved! 🎉",
                    String.format("Your vehicle '%s %s (%s)' has been approved and is now available for booking!",
                            vehicle.getBrand(), vehicle.getModel(), vehicle.getLicensePlate()),
                    Notification.NotificationType.VEHICLE_APPROVED,
                    vehicleId
            );

        } else {
            vehicle.setIsVerified(false);
            vehicle.setRejectionReason(rejectionReason);
            vehicle.setVerifiedBy(null);
            vehicle.setVerifiedAt(null);
            vehicle.setIsAvailable(false);
            log.info("Vehicle verification rejected: {} {} - Reason: {}",
                    vehicle.getBrand(), vehicle.getModel(), rejectionReason);

            // Notify owner about rejection
            notificationService.createNotification(
                    vehicle.getOwner(),
                    "Vehicle Verification Rejected",
                    String.format("Your vehicle '%s %s (%s)' was not approved. Reason: %s. Please update and resubmit.",
                            vehicle.getBrand(), vehicle.getModel(), vehicle.getLicensePlate(),
                            rejectionReason != null ? rejectionReason : "No reason provided"),
                    Notification.NotificationType.VEHICLE_REJECTED,
                    vehicleId
            );
        }

        Vehicle savedVehicle = vehicleRepository.save(vehicle);
        return convertToResponseDto(savedVehicle);
    }

    /**
     * Admin - Get pending vehicles for verification
     */
    public List<VehicleResponseDto> getPendingVehicles() {
        return vehicleRepository.findByIsVerifiedFalseAndRejectionReasonIsNull()
                .stream()
                .map(this::convertToResponseDto)
                .collect(Collectors.toList());
    }

    /**
     * Admin - Get all vehicles (for admin management)
     */
    public Page<VehicleResponseDto> getAllVehicles(int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        return vehicleRepository.findAll(pageable)
                .map(this::convertToResponseDto);
    }

    // ==================== HELPER METHODS ====================

    /**
     * Generic helper: converts any List<String> (photos, features, bluebook docs) to JSON string
     */
    private String convertListToString(List<String> list) {
        if (list == null || list.isEmpty()) {
            return "[]";
        }
        try {
            return objectMapper.writeValueAsString(list);
        } catch (Exception e) {
            log.error("Failed to convert list to JSON", e);
            return "[]";
        }
    }

    /**
     * Generic helper: converts JSON string back to List<String>
     */
    private List<String> convertStringToList(String json) {
        if (json == null || json.isEmpty()) {
            return Collections.emptyList();
        }
        try {
            return objectMapper.readValue(json, List.class);
        } catch (Exception e) {
            log.error("Failed to convert JSON to list", e);
            return Collections.emptyList();
        }
    }

    private Sort getSortOrder(String sortBy) {
        if (sortBy == null) {
            return Sort.by(Sort.Direction.DESC, "createdAt");
        }
        return switch (sortBy) {
            case "price_asc" -> Sort.by(Sort.Direction.ASC, "pricePerDay");
            case "price_desc" -> Sort.by(Sort.Direction.DESC, "pricePerDay");
            case "rating_desc" -> Sort.by(Sort.Direction.DESC, "averageRating");
            default -> Sort.by(Sort.Direction.DESC, "createdAt");
        };
    }

    /**
     * Get owner name with proper fallbacks
     */
    private String getOwnerName(User owner) {
        if (owner == null) {
            return "Vehicle Owner";
        }

        // Try full name first
        if (owner.getFullName() != null && !owner.getFullName().trim().isEmpty()) {
            return owner.getFullName().trim();
        }

        // Try username as fallback
        if (owner.getUsername() != null && !owner.getUsername().trim().isEmpty()) {
            return owner.getUsername().trim();
        }

        // Ultimate fallback
        return "Vehicle Owner";
    }

    /**
     * Convert Vehicle entity to VehicleResponseDto with proper owner information
     */
    private VehicleResponseDto convertToResponseDto(Vehicle vehicle) {
        if (vehicle == null) {
            return null;
        }

        User owner = vehicle.getOwner();

        // Count owner's vehicles
        long ownerVehicleCount = 0;
        if (owner != null) {
            ownerVehicleCount = vehicleRepository.countByOwnerId(owner.getId());
        }
        Integer ownerTotalVehicles = (int) ownerVehicleCount;

        // Get owner name with fallbacks
        String ownerName = getOwnerName(owner);
        String ownerAvatar = owner != null ? owner.getAvatarUrl() : null;
        Long ownerId = owner != null ? owner.getId() : null;
        String ownerEmail = owner != null ? owner.getEmail() : null;

        return VehicleResponseDto.builder()
                .id(vehicle.getId())
                .ownerId(ownerId)
                .ownerName(ownerName)
                .ownerEmail(ownerEmail)
                .ownerAvatar(ownerAvatar)
                .ownerTotalVehicles(ownerTotalVehicles)
                .ownerRating(0.0)
                .brand(vehicle.getBrand())
                .model(vehicle.getModel())
                .year(vehicle.getYear())
                .color(vehicle.getColor())
                .licensePlate(vehicle.getLicensePlate())
                .fuelType(vehicle.getFuelType())
                .transmission(vehicle.getTransmission())
                .seats(vehicle.getSeats())
                .doors(vehicle.getDoors())
                .luggageCapacity(vehicle.getLuggageCapacity())
                .features(convertStringToList(vehicle.getFeatures()))
                .pricePerDay(vehicle.getPricePerDay())
                .pricePerWeek(vehicle.getPricePerWeek())
                .pricePerMonth(vehicle.getPricePerMonth())
                .securityDeposit(vehicle.getSecurityDeposit())
                .address(vehicle.getAddress())
                .city(vehicle.getCity())
                .state(vehicle.getState())
                .zipCode(vehicle.getZipCode())
                .latitude(vehicle.getLatitude())
                .longitude(vehicle.getLongitude())
                .isAvailable(vehicle.getIsAvailable())
                .isVerified(vehicle.getIsVerified())
                .rejectionReason(vehicle.getRejectionReason())
                .availableFrom(vehicle.getAvailableFrom())
                .availableTo(vehicle.getAvailableTo())
                .minRentalDays(vehicle.getMinRentalDays())
                .maxRentalDays(vehicle.getMaxRentalDays())
                .description(vehicle.getDescription())
                .terms(vehicle.getTerms())
                .photos(convertStringToList(vehicle.getPhotos()))
                .bluebookDocuments(convertStringToList(vehicle.getBluebookDocument()))
                .totalRentals(vehicle.getTotalRentals())
                .averageRating(vehicle.getAverageRating())
                .totalRatings(vehicle.getTotalRatings())
                .viewCount(vehicle.getViewCount())
                .createdAt(vehicle.getCreatedAt())
                .updatedAt(vehicle.getUpdatedAt())
                .build();
    }
}