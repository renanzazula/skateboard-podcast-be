package com.skateboard.podcast.application.service;

import com.skateboard.podcast.application.port.in.CreatePostUseCase;
import com.skateboard.podcast.application.port.out.LoadPostPort;
import com.skateboard.podcast.application.port.out.SavePostPort;
import com.skateboard.podcast.domain.model.Post;
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
 * import and the YouTube sync — so this is where "publishing an episode tells
 * subscribers about it" is either wired up or silently lost.
 *
 * <p>Whether a given post actually qualifies is
 * {@link PodcastPublicationNotifier}'s decision and is covered by its own
 * test. What matters here is that the notifier is consulted at all, and that it
 * is handed the <em>saved</em> post rather than the caller's input.
 */
class CreatePostServiceTest {

    @Mock
    private LoadPostPort loadPostPort;

    @Mock
    private SavePostPort savePostPort;

    @Mock
    private PodcastPublicationNotifier publicationNotifier;

    private CreatePostService service;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        service = new CreatePostService(loadPostPort, savePostPort, publicationNotifier);
        when(savePostPort.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    /**
     * The admin screen's default: the form posts status PUBLISHED with
     * publishAt set to now, so creating an episode there has to announce it.
     */
    @Test
    void creatingAPublishedEpisodeAnnouncesIt() {
        service.execute(new CreatePostUseCase.Input("Episode 42", "episode-42",
                PostStatus.PUBLISHED, Instant.now(), "cover.jpg", "[]", "[]", UUID.randomUUID()));

        verify(publicationNotifier).notifyIfNewlyPublished(any(Post.class));
    }

    /**
     * The notifier reads the post's id, slug and publishAt to build the event,
     * and writes notifiedAt back through the repository. Handing it anything
     * other than what was saved would emit an event pointing at a post that
     * does not exist under that identity.
     */
    @Test
    void announcesTheSavedPostNotTheInput() {
        service.execute(new CreatePostUseCase.Input("Episode 43", "episode-43",
                PostStatus.PUBLISHED, Instant.now(), "cover.jpg", "[]", "[]", UUID.randomUUID()));

        ArgumentCaptor<Post> saved = ArgumentCaptor.forClass(Post.class);
        verify(savePostPort).save(saved.capture());
        ArgumentCaptor<Post> announced = ArgumentCaptor.forClass(Post.class);
        verify(publicationNotifier).notifyIfNewlyPublished(announced.capture());

        assertThat(announced.getValue()).isSameAs(saved.getValue());
    }

    /**
     * A draft is not news. The notifier gates on this itself, but the slug
     * uniqueness loop and the notifier call sit in the same method, so a
     * refactor that reorders them would be caught here rather than by someone
     * receiving a push for an unfinished episode.
     */
    @Test
    void creatingADraftAnnouncesNothingWorthSending() {
        service.execute(new CreatePostUseCase.Input("Half-written", "half-written",
                PostStatus.DRAFT, null, null, "[]", "[]", UUID.randomUUID()));

        ArgumentCaptor<Post> announced = ArgumentCaptor.forClass(Post.class);
        verify(publicationNotifier).notifyIfNewlyPublished(announced.capture());
        assertThat(announced.getValue().getStatus()).isEqualTo(PostStatus.DRAFT);
    }

    /**
     * The slug collision path returns a different slug than the caller asked
     * for, and the announcement has to carry the one that was stored — the app
     * deep-links by slug, so announcing the requested one would send everybody
     * to a 404.
     */
    @Test
    void announcesTheDeduplicatedSlugWhenTheRequestedOneIsTaken() {
        when(loadPostPort.existsBySlug("episode-44")).thenReturn(true);
        when(loadPostPort.existsBySlug("episode-44-1")).thenReturn(false);

        service.execute(new CreatePostUseCase.Input("Episode 44", "episode-44",
                PostStatus.PUBLISHED, Instant.now(), "cover.jpg", "[]", "[]", UUID.randomUUID()));

        ArgumentCaptor<Post> announced = ArgumentCaptor.forClass(Post.class);
        verify(publicationNotifier).notifyIfNewlyPublished(announced.capture());
        assertThat(announced.getValue().getSlug()).isEqualTo("episode-44-1");
    }
}
