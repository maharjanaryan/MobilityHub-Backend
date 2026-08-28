// src/main/java/com/mobilityhub/dto/GalleryImageRequest.java
package com.mobilityhub.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class GalleryImageRequest {
    private String title;
    private String category;
    private String description;
    private String imageUrl;   // From upload response
    private String fileName;   // From upload response
}