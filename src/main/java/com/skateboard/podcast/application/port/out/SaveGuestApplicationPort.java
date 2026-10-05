package com.skateboard.podcast.application.port.out;

import com.skateboard.podcast.domain.exception.DuplicateActiveApplicationException;
import com.skateboard.podcast.domain.model.GuestApplication;

public interface SaveGuestApplicationPort {
    /** @throws DuplicateActiveApplicationException if the active-status unique index was violated */
    GuestApplication save(GuestApplication application);
}
