package com.skateboard.podcast.infrastructure.messaging;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.UUID;

/**
 * Controls whether submitting a guest application tells anyone about it.
 * Unlike {@link PodcastNotificationProperties}, there is no recency window:
 * a submission is always worth announcing, so there is no back-catalogue to
 * guard against.
 *
 * @param enabled  off by default, same posture as podcast.notifications
 * @param tenantId the tenant every event is stamped with
 */
@ConfigurationProperties(prefix = "podcast.guest-application-notifications")
public record GuestApplicationNotificationProperties(boolean enabled, UUID tenantId) {
}
