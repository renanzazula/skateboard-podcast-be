package com.skateboard.podcast.application.port.in;

import com.skateboard.podcast.domain.model.GuestApplication;

import java.util.List;
import java.util.UUID;

public interface SubmitGuestApplicationUseCase {

    record Input(UUID userId, String name, String email, String message, List<String> socialLinks) {
    }

    /**
     * @throws com.skateboard.podcast.domain.exception.DuplicateActiveApplicationException
     *         if the user already has an active application
     * @throws IllegalArgumentException if a social link is not an http(s) URL
     */
    GuestApplication execute(Input input);
}
