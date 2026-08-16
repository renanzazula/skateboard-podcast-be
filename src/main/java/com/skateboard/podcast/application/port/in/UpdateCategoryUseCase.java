package com.skateboard.podcast.application.port.in;

import com.skateboard.podcast.domain.model.Category;

import java.util.UUID;

public interface UpdateCategoryUseCase {

    /** {@code customName} null/blank resets the display name to the YouTube title. */
    record Input(UUID id, String customName) {}

    Category execute(Input input);
}
