package com.skateboard.podcast.adapter.in.scheduler;

import com.skateboard.podcast.application.port.out.LoadGuestApplicationPort;
import com.skateboard.podcast.application.port.out.PublishDomainEventPort;
import com.skateboard.podcast.application.port.out.SaveGuestApplicationPort;
import com.skateboard.podcast.application.service.GuestApplicationSubmissionNotifier;
import com.skateboard.podcast.domain.model.GuestApplication;
import com.skateboard.podcast.infrastructure.messaging.GuestApplicationNotificationProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
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
 * The outbox-without-a-table recovery pass for guest applications, mirroring
 * {@link PendingPodcastNotificationJobTest}.
 */
@ExtendWith(MockitoExtension.class)
class PendingGuestApplicationNotificationJobTest {

    private static final UUID TENANT = UUID.randomUUID();

    @Mock
    private LoadGuestApplicationPort loadGuestApplicationPort;

    @Test
    void doesNothingWhenThereIsNoBacklog() {
        GuestApplicationSubmissionNotifier notifier = mock(GuestApplicationSubmissionNotifier.class);
        PendingGuestApplicationNotificationJob job =
                new PendingGuestApplicationNotificationJob(loadGuestApplicationPort, notifier);
        when(loadGuestApplicationPort.findAwaitingNotification(20)).thenReturn(List.of());

        job.run();

        verifyNoInteractions(notifier);
    }

    /** BATCH_LIMIT bounds one pass so a surprising backlog cannot become a push storm. */
    @Test
    void boundsTheQueryToTwentyApplications() {
        GuestApplicationSubmissionNotifier notifier = mock(GuestApplicationSubmissionNotifier.class);
        PendingGuestApplicationNotificationJob job =
                new PendingGuestApplicationNotificationJob(loadGuestApplicationPort, notifier);
        when(loadGuestApplicationPort.findAwaitingNotification(anyInt())).thenReturn(List.of());

        job.run();

        verify(loadGuestApplicationPort).findAwaitingNotification(20);
    }

    /**
     * Real end-to-end wiring through the actual notifier, so this proves the
     * job really reaches the broker for every pending application.
     */
    @Test
    void announcesEveryPendingApplicationThroughTheRealNotifier() {
        PublishDomainEventPort publishDomainEventPort = mock(PublishDomainEventPort.class);
        SaveGuestApplicationPort saveGuestApplicationPort = mock(SaveGuestApplicationPort.class);
        when(publishDomainEventPort.publish(any(), anyString(), anyInt(), any(), any(), anyString(), any()))
                .thenReturn(true);
        GuestApplicationSubmissionNotifier realNotifier = new GuestApplicationSubmissionNotifier(
                publishDomainEventPort, saveGuestApplicationPort,
                new GuestApplicationNotificationProperties(true, TENANT));
        PendingGuestApplicationNotificationJob job =
                new PendingGuestApplicationNotificationJob(loadGuestApplicationPort, realNotifier);

        GuestApplication first = application();
        GuestApplication second = application();
        when(loadGuestApplicationPort.findAwaitingNotification(20)).thenReturn(List.of(first, second));

        job.run();

        verify(publishDomainEventPort, times(2)).publish(any(), anyString(), anyInt(), any(), any(), anyString(), any());
        verify(saveGuestApplicationPort, times(2)).save(any());
        assertThat(first.getNotifiedAt()).isNotNull();
        assertThat(second.getNotifiedAt()).isNotNull();
    }

    @Test
    void announcesNothingThroughTheRealNotifierWhenNotificationsAreDisabled() {
        PublishDomainEventPort publishDomainEventPort = mock(PublishDomainEventPort.class);
        SaveGuestApplicationPort saveGuestApplicationPort = mock(SaveGuestApplicationPort.class);
        GuestApplicationSubmissionNotifier realNotifier = new GuestApplicationSubmissionNotifier(
                publishDomainEventPort, saveGuestApplicationPort,
                new GuestApplicationNotificationProperties(false, TENANT));
        PendingGuestApplicationNotificationJob job =
                new PendingGuestApplicationNotificationJob(loadGuestApplicationPort, realNotifier);

        GuestApplication pending = application();
        when(loadGuestApplicationPort.findAwaitingNotification(20)).thenReturn(List.of(pending));

        job.run();

        verifyNoInteractions(publishDomainEventPort);
        verifyNoInteractions(saveGuestApplicationPort);
        assertThat(pending.getNotifiedAt()).isNull();
    }

    /**
     * A failure announcing one application must not stop the rest of the
     * batch from being attempted.
     */
    @Test
    void aFailureAnnouncingOneApplicationDoesNotBlockTheOthers() {
        GuestApplicationSubmissionNotifier notifier = mock(GuestApplicationSubmissionNotifier.class);
        PendingGuestApplicationNotificationJob job =
                new PendingGuestApplicationNotificationJob(loadGuestApplicationPort, notifier);

        GuestApplication failing = application();
        GuestApplication succeeding = application();
        when(loadGuestApplicationPort.findAwaitingNotification(20)).thenReturn(List.of(failing, succeeding));
        when(notifier.notifyIfSubmitted(failing)).thenReturn(false);
        when(notifier.notifyIfSubmitted(succeeding)).thenReturn(true);

        job.run();

        verify(notifier).notifyIfSubmitted(failing);
        verify(notifier).notifyIfSubmitted(succeeding);
    }

    private GuestApplication application() {
        return GuestApplication.submit(UUID.randomUUID(), "Jane Doe", "jane@example.com", "I love skating", null);
    }
}
