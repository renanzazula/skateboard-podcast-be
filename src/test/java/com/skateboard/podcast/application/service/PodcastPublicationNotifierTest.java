package com.skateboard.podcast.application.service;

import com.skateboard.podcast.application.port.out.PublishDomainEventPort;
import com.skateboard.podcast.application.port.out.SavePostPort;
import com.skateboard.podcast.domain.model.Post;
import com.skateboard.podcast.domain.model.PostStatus;
import com.skateboard.podcast.infrastructure.messaging.PodcastNotificationProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Which published podcasts are worth telling users about.
 *
 * <p>The expensive mistake here is the back catalogue: the YouTube sync
 * hard-codes PUBLISHED and carries each video's real publication date, which
 * for an established channel is mostly years old. A first sync that announced
 * all of it would be a push storm, and there is no way to take it back.
 */
class PodcastPublicationNotifierTest {

    private static final UUID TENANT = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Mock private PublishDomainEventPort publishDomainEventPort;
    @Mock private SavePostPort savePostPort;

    private PodcastPublicationNotifier notifier;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        notifier = new PodcastPublicationNotifier(publishDomainEventPort, savePostPort,
                new PodcastNotificationProperties(true, 48, TENANT));
        when(publishDomainEventPort.publish(any(), anyString(), anyInt(), any(), any(), anyString(), any()))
                .thenReturn(true);
    }

    @Test
    void announcesAnEpisodePublishedJustNow() {
        Post post = publishedPost(Instant.now().minus(1, ChronoUnit.HOURS));

        assertThat(notifier.notifyIfNewlyPublished(post)).isTrue();
        assertThat(post.getNotifiedAt()).isNotNull();
        verify(savePostPort).save(post);
    }

    @Test
    void doesNotAnnounceAnEpisodeFromTheBackCatalogue() {
        Post post = publishedPost(Instant.now().minus(400, ChronoUnit.DAYS));

        assertThat(notifier.notifyIfNewlyPublished(post)).isFalse();
        assertThat(post.getNotifiedAt()).isNull();
        verifyNoInteractions(publishDomainEventPort);
    }

    @Test
    void doesNotAnnounceAPostThatWasAlreadyAnnounced() {
        Post post = publishedPost(Instant.now().minus(1, ChronoUnit.HOURS));
        post.markNotified();

        assertThat(notifier.notifyIfNewlyPublished(post)).isFalse();
        verifyNoInteractions(publishDomainEventPort);
    }

    @Test
    void doesNotAnnounceADraft() {
        Post post = Post.create("Draft episode", "draft-episode", PostStatus.DRAFT,
                Instant.now(), "cover.jpg", "[]", "[]", UUID.randomUUID());

        assertThat(notifier.notifyIfNewlyPublished(post)).isFalse();
        verifyNoInteractions(publishDomainEventPort);
    }

    /**
     * Nothing should be announced until someone turns the feature on
     * deliberately — a deploy is not that decision.
     */
    @Test
    void announcesNothingWhileTheFeatureIsSwitchedOff() {
        notifier = new PodcastPublicationNotifier(publishDomainEventPort, savePostPort,
                new PodcastNotificationProperties(false, 48, TENANT));

        assertThat(notifier.notifyIfNewlyPublished(publishedPost(Instant.now()))).isFalse();
        verifyNoInteractions(publishDomainEventPort);
    }

    /** The feed sorts by publishAt and the sync always sets it; a null is an oddity, not news. */
    @Test
    void doesNotAnnounceAPostWithNoPublishDate() {
        Post post = publishedPost(null);

        assertThat(notifier.notifyIfNewlyPublished(post)).isFalse();
        verifyNoInteractions(publishDomainEventPort);
    }

    /**
     * The post stays owed so the reconciliation job finds it again. Marking it
     * here would lose the notification permanently in exchange for nothing.
     */
    @Test
    void leavesThePostOwedWhenTheBrokerDoesNotConfirm() {
        when(publishDomainEventPort.publish(any(), anyString(), anyInt(), any(), any(), anyString(), any()))
                .thenReturn(false);
        Post post = publishedPost(Instant.now());

        assertThat(notifier.notifyIfNewlyPublished(post)).isFalse();
        assertThat(post.getNotifiedAt()).isNull();
        verify(savePostPort, never()).save(any());
    }

    /**
     * The inline publish and the reconciliation job can both fire for one post.
     * A stable event id is what lets the consumer collapse them instead of
     * sending twice.
     */
    @Test
    void derivesTheSameEventIdEveryTimeForAGivenPost() {
        Post post = publishedPost(Instant.now());

        assertThat(notifier.eventIdFor(post)).isEqualTo(notifier.eventIdFor(post));
    }

    @Test
    void givesDifferentPostsDifferentEventIds() {
        assertThat(notifier.eventIdFor(publishedPost(Instant.now())))
                .isNotEqualTo(notifier.eventIdFor(publishedPost(Instant.now())));
    }

    /** The app routes by slug, so an event without one cannot deep-link. */
    @Test
    void carriesTheSlugAndTenantInTheEvent() {
        Post post = publishedPost(Instant.now());

        notifier.notifyIfNewlyPublished(post);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> payload = ArgumentCaptor.forClass(Map.class);
        verify(publishDomainEventPort).publish(eq(notifier.eventIdFor(post)), eq("PODCAST_PUBLISHED"),
                eq(1), eq(TENANT), any(), eq("podcast.published.v1"), payload.capture());

        assertThat(payload.getValue())
                .containsEntry("podcastId", post.getId().toString())
                .containsEntry("slug", post.getSlug())
                .containsEntry("title", post.getTitle());
    }

    private Post publishedPost(Instant publishAt) {
        return Post.reconstitute(UUID.randomUUID(), "barcelona-street-sessions-14",
                "Barcelona Street Sessions #14", PostStatus.PUBLISHED, publishAt,
                "cover.jpg", null, null, "[]", "[]", Instant.now(), Instant.now(), UUID.randomUUID(),
                null, null, null, 14, null, List.of());
    }
}
