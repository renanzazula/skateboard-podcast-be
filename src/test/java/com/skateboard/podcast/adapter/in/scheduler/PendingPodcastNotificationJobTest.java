package com.skateboard.podcast.adapter.in.scheduler;

import com.skateboard.podcast.application.port.out.LoadPostPort;
import com.skateboard.podcast.application.port.out.PublishDomainEventPort;
import com.skateboard.podcast.application.port.out.SavePostPort;
import com.skateboard.podcast.application.service.PodcastPublicationNotifier;
import com.skateboard.podcast.domain.model.Post;
import com.skateboard.podcast.domain.model.PostStatus;
import com.skateboard.podcast.infrastructure.messaging.PodcastNotificationProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * This job is the transactional outbox without an outbox table (see
 * CLAUDE.md, "Publishing a podcast notifies subscribers"): a post saved but
 * never announced is exactly a row with {@code notified_at IS NULL}, and this
 * class's whole job is to find and re-announce those.
 */
@ExtendWith(MockitoExtension.class)
class PendingPodcastNotificationJobTest {

    private static final UUID TENANT = UUID.randomUUID();

    @Mock
    private LoadPostPort loadPostPort;

    @Test
    void doesNothingWhenThereIsNoBacklog() {
        PodcastPublicationNotifier notifier = mock(PodcastPublicationNotifier.class);
        PendingPodcastNotificationJob job = new PendingPodcastNotificationJob(loadPostPort, notifier,
                new PodcastNotificationProperties(true, 48, TENANT));
        when(loadPostPort.findPublishedAwaitingNotification(any(), eq(20))).thenReturn(List.of());

        job.run();

        verifyNoInteractions(notifier);
    }

    /** BATCH_LIMIT bounds one pass so a surprising backlog cannot become a push storm. */
    @Test
    void boundsTheQueryToTwentyPostsAndUsesTheConfiguredRecencyWindow() {
        PodcastPublicationNotifier notifier = mock(PodcastPublicationNotifier.class);
        PendingPodcastNotificationJob job = new PendingPodcastNotificationJob(loadPostPort, notifier,
                new PodcastNotificationProperties(true, 48, TENANT));
        when(loadPostPort.findPublishedAwaitingNotification(any(), anyInt())).thenReturn(List.of());

        job.run();

        ArgumentCaptor<Instant> publishedAfter = ArgumentCaptor.forClass(Instant.class);
        ArgumentCaptor<Integer> limit = ArgumentCaptor.forClass(Integer.class);
        verify(loadPostPort).findPublishedAwaitingNotification(publishedAfter.capture(), limit.capture());

        assertThat(limit.getValue()).isEqualTo(20);
        Instant expectedCutoff = Instant.now().minus(java.time.Duration.ofHours(48));
        assertThat(publishedAfter.getValue()).isCloseTo(expectedCutoff, within(5, ChronoUnit.SECONDS));
    }

    /**
     * Real end-to-end wiring through the actual PodcastPublicationNotifier
     * (rather than mocking it away) so this test proves the job really
     * reaches the broker for every qualifying pending post, not just that it
     * calls some mock.
     */
    @Test
    void announcesEveryQualifyingPendingPostThroughTheRealNotifier() {
        PublishDomainEventPort publishDomainEventPort = mock(PublishDomainEventPort.class);
        SavePostPort savePostPort = mock(SavePostPort.class);
        when(publishDomainEventPort.publish(any(), anyString(), anyInt(), any(), any(), anyString(), any()))
                .thenReturn(true);
        PodcastPublicationNotifier realNotifier = new PodcastPublicationNotifier(publishDomainEventPort, savePostPort,
                new PodcastNotificationProperties(true, 48, TENANT));
        PendingPodcastNotificationJob job = new PendingPodcastNotificationJob(loadPostPort, realNotifier,
                new PodcastNotificationProperties(true, 48, TENANT));

        Post first = numberedEpisode("first-episode", "Skateboard Podcast #1");
        Post second = numberedEpisode("second-episode", "Skateboard Podcast #2");
        when(loadPostPort.findPublishedAwaitingNotification(any(), eq(20))).thenReturn(List.of(first, second));

        job.run();

        verify(publishDomainEventPort, times(2)).publish(any(), anyString(), anyInt(), any(), any(), anyString(), any());
        verify(savePostPort, times(2)).save(any());
        assertThat(first.getNotifiedAt()).isNotNull();
        assertThat(second.getNotifiedAt()).isNotNull();
    }

    /**
     * Notifications turned off is the default posture for a fresh deployment:
     * the backlog is still found, but the real notifier's own "enabled" gate
     * refuses to announce any of it.
     */
    @Test
    void announcesNothingThroughTheRealNotifierWhenNotificationsAreDisabled() {
        PublishDomainEventPort publishDomainEventPort = mock(PublishDomainEventPort.class);
        SavePostPort savePostPort = mock(SavePostPort.class);
        PodcastPublicationNotifier realNotifier = new PodcastPublicationNotifier(publishDomainEventPort, savePostPort,
                new PodcastNotificationProperties(false, 48, TENANT));
        PendingPodcastNotificationJob job = new PendingPodcastNotificationJob(loadPostPort, realNotifier,
                new PodcastNotificationProperties(false, 48, TENANT));

        Post pending = numberedEpisode("first-episode", "Skateboard Podcast #1");
        when(loadPostPort.findPublishedAwaitingNotification(any(), eq(20))).thenReturn(List.of(pending));

        job.run();

        verifyNoInteractions(publishDomainEventPort);
        verifyNoInteractions(savePostPort);
        assertThat(pending.getNotifiedAt()).isNull();
    }

    /**
     * A failure/refusal announcing one post (broker unreachable, or the post
     * simply doesn't qualify) must not stop the rest of the batch from being
     * attempted — the whole point of a bounded reconciliation pass.
     */
    @Test
    void aFailureAnnouncingOnePostDoesNotBlockTheOthers() {
        PodcastPublicationNotifier notifier = mock(PodcastPublicationNotifier.class);
        PendingPodcastNotificationJob job = new PendingPodcastNotificationJob(loadPostPort, notifier,
                new PodcastNotificationProperties(true, 48, TENANT));

        Post failing = numberedEpisode("failing-episode", "Skateboard Podcast #1");
        Post succeeding = numberedEpisode("succeeding-episode", "Skateboard Podcast #2");
        when(loadPostPort.findPublishedAwaitingNotification(any(), eq(20))).thenReturn(List.of(failing, succeeding));
        when(notifier.notifyIfNewlyPublished(failing)).thenReturn(false);
        when(notifier.notifyIfNewlyPublished(succeeding)).thenReturn(true);

        job.run();

        verify(notifier).notifyIfNewlyPublished(failing);
        verify(notifier).notifyIfNewlyPublished(succeeding);
    }

    private Post numberedEpisode(String slug, String title) {
        return Post.reconstitute(UUID.randomUUID(), slug, title, PostStatus.PUBLISHED,
                Instant.now().minus(1, ChronoUnit.HOURS), "cover.jpg", null, null, "[]", "[]",
                Instant.now(), Instant.now(), UUID.randomUUID(), "yt-id", null, null, 1, null, List.of());
    }
}
