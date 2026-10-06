package com.skateboard.podcast.application.service;

import com.skateboard.podcast.application.port.out.PublishDomainEventPort;
import com.skateboard.podcast.application.port.out.SaveGuestApplicationPort;
import com.skateboard.podcast.domain.model.GuestApplication;
import com.skateboard.podcast.infrastructure.messaging.GuestApplicationNotificationProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

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

class GuestApplicationSubmissionNotifierTest {

    private static final UUID TENANT = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Mock private PublishDomainEventPort publishDomainEventPort;
    @Mock private SaveGuestApplicationPort saveGuestApplicationPort;

    private GuestApplicationSubmissionNotifier notifier;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        notifier = new GuestApplicationSubmissionNotifier(publishDomainEventPort, saveGuestApplicationPort,
                new GuestApplicationNotificationProperties(true, TENANT));
        when(publishDomainEventPort.publish(any(), anyString(), anyInt(), any(), any(), anyString(), any()))
                .thenReturn(true);
    }

    @Test
    void announcesANewlySubmittedApplication() {
        GuestApplication application = application();

        assertThat(notifier.notifyIfSubmitted(application)).isTrue();
        assertThat(application.getNotifiedAt()).isNotNull();
        verify(saveGuestApplicationPort).save(application);
    }

    @Test
    void doesNotAnnounceAnApplicationAlreadyAnnounced() {
        GuestApplication application = application();
        application.markNotified();

        assertThat(notifier.notifyIfSubmitted(application)).isFalse();
        verifyNoInteractions(publishDomainEventPort);
    }

    @Test
    void announcesNothingWhileTheFeatureIsSwitchedOff() {
        notifier = new GuestApplicationSubmissionNotifier(publishDomainEventPort, saveGuestApplicationPort,
                new GuestApplicationNotificationProperties(false, TENANT));

        assertThat(notifier.notifyIfSubmitted(application())).isFalse();
        verifyNoInteractions(publishDomainEventPort);
    }

    @Test
    void leavesTheApplicationOwedWhenTheBrokerDoesNotConfirm() {
        when(publishDomainEventPort.publish(any(), anyString(), anyInt(), any(), any(), anyString(), any()))
                .thenReturn(false);
        GuestApplication application = application();

        assertThat(notifier.notifyIfSubmitted(application)).isFalse();
        assertThat(application.getNotifiedAt()).isNull();
        verify(saveGuestApplicationPort, never()).save(any());
    }

    @Test
    void derivesTheSameEventIdEveryTimeForAGivenApplication() {
        GuestApplication application = application();

        assertThat(notifier.eventIdFor(application)).isEqualTo(notifier.eventIdFor(application));
    }

    @Test
    void givesDifferentApplicationsDifferentEventIds() {
        assertThat(notifier.eventIdFor(application())).isNotEqualTo(notifier.eventIdFor(application()));
    }

    @Test
    void carriesTheApplicantDataAndTenantInTheEvent() {
        GuestApplication application = application();

        notifier.notifyIfSubmitted(application);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> payload = ArgumentCaptor.forClass(Map.class);
        verify(publishDomainEventPort).publish(eq(notifier.eventIdFor(application)), eq("GUEST_APPLICATION_SUBMITTED"),
                eq(1), eq(TENANT), any(), eq("podcast.guest-application.submitted.v1"), payload.capture());

        assertThat(payload.getValue())
                .containsEntry("applicationId", application.getId().toString())
                .containsEntry("userId", application.getUserId().toString())
                .containsEntry("name", application.getName())
                .containsEntry("email", application.getEmail());
    }

    private GuestApplication application() {
        return GuestApplication.submit(UUID.randomUUID(), "Jane Doe", "jane@example.com",
                "I love skating", List.of("https://instagram.com/jane"));
    }
}
