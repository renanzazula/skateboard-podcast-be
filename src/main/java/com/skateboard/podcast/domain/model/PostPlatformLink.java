package com.skateboard.podcast.domain.model;

/** An episode's link on one distribution platform. At most one per {@link PostPlatform} per post. */
public record PostPlatformLink(PostPlatform platform, String externalId, String externalUrl) {
}
