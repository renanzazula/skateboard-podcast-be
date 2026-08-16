package com.skateboard.podcast.application.port.in;

import com.skateboard.podcast.domain.model.Category;

import java.util.UUID;

public interface SetDefaultCategoryUseCase {

    Category execute(UUID id);
}
