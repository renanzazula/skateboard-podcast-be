package com.skateboard.podcast.application.service;

import com.skateboard.podcast.application.port.in.GetCategoriesUseCase;
import com.skateboard.podcast.application.port.out.CategoryRepositoryPort;
import com.skateboard.podcast.application.port.out.PostCategoryPort;
import com.skateboard.podcast.domain.model.Category;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;

@Service
public class GetCategoriesService implements GetCategoriesUseCase {

    private final CategoryRepositoryPort categoryRepositoryPort;
    private final PostCategoryPort postCategoryPort;

    public GetCategoriesService(CategoryRepositoryPort categoryRepositoryPort, PostCategoryPort postCategoryPort) {
        this.categoryRepositoryPort = categoryRepositoryPort;
        this.postCategoryPort = postCategoryPort;
    }

    @Override
    public Result execute() {
        Map<UUID, Long> counts = postCategoryPort.countPublishedByCategory();
        return new Result(categoryRepositoryPort.findAllEnabled().stream()
                .map(category -> new CategoryWithCount(category, counts.getOrDefault(category.getId(), 0L)))
                .toList());
    }
}
