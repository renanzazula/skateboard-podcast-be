package com.skateboard.podcast.domain.exception;

import java.util.UUID;

public class DuplicateActiveApplicationException extends RuntimeException {

    public DuplicateActiveApplicationException(UUID userId) {
        super("User " + userId + " already has an active guest application");
    }
}
