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

    /**
     * Deleting an episode that has platform links used to fail with
     * post_platform_link_post_id_fkey — the admin screen surfaced it as
     * "Podcast service is currently unavailable", since the BFF maps a 500 to
     * an outage. Every synced episode has a YouTube link, so this was every
     * delete that mattered.
     *
     * <p>The link rows must be gone too, not merely detached: their
     * (platform, external_id) pair is UNIQUE, so a leftover row would keep the
     * video id claimed and block re-importing that episode — which is what the
     * re-import at the end of this test checks.
     */
    @Test
    void deletingAPostRemovesItsPlatformLinks() {
        Post post = Post.create("EP 26 Skateboarding", "ep-26-skateboarding-" + UUID.randomUUID(),
                PostStatus.PUBLISHED, null, null, "[]", "[]", null);
        String externalId = "yt-26-" + UUID.randomUUID();
        post.attachPlatformLink(new PostPlatformLink(PostPlatform.YOUTUBE, externalId,
                "https://www.youtube.com/watch?v=" + externalId));
        Post saved = savePostPort.save(post);
        UUID postId = saved.getId();
        assertThat(platformLinkRepository.findByPostId(postId)).hasSize(1);

        savePostPort.deleteById(postId.toString());

        assertThat(loadPostPort.findById(postId.toString())).isEmpty();
        assertThat(platformLinkRepository.findByPostId(postId)).isEmpty();

        // The external id is free again, which the unique constraint would not
        // allow if the link row had survived.
        Post reimported = Post.create("EP 26 Skateboarding", "ep-26-reimported-" + UUID.randomUUID(),
                PostStatus.PUBLISHED, null, null, "[]", "[]", null);
        reimported.attachPlatformLink(new PostPlatformLink(PostPlatform.YOUTUBE, externalId,
                "https://www.youtube.com/watch?v=" + externalId));
        assertThat(savePostPort.save(reimported).getId()).isNotNull();
    }
}
