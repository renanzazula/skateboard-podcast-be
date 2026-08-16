package com.skateboard.podcast.application.port.out;

import com.skateboard.podcast.domain.model.Category;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CategoryRepositoryPort {
    Optional<Category> findByExternalId(String source, String externalId);
    Optional<Category> findBySlug(String slug);
    Optional<Category> findById(UUID id);
    Category save(Category category);
    /** Enabled categories, ordered displayOrder ASC NULLS LAST, isDefault DESC, createdAt ASC. */
    List<Category> findAllEnabled();
    /** Every category regardless of enabled state — used by the sync (to disable ones whose playlist disappeared) and the admin endpoints. */
    List<Category> findAll();
}
