package com.skateboard.podcast.adapter.out.persistence;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Plain getter/setter coverage for the post_platform_link entity, on top of
 * the behavior PostPersistenceAdapterTest already exercises indirectly via
 * toLinkEntity/toLink. Pins each field individually so a forgotten setter (or
 * a getter returning the wrong field after a copy/paste) shows up directly.
 */
class PostPlatformLinkJpaEntityTest {

    @Test
    void gettersReturnValuesSetBySetters() {
        PostPlatformLinkJpaEntity entity = new PostPlatformLinkJpaEntity();
        UUID id = UUID.randomUUID();
        UUID postId = UUID.randomUUID();
        Instant createdAt = Instant.parse("2024-01-01T00:00:00Z");
        Instant updatedAt = Instant.parse("2024-01-02T00:00:00Z");

        entity.setId(id);
        entity.setPostId(postId);
        entity.setPlatform("YOUTUBE");
        entity.setExternalId("yt-123");
        entity.setExternalUrl("https://www.youtube.com/watch?v=yt-123");
        entity.setCreatedAt(createdAt);
        entity.setUpdatedAt(updatedAt);

        assertThat(entity.getId()).isEqualTo(id);
        assertThat(entity.getPostId()).isEqualTo(postId);
        assertThat(entity.getPlatform()).isEqualTo("YOUTUBE");
        assertThat(entity.getExternalId()).isEqualTo("yt-123");
        assertThat(entity.getExternalUrl()).isEqualTo("https://www.youtube.com/watch?v=yt-123");
        assertThat(entity.getCreatedAt()).isEqualTo(createdAt);
        assertThat(entity.getUpdatedAt()).isEqualTo(updatedAt);
    }

    @Test
    void fieldsAreNullBeforeAnySetterIsCalled() {
        PostPlatformLinkJpaEntity entity = new PostPlatformLinkJpaEntity();

        assertThat(entity.getId()).isNull();
        assertThat(entity.getPostId()).isNull();
        assertThat(entity.getPlatform()).isNull();
        assertThat(entity.getExternalId()).isNull();
        assertThat(entity.getExternalUrl()).isNull();
        assertThat(entity.getCreatedAt()).isNull();
        assertThat(entity.getUpdatedAt()).isNull();
    }

    @Test
    void supportsBothPlatformValuesAsPlainStrings() {
        PostPlatformLinkJpaEntity youtube = new PostPlatformLinkJpaEntity();
        youtube.setPlatform("YOUTUBE");
        PostPlatformLinkJpaEntity spotify = new PostPlatformLinkJpaEntity();
        spotify.setPlatform("SPOTIFY");

        assertThat(youtube.getPlatform()).isEqualTo("YOUTUBE");
        assertThat(spotify.getPlatform()).isEqualTo("SPOTIFY");
    }
}
