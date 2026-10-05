package com.skateboard.podcast.domain.model;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class GuestApplicationTest {

    @Test
    void submitStartsNewWithATimestampedCreationAndNoReviewer() {
        UUID userId = UUID.randomUUID();

        GuestApplication application = GuestApplication.submit(
                userId, "Jane Doe", "jane@example.com", "I love skating", List.of("https://instagram.com/jane"));

        assertThat(application.getId()).isNotNull();
        assertThat(application.getUserId()).isEqualTo(userId);
        assertThat(application.getStatus()).isEqualTo(GuestApplicationStatus.NEW);
        assertThat(application.isActive()).isTrue();
        assertThat(application.getSocialLinks()).containsExactly("https://instagram.com/jane");
        assertThat(application.getReviewedBy()).isNull();
        assertThat(application.getNotifiedAt()).isNull();
        assertThat(application.getCreatedAt()).isEqualTo(application.getUpdatedAt());
    }

    @Test
    void submitWithNoSocialLinksDefaultsToAnEmptyList() {
        GuestApplication application = GuestApplication.submit(
                UUID.randomUUID(), "Jane Doe", "jane@example.com", "I love skating", null);

        assertThat(application.getSocialLinks()).isEmpty();
    }

    @Test
    void updateStatusRecordsTheReviewerAndBumpsUpdatedAt() throws InterruptedException {
        GuestApplication application = GuestApplication.submit(
                UUID.randomUUID(), "Jane Doe", "jane@example.com", "I love skating", null);
        Instant createdUpdatedAt = application.getUpdatedAt();
        UUID admin = UUID.randomUUID();
        Thread.sleep(2);

        application.updateStatus(GuestApplicationStatus.CONTACTED, admin);

        assertThat(application.getStatus()).isEqualTo(GuestApplicationStatus.CONTACTED);
        assertThat(application.getReviewedBy()).isEqualTo(admin);
        assertThat(application.getUpdatedAt()).isAfter(createdUpdatedAt);
    }

    @Test
    void declinedIsNotActive() {
        GuestApplication application = GuestApplication.submit(
                UUID.randomUUID(), "Jane Doe", "jane@example.com", "I love skating", null);

        application.updateStatus(GuestApplicationStatus.DECLINED, UUID.randomUUID());

        assertThat(application.isActive()).isFalse();
    }

    @Test
    void markNotifiedSetsATimestamp() {
        GuestApplication application = GuestApplication.submit(
                UUID.randomUUID(), "Jane Doe", "jane@example.com", "I love skating", null);

        application.markNotified();

        assertThat(application.getNotifiedAt()).isNotNull();
    }

    @Test
    void reconstituteCarriesEveryFieldAsStored() {
        UUID id = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID reviewedBy = UUID.randomUUID();
        Instant createdAt = Instant.parse("2026-01-01T00:00:00Z");
        Instant updatedAt = Instant.parse("2026-01-02T00:00:00Z");
        Instant notifiedAt = Instant.parse("2026-01-02T00:05:00Z");

        GuestApplication application = GuestApplication.reconstitute(id, userId, "Jane Doe", "jane@example.com",
                "I love skating", List.of("https://x.com/jane"), GuestApplicationStatus.ACCEPTED,
                createdAt, updatedAt, reviewedBy, notifiedAt);

        assertThat(application.getId()).isEqualTo(id);
        assertThat(application.getUserId()).isEqualTo(userId);
        assertThat(application.getStatus()).isEqualTo(GuestApplicationStatus.ACCEPTED);
        assertThat(application.getCreatedAt()).isEqualTo(createdAt);
        assertThat(application.getUpdatedAt()).isEqualTo(updatedAt);
        assertThat(application.getReviewedBy()).isEqualTo(reviewedBy);
        assertThat(application.getNotifiedAt()).isEqualTo(notifiedAt);
    }
}
