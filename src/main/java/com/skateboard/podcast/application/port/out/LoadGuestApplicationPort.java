package com.skateboard.podcast.application.port.out;

import com.skateboard.podcast.domain.model.GuestApplication;
import com.skateboard.podcast.domain.model.GuestApplicationStatus;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LoadGuestApplicationPort {
    Optional<GuestApplication> findById(UUID id);

    /** The one active application for this user, if any; see the active-status partial unique index. */
    Optional<GuestApplication> findActiveByUserId(UUID userId);

    /** Most recently created application for this user, active or not. */
    Optional<GuestApplication> findLatestByUserId(UUID userId);

    /** @param status null means every status */
    List<GuestApplication> findAll(GuestApplicationStatus status, int page, int size);

    long countAll(GuestApplicationStatus status);

    /**
     * Applications whose GUEST_APPLICATION_SUBMITTED event never reached the
     * broker — the outbox query, mirroring
     * {@link LoadPostPort#findPublishedAwaitingNotification}.
     */
    List<GuestApplication> findAwaitingNotification(int limit);
}
