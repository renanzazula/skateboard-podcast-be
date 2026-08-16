package com.skateboard.podcast.application.port.in;

import com.skateboard.podcast.domain.model.Category;

import java.util.List;

public interface GetAdminCategoriesUseCase {

    record CategoryWithCount(Category category, long postCount) {}

    record Result(List<CategoryWithCount> categories) {}

    /** Every category, disabled ones included, in display order. */
    Result execute();
}
