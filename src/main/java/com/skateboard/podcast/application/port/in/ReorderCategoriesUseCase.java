package com.skateboard.podcast.application.port.in;

import com.skateboard.podcast.domain.model.Category;

import java.util.List;
import java.util.UUID;

public interface ReorderCategoriesUseCase {

    /** The complete ordered id list — a permutation of every category, disabled ones included. */
    record Input(List<UUID> categoryIds) {}

    record Result(List<Category> categories) {}

    Result execute(Input input);
}
