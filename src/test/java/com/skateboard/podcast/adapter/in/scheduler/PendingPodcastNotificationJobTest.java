package com.skateboard.podcast.adapter.in.scheduler;

import com.skateboard.podcast.application.port.out.LoadPostPort;
import com.skateboard.podcast.application.service.PodcastPublicationNotifier;
import com.skateboard.podcast.domain.model.Post;
import com.skateboard.podcast.domain.model.PostStatus;
import com.skateboard.podcast.infrastructure.messaging.PodcastNotificationProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * This job is the only thing that announces an episode — creating or updating
 * a post deliberately does not — so what it asks for and what it does with the
 * answer is the whole feature.
 *
 * <p>Whether an individual post qualifies belongs to
 * {@link PodcastPublicationNotifier} and is covered by its own test.
 */
class PendingPodcastNotificationJobTest {

    private static final int MAX_AGE_HOURS = 48;

    @Mock
    private LoadPostPort loadPostPort;

    @Mock
    private PodcastPublicationNotifier publicationNotifier;

    private PendingPodcastNotificationJob job;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        job = new PendingPodcastNotificationJob(loadPostPort, publicationNotifier,
                new PodcastNotificationProperties(true, MAX_AGE_HOURS,
                        UUID.fromString("00000000-0000-0000-0000-000000000001")));
    }

    private static Post published(String slug) {
        return Post.create("Episode " + slug, slug, PostStatus.PUBLISHED,
                Instant.now(), "cover.jpg", "[]", "[]", UUID.randomUUID());
    }

    /**
     * The case that replaced announcing from the request: an episode published
     * from the admin screen is just a row, and this pass is what tells anyone
     * about it.
     */
    @Test
    void announcesEveryPostTheQueryReturns() {
        List<Post> pending = List.of(published("ep-1"), published("ep-2"));
        when(loadPostPort.findPublishedAwaitingNotification(any(), anyInt())).thenReturn(pending);

        job.run();

        verify(publicationNotifier).notifyIfNewlyPublished(pending.get(0));
        verify(publicationNotifier).notifyIfNewlyPublished(pending.get(1));
    }

    /**
     * The idle case, which is almost every pass — a run every five minutes
     * must not touch the broker when there is nothing owed.
     */
    @Test
    void announcesNothingWhenNothingIsOwed() {
        when(loadPostPort.findPublishedAwaitingNotification(any(), anyInt())).thenReturn(List.of());

        job.run();

        verify(publicationNotifier, never()).notifyIfNewlyPublished(any());
    }

    /**
     * The back-catalogue guard has to be in the query, not only in the
     * notifier: without the cutoff the job would page through every
     * un-notified post ever written to have each one rejected, and the batch
     * limit would mean it never reached the recent ones.
     */
    @Test
    void asksOnlyForPostsInsideTheRecencyWindow() {
        when(loadPostPort.findPublishedAwaitingNotification(any(), anyInt())).thenReturn(List.of());
        Instant before = Instant.now().minus(Duration.ofHours(MAX_AGE_HOURS));

        job.run();

        ArgumentCaptor<Instant> publishedAfter = ArgumentCaptor.forClass(Instant.class);
        verify(loadPostPort).findPublishedAwaitingNotification(publishedAfter.capture(), anyInt());
        assertThat(publishedAfter.getValue())
                .isBetween(before, Instant.now().minus(Duration.ofHours(MAX_AGE_HOURS)));
    }

    /**
     * One pass is bounded so a surprising backlog — a bulk import, or the flag
     * being switched on for the first time — cannot become a push storm.
     */
    @Test
    void boundsHowMuchOnePassCanAnnounce() {
        when(loadPostPort.findPublishedAwaitingNotification(any(), anyInt())).thenReturn(List.of());

        job.run();

        ArgumentCaptor<Integer> limit = ArgumentCaptor.forClass(Integer.class);
        verify(loadPostPort).findPublishedAwaitingNotification(any(), limit.capture());
        assertThat(limit.getValue()).isEqualTo(20);
    }
}
