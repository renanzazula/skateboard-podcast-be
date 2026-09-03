package com.skateboard.podcast.infrastructure.messaging;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.UUID;

/**
 * Controls whether publishing a podcast tells anyone about it.
 *
 * @param enabled     off by default, so a deployment is silent until someone
 *                    deliberately turns it on. The same posture the magazine
 *                    plan takes with its no-op notification adapter
 * @param maxAgeHours how recent a post's publishAt must be to be worth
 *                    notifying about. This is the guard against the back
 *                    catalogue: the YouTube sync creates PUBLISHED posts
 *                    carrying each video's real publication date, which for an
 *                    existing channel is mostly years old
 * @param tenantId    the tenant every event is stamped with; this service has
 *                    no other source for it
 */
@ConfigurationProperties(prefix = "podcast.notifications")
public record PodcastNotificationProperties(boolean enabled, long maxAgeHours, UUID tenantId) {
}
