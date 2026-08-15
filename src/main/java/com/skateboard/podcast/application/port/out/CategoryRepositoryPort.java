package com.skateboard.podcast.application.port.out;

import com.skateboard.podcast.domain.model.Category;

import java.util.List;
import java.util.Optional;

public interface CategoryRepositoryPort {
    Optional<Category> findByExternalId(String source, String externalId);
    Optional<Category> findBySlug(String slug);
    Category save(Category category);
    /** Enabled categories, ordered isDefault DESC, createdAt ASC. */
    List<Category> findAllEnabled();
    /** Every category regardless of enabled state — used to disable ones whose playlist disappeared. */
    List<Category> findAll();
}
