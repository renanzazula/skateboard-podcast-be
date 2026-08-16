package com.skateboard.podcast.application.service;

import com.skateboard.podcast.application.port.in.UpdateCategoryUseCase;
import com.skateboard.podcast.application.port.out.CategoryRepositoryPort;
import com.skateboard.podcast.domain.exception.CategoryNotFoundException;
import com.skateboard.podcast.domain.model.Category;
import org.springframework.stereotype.Service;

@Service
public class UpdateCategoryService implements UpdateCategoryUseCase {

    private final CategoryRepositoryPort categoryRepositoryPort;

    public UpdateCategoryService(CategoryRepositoryPort categoryRepositoryPort) {
        this.categoryRepositoryPort = categoryRepositoryPort;
    }

    @Override
    public Category execute(Input input) {
        Category category = categoryRepositoryPort.findById(input.id())
                .orElseThrow(() -> new CategoryNotFoundException(input.id().toString()));
        category.rename(input.customName());
        return categoryRepositoryPort.save(category);
    }
}
