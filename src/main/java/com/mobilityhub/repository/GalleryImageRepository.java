// src/main/java/com/mobilityhub/repository/GalleryImageRepository.java
package com.mobilityhub.repository;

import com.mobilityhub.model.GalleryImage;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface GalleryImageRepository extends JpaRepository<GalleryImage, Long> {

    @Query("SELECT g FROM GalleryImage g WHERE " +
            "(:category IS NULL OR :category = 'All' OR g.category = :category) AND " +
            "(:search IS NULL OR LOWER(g.title) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
            "LOWER(g.description) LIKE LOWER(CONCAT('%', :search, '%')))")
    Page<GalleryImage> findAllWithFilters(@Param("category") String category,
                                          @Param("search") String search,
                                          Pageable pageable);

    Page<GalleryImage> findByCategory(String category, Pageable pageable);
}