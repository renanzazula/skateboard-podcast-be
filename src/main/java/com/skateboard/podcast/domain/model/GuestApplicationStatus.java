package com.skateboard.podcast.domain.model;

public enum GuestApplicationStatus {
    NEW, CONTACTED, ACCEPTED, DECLINED;

    /** Active statuses block a new application from the same user; DECLINED does not. */
    public boolean isActive() {
        return this != DECLINED;
    }
}
