package com.skateboard.podcast.application.port.in;

import com.skateboard.podcast.domain.model.GuestApplication;
import com.skateboard.podcast.domain.model.GuestApplicationStatus;

import java.util.UUID;

public interface UpdateGuestApplicationStatusUseCase {

    record Input(UUID id, GuestApplicationStatus status, UUID reviewedBy) {
    }

    /** @throws com.skateboard.podcast.domain.exception.GuestApplicationNotFoundException if id doesn't exist */
    GuestApplication execute(Input input);
}
