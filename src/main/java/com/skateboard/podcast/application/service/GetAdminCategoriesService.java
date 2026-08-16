package com.skateboard.podcast.application.service;

import com.skateboard.podcast.application.port.in.GetAdminCategoriesUseCase;
import com.skateboard.podcast.application.port.out.CategoryRepositoryPort;
import com.skateboard.podcast.application.port.out.PostCategoryPort;
import com.skateboard.podcast.domain.model.Category;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.Map;
import java.util.UUID;

@Service
public class GetAdminCategoriesService implements GetAdminCategoriesUseCase {

    // Same ordering findAllEnabledOrdered uses in SQL, applied in memory
    // because the admin list also includes disabled categories.
    private static final Comparator<Category> DISPLAY_ORDER = Comparator
            .comparing(Category::getDisplayOrder, Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(Category::isDefault, Comparator.reverseOrder())
            .thenComparing(Category::getCreatedAt);

    private final CategoryRepositoryPort categoryRepositoryPort;
    private final PostCategoryPort postCategoryPort;

    public GetAdminCategoriesService(CategoryRepositoryPort categoryRepositoryPort,
                                     PostCategoryPort postCategoryPort) {
        this.categoryRepositoryPort = categoryRepositoryPort;
        this.postCategoryPort = postCategoryPort;
    }

    @Override
    public Result execute() {
        Map<UUID, Long> counts = postCategoryPort.countPublishedByCategory();
        return new Result(categoryRepositoryPort.findAll().stream()
                .sorted(DISPLAY_ORDER)
                .map(category -> new CategoryWithCount(category, counts.getOrDefault(category.getId(), 0L)))
                .toList());
    }
}
