package com.skateboard.podcast.application.port.in;

import com.skateboard.podcast.domain.model.GuestApplication;

import java.util.Optional;
import java.util.UUID;

public interface GetGuestApplicationByIdUseCase {
    Optional<GuestApplication> execute(UUID id);
}
