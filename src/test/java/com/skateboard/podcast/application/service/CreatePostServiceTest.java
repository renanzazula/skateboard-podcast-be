package com.skateboard.podcast.application.service;

import com.skateboard.podcast.application.port.in.CreatePostUseCase;
import com.skateboard.podcast.application.port.out.LoadPostPort;
import com.skateboard.podcast.application.port.out.SavePostPort;
import com.skateboard.podcast.domain.model.Post;
import com.skateboard.podcast.domain.model.PostPlatform;
import com.skateboard.podcast.domain.model.PostPlatformLink;
import com.skateboard.podcast.domain.model.PostStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Every create path funnels through this service — the admin screen, the JSON
 * import and the YouTube sync.
 *
 * <p>Announcing is deliberately not one of its jobs: it saves a post and stops,
 * leaving PendingPodcastNotificationJob to find what is owed. What it does owe
 * that job is a post whose stored state is right, which is what these cases
 * pin down.
 */
class CreatePostServiceTest {

    @Mock
    private LoadPostPort loadPostPort;

    @Mock
    private SavePostPort savePostPort;

    private CreatePostService service;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        service = new CreatePostService(loadPostPort, savePostPort);
        when(savePostPort.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    private Post created(CreatePostUseCase.Input input) {
        service.execute(input);
        ArgumentCaptor<Post> saved = ArgumentCaptor.forClass(Post.class);
        verify(savePostPort).save(saved.capture());
        return saved.getValue();
    }

    /**
     * The admin screen's default: the form posts PUBLISHED with publishAt set
     * to now. Saved with no notifiedAt, that row is exactly what the job reads
     * as "this episode is owed an announcement".
     */
    @Test
    void aPublishedEpisodeIsSavedOwingAnAnnouncement() {
        Post post = created(new CreatePostUseCase.Input("Episode 42", "episode-42",
                PostStatus.PUBLISHED, Instant.now(), "cover.jpg", "[]", "[]", UUID.randomUUID()));

        assertThat(post.getStatus()).isEqualTo(PostStatus.PUBLISHED);
        assertThat(post.getNotifiedAt()).isNull();
    }

    /**
     * The collision path returns a different slug than the caller asked for,
     * and the stored one is what the announcement will carry — the app
     * deep-links by slug, so getting this wrong sends everybody to a 404.
     */
    @Test
    void aCollidingSlugIsDeduplicatedBeforeThePostIsStored() {
        when(loadPostPort.existsBySlug("episode-44")).thenReturn(true);
        when(loadPostPort.existsBySlug("episode-44-1")).thenReturn(false);

        Post post = created(new CreatePostUseCase.Input("Episode 44", "episode-44",
                PostStatus.PUBLISHED, Instant.now(), "cover.jpg", "[]", "[]", UUID.randomUUID()));

        assertThat(post.getSlug()).isEqualTo("episode-44-1");
    }

    /**
     * The YouTube sync's shape. The video id is both the sync's own dedup key
     * and the source of the platform link the episode screen renders.
     */
    @Test
    void aYoutubeVideoIdBecomesMetadataAndAPlatformLink() {
        Post post = created(new CreatePostUseCase.Input("Episode 45", "episode-45",
                PostStatus.PUBLISHED, Instant.now(), "cover.jpg", "[]", "[]", null,
                "vid-45", "Description", 3600, 45));

        assertThat(post.getYoutubeVideoId()).isEqualTo("vid-45");
        assertThat(post.getPlatformLinks()).singleElement().satisfies(link -> {
            assertThat(link.platform()).isEqualTo(PostPlatform.YOUTUBE);
            assertThat(link.externalId()).isEqualTo("vid-45");
        });
    }

    /**
     * A manually authored post carries no video id, so it must not acquire an
     * empty platform link — the episode screen renders one chip per link.
     */
    @Test
    void aManuallyAuthoredPostGetsNoPlatformLink() {
        Post post = created(new CreatePostUseCase.Input("Hand written", "hand-written",
                PostStatus.PUBLISHED, Instant.now(), "cover.jpg", "[]", "[]", UUID.randomUUID()));

        assertThat(post.getYoutubeVideoId()).isNull();
        assertThat(post.getPlatformLinks()).isEmpty();
    }
}
