package com.skateboard.podcast.domain.model;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public class GuestApplication {

    private final UUID id;
    private final UUID userId;
    // Name/email/message are a snapshot of the applicant at submission time
    // (spec §4) — a later profile edit must not rewrite an existing
    // application, so none of this is re-read from the account afterwards.
    private final String name;
    private final String email;
    private final String message;
    private final List<String> socialLinks;
    private GuestApplicationStatus status;
    private final Instant createdAt;
    private Instant updatedAt;
    private UUID reviewedBy;
    // When GUEST_APPLICATION_SUBMITTED was successfully emitted. Null means
    // owed; see notified_at on posts for the identical reconciliation pattern.
    private Instant notifiedAt;

    private GuestApplication(UUID id, UUID userId, String name, String email, String message,
                             List<String> socialLinks, GuestApplicationStatus status,
                             Instant createdAt, Instant updatedAt, UUID reviewedBy, Instant notifiedAt) {
        this.id = id;
        this.userId = userId;
        this.name = name;
        this.email = email;
        this.message = message;
        this.socialLinks = socialLinks != null ? List.copyOf(socialLinks) : List.of();
        this.status = status;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.reviewedBy = reviewedBy;
        this.notifiedAt = notifiedAt;
    }

    public static GuestApplication submit(UUID userId, String name, String email, String message,
                                          List<String> socialLinks) {
        Instant now = Instant.now();
        return new GuestApplication(UUID.randomUUID(), userId, name, email, message, socialLinks,
                GuestApplicationStatus.NEW, now, now, null, null);
    }

    public static GuestApplication reconstitute(UUID id, UUID userId, String name, String email, String message,
                                                List<String> socialLinks, GuestApplicationStatus status,
                                                Instant createdAt, Instant updatedAt, UUID reviewedBy,
                                                Instant notifiedAt) {
        return new GuestApplication(id, userId, name, email, message, socialLinks, status,
                createdAt, updatedAt, reviewedBy, notifiedAt);
    }

    /** Admin review transition (spec §4's status table); any status is a valid target. */
    public void updateStatus(GuestApplicationStatus newStatus, UUID reviewedBy) {
        this.status = Objects.requireNonNull(newStatus, "newStatus");
        this.reviewedBy = reviewedBy;
        this.updatedAt = Instant.now();
    }

    /** Called only after the broker confirmed GUEST_APPLICATION_SUBMITTED; see {@link #notifiedAt}. */
    public void markNotified() {
        this.notifiedAt = Instant.now();
    }

    public boolean isActive() {
        return status.isActive();
    }

    public UUID getId()                   { return id; }
    public UUID getUserId()                { return userId; }
    public String getName()                { return name; }
    public String getEmail()               { return email; }
    public String getMessage()             { return message; }
    public List<String> getSocialLinks()   { return socialLinks; }
    public GuestApplicationStatus getStatus() { return status; }
    public Instant getCreatedAt()          { return createdAt; }
    public Instant getUpdatedAt()          { return updatedAt; }
    public UUID getReviewedBy()            { return reviewedBy; }
    public Instant getNotifiedAt()         { return notifiedAt; }
}
