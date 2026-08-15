package com.skateboard.podcast.application.port.in;

import com.skateboard.podcast.domain.model.Category;

import java.util.List;

public interface GetCategoriesUseCase {

    record CategoryWithCount(Category category, long postCount) {}

    record Result(List<CategoryWithCount> categories) {}

    Result execute();
}
