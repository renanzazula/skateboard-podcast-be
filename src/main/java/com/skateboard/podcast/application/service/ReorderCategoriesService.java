package com.skateboard.podcast.application.service;

import com.skateboard.podcast.application.port.in.ReorderCategoriesUseCase;
import com.skateboard.podcast.application.port.out.CategoryRepositoryPort;
import com.skateboard.podcast.domain.model.Category;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class ReorderCategoriesService implements ReorderCategoriesUseCase {

    private final CategoryRepositoryPort categoryRepositoryPort;

    public ReorderCategoriesService(CategoryRepositoryPort categoryRepositoryPort) {
        this.categoryRepositoryPort = categoryRepositoryPort;
    }

    /**
     * Writes {@code display_order = 0..n-1} from the submitted permutation.
     * The list must cover every category exactly once (disabled ones
     * included) — a stale or partial list is rejected so a concurrent sync
     * that created a category can't silently lose its slot.
     */
    @Override
    @Transactional
    public Result execute(Input input) {
        List<UUID> ids = input.categoryIds() != null ? input.categoryIds() : List.of();
        Map<UUID, Category> byId = categoryRepositoryPort.findAll().stream()
                .collect(Collectors.toMap(Category::getId, Function.identity()));

        Set<UUID> seen = new HashSet<>();
        for (UUID id : ids) {
            if (!seen.add(id)) {
                throw new IllegalArgumentException("Duplicate category id: " + id);
            }
            if (!byId.containsKey(id)) {
                throw new IllegalArgumentException("Unknown category id: " + id);
            }
        }
        if (seen.size() != byId.size()) {
            throw new IllegalArgumentException(
                    "Order must include every category (got " + seen.size() + " of " + byId.size()
                            + ") — refresh and retry");
        }

        int position = 0;
        for (UUID id : ids) {
            Category category = byId.get(id);
            category.setDisplayOrder(position++);
            categoryRepositoryPort.save(category);
        }
        return new Result(ids.stream().map(byId::get).toList());
    }
}
