// src/main/java/com/mobilityhub/controller/GalleryController.java
package com.mobilityhub.controller;

import com.mobilityhub.dto.GalleryImageRequest;
import com.mobilityhub.model.GalleryImage;
import com.mobilityhub.security.services.UserDetailsImpl;
import com.mobilityhub.service.GalleryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@CrossOrigin(origins = "*", maxAge = 3600)
public class GalleryController {

    private final GalleryService galleryService;

    // Step 1: Upload image file (Admin only)
    @PostMapping(value = "/admin/gallery/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, String>> uploadImage(@RequestParam("image") MultipartFile file) throws IOException {
        return ResponseEntity.ok(galleryService.uploadImage(file));
    }

    // Step 2: Add image to gallery with URL from upload (Admin only)
    @PostMapping("/admin/gallery")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<GalleryImage> addGalleryImage(
            @RequestBody GalleryImageRequest request,
            Authentication authentication) {
        UserDetailsImpl userDetails = (UserDetailsImpl) authentication.getPrincipal();

        // The frontend sends imageUrl and fileName from the upload response
        GalleryImage image = galleryService.addGalleryImage(
                request.getTitle(),
                request.getCategory(),
                request.getDescription(),
                request.getImageUrl(),  // From upload response
                userDetails.getId(),
                request.getFileName()   // From upload response
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(image);
    }

    // Get all gallery images (Admin only)
    @GetMapping("/admin/gallery")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Page<GalleryImage>> getGalleryImages(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        return ResponseEntity.ok(galleryService.getGalleryImages(category, search, page, size));
    }

    // Get single image (Admin only)
    @GetMapping("/admin/gallery/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<GalleryImage> getGalleryImage(@PathVariable Long id) {
        return ResponseEntity.ok(galleryService.getGalleryImage(id));
    }

    // Update image (Admin only)
    @PutMapping("/admin/gallery/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<GalleryImage> updateGalleryImage(
            @PathVariable Long id,
            @RequestBody GalleryImageRequest request) {
        return ResponseEntity.ok(galleryService.updateGalleryImage(
                id,
                request.getTitle(),
                request.getCategory(),
                request.getDescription()
        ));
    }

    // Delete image (Admin only)
    @DeleteMapping("/admin/gallery/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, String>> deleteGalleryImage(@PathVariable Long id) {
        galleryService.deleteGalleryImage(id);
        Map<String, String> response = new HashMap<>();
        response.put("message", "Image deleted successfully");
        return ResponseEntity.ok(response);
    }

    // Get public gallery images (No auth required)
    @GetMapping("/gallery/public")
    public ResponseEntity<Page<GalleryImage>> getPublicGalleryImages(
            @RequestParam(required = false) String category,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(galleryService.getPublicGalleryImages(category, page, size));
    }
}