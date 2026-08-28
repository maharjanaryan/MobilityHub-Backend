// src/main/java/com/mobilityhub/service/GalleryService.java
package com.mobilityhub.service;

import com.mobilityhub.model.GalleryImage;
import com.mobilityhub.model.User;
import com.mobilityhub.repository.GalleryImageRepository;
import com.mobilityhub.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class GalleryService {

    private final GalleryImageRepository galleryImageRepository;
    private final UserRepository userRepository;

    @Value("${upload.path:./uploads/gallery}")
    private String uploadPath;

    @Value("${server.base-url:http://localhost:8080}")
    private String baseUrl;

    // Upload image file
    @Transactional
    public Map<String, String> uploadImage(MultipartFile file) throws IOException {
        if (file.isEmpty()) {
            throw new RuntimeException("Please upload an image file");
        }

        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            throw new RuntimeException("Only image files are allowed");
        }

        if (file.getSize() > 10 * 1024 * 1024) {
            throw new RuntimeException("File size exceeds 10MB limit");
        }

        Path uploadDir = Paths.get(uploadPath);
        if (!Files.exists(uploadDir)) {
            Files.createDirectories(uploadDir);
        }

        String originalFilename = file.getOriginalFilename();
        String extension = "";
        if (originalFilename != null && originalFilename.contains(".")) {
            extension = originalFilename.substring(originalFilename.lastIndexOf("."));
        }
        String filename = UUID.randomUUID().toString() + "-" + System.currentTimeMillis() + extension;

        Path filePath = uploadDir.resolve(filename);
        Files.copy(file.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);

        String imageUrl = baseUrl + "/uploads/gallery/" + filename;

        Map<String, String> response = new HashMap<>();
        response.put("url", imageUrl);
        response.put("filename", filename);
        response.put("message", "Image uploaded successfully");

        return response;
    }

    // Add image to gallery
    @Transactional
    public GalleryImage addGalleryImage(String title, String category, String description,
                                        String imageUrl, Long userId, String fileName) {
        User uploader = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        GalleryImage image = GalleryImage.builder()
                .title(title)
                .category(category)
                .description(description)
                .imageUrl(imageUrl)
                .uploadedBy(uploader)
                .fileName(fileName)
                .build();

        return galleryImageRepository.save(image);
    }

    // Get all gallery images (with filters)
    public Page<GalleryImage> getGalleryImages(String category, String search, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        return galleryImageRepository.findAllWithFilters(category, search, pageable);
    }

    // Get public gallery images
    public Page<GalleryImage> getPublicGalleryImages(String category, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        if (category != null && !category.isEmpty() && !category.equals("All")) {
            return galleryImageRepository.findByCategory(category, pageable);
        }
        return galleryImageRepository.findAllWithFilters(null, null, pageable);
    }

    // Get single image
    public GalleryImage getGalleryImage(Long id) {
        return galleryImageRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Image not found"));
    }

    // Update image
    @Transactional
    public GalleryImage updateGalleryImage(Long id, String title, String category, String description) {
        GalleryImage image = galleryImageRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Image not found"));

        image.setTitle(title);
        image.setCategory(category);
        image.setDescription(description);

        return galleryImageRepository.save(image);
    }

    // Delete image
    @Transactional
    public void deleteGalleryImage(Long id) {
        GalleryImage image = galleryImageRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Image not found"));

        if (image.getFileName() != null) {
            try {
                Path filePath = Paths.get(uploadPath, image.getFileName());
                Files.deleteIfExists(filePath);
                log.info("Image file deleted: {}", image.getFileName());
            } catch (IOException e) {
                log.warn("Could not delete image file: {}", image.getFileName());
            }
        }

        galleryImageRepository.delete(image);
    }
}