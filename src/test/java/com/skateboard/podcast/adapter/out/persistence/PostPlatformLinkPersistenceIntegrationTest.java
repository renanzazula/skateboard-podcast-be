package com.skateboard.podcast.adapter.out.persistence;

import com.skateboard.podcast.application.port.out.LoadPostPort;
import com.skateboard.podcast.application.port.out.SavePostPort;
import com.skateboard.podcast.domain.model.Post;
import com.skateboard.podcast.domain.model.PostPlatform;
import com.skateboard.podcast.domain.model.PostPlatformLink;
import com.skateboard.podcast.domain.model.PostStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Real Postgres (Testcontainers) behind the full Flyway stack, including
 * V5__post_platform_links.sql — verifies PostPersistenceAdapter round-trips
 * {@code Post.platformLinks} and that re-saving with the same links is
 * idempotent (no duplicate rows), per
 * .docs/README_SPOTIFY_YOUTUBE_PODCAST_INTEGRATION.md §11.
 */
@SpringBootTest(properties = {
        "spring.security.oauth2.resourceserver.jwt.issuer-uri=http://localhost:0/realms/test",
        "app.security.oauth2.audience=skateboard-podcast-be"
})
@Testcontainers
class PostPlatformLinkPersistenceIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @Autowired
    private LoadPostPort loadPostPort;

    @Autowired
    private SavePostPort savePostPort;

    @Autowired
    private SpringPostPlatformLinkRepository platformLinkRepository;

    @Test
    void platformLinksRoundTripAndReSavingDoesNotDuplicateRows() {
        Post post = Post.create("EP 24 Skateboarding", "ep-24-skateboarding-" + UUID.randomUUID(),
                PostStatus.PUBLISHED, null, null, "[]", "[]", null);
        post.attachPlatformLink(new PostPlatformLink(PostPlatform.YOUTUBE, "yt-24", "https://www.youtube.com/watch?v=yt-24"));
        post.attachPlatformLink(new PostPlatformLink(PostPlatform.SPOTIFY, "sp-24", "https://open.spotify.com/episode/sp-24"));
        Post saved = savePostPort.save(post);

        Optional<Post> reloaded = loadPostPort.findById(saved.getId().toString());

        assertThat(reloaded).isPresent();
        assertThat(reloaded.get().getPlatformLinks()).hasSize(2);
        assertThat(reloaded.get().getPlatformLinks())
                .extracting(PostPlatformLink::platform)
                .containsExactlyInAnyOrder(PostPlatform.YOUTUBE, PostPlatform.SPOTIFY);
        assertThat(platformLinkRepository.findByPostId(saved.getId())).hasSize(2);

        // Re-save with the same domain state (as a re-run of the Spotify match would do).
        savePostPort.save(reloaded.get());

        assertThat(platformLinkRepository.findByPostId(saved.getId())).hasSize(2);
    }

    @Test
    void attachingAReplacementLinkForTheSamePlatformOverwritesTheOldOne() {
        Post post = Post.create("EP 25 Skateboarding", "ep-25-skateboarding-" + UUID.randomUUID(),
                PostStatus.PUBLISHED, null, null, "[]", "[]", null);
        post.attachPlatformLink(new PostPlatformLink(PostPlatform.SPOTIFY, "sp-old", "https://open.spotify.com/episode/sp-old"));
        Post saved = savePostPort.save(post);

        Post reloaded = loadPostPort.findById(saved.getId().toString()).orElseThrow();
        reloaded.attachPlatformLink(new PostPlatformLink(PostPlatform.SPOTIFY, "sp-new", "https://open.spotify.com/episode/sp-new"));
        savePostPort.save(reloaded);

        Post reReloaded = loadPostPort.findById(saved.getId().toString()).orElseThrow();
        assertThat(reReloaded.getPlatformLinks()).singleElement()
                .satisfies(link -> assertThat(link.externalId()).isEqualTo("sp-new"));
    }
}
