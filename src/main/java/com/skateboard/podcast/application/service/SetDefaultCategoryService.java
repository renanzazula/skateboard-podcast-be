package com.skateboard.podcast.application.service;

import com.skateboard.podcast.application.port.in.SetDefaultCategoryUseCase;
import com.skateboard.podcast.application.port.out.CategoryRepositoryPort;
import com.skateboard.podcast.domain.exception.CategoryNotFoundException;
import com.skateboard.podcast.domain.model.Category;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class SetDefaultCategoryService implements SetDefaultCategoryUseCase {

    private final CategoryRepositoryPort categoryRepositoryPort;

    public SetDefaultCategoryService(CategoryRepositoryPort categoryRepositoryPort) {
        this.categoryRepositoryPort = categoryRepositoryPort;
    }

    /**
     * Makes {@code id} the single default. Every row is locked
     * ({@code default_locked}) — not just the chosen one — so the sync stops
     * applying its config-driven default to any category from now on, and
     * clears are written before the new default is set.
     */
    @Override
    @Transactional
    public Category execute(UUID id) {
        var categories = categoryRepositoryPort.findAll();
        Category target = categories.stream()
                .filter(category -> category.getId().equals(id))
                .findFirst()
                .orElseThrow(() -> new CategoryNotFoundException(id.toString()));

        // Clears are written before the new default is set so no state with
        // two defaults is ever persisted.
        for (Category category : categories) {
            if (category == target) continue;
            boolean changed = category.isDefault() || !category.isDefaultLocked();
            category.clearDefault();
            if (changed) {
                categoryRepositoryPort.save(category);
            }
        }
        target.markDefault();
        return categoryRepositoryPort.save(target);
    }
}
