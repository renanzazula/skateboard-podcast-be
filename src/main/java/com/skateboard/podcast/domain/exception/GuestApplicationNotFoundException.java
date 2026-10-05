package com.skateboard.podcast.domain.exception;

public class GuestApplicationNotFoundException extends RuntimeException {

    public GuestApplicationNotFoundException(String id) {
        super("Guest application not found: " + id);
    }
}
