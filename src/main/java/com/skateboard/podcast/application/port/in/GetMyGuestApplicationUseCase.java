package com.skateboard.podcast.application.port.in;

import com.skateboard.podcast.domain.model.GuestApplication;

import java.util.Optional;
import java.util.UUID;

public interface GetMyGuestApplicationUseCase {

    /** The user's active application, or else their most recent one; empty if they never applied. */
    Optional<GuestApplication> execute(UUID userId);
}
