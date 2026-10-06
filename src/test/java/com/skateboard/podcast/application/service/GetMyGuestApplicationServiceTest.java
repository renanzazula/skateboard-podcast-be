package com.skateboard.podcast.application.service;

import com.skateboard.podcast.application.port.out.LoadGuestApplicationPort;
import com.skateboard.podcast.domain.model.GuestApplication;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GetMyGuestApplicationServiceTest {

    @Mock private LoadGuestApplicationPort loadGuestApplicationPort;

    private GetMyGuestApplicationService service;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        service = new GetMyGuestApplicationService(loadGuestApplicationPort);
    }

    @Test
    void prefersTheActiveApplicationOverTheLatestOne() {
        UUID userId = UUID.randomUUID();
        GuestApplication active = GuestApplication.submit(userId, "Jane", "jane@example.com", "msg", null);
        when(loadGuestApplicationPort.findActiveByUserId(userId)).thenReturn(Optional.of(active));

        Optional<GuestApplication> result = service.execute(userId);

        assertThat(result).contains(active);
        verify(loadGuestApplicationPort, never()).findLatestByUserId(userId);
    }

    @Test
    void fallsBackToTheLatestApplicationWhenNoneIsActive() {
        UUID userId = UUID.randomUUID();
        GuestApplication declined = GuestApplication.submit(userId, "Jane", "jane@example.com", "msg", null);
        when(loadGuestApplicationPort.findActiveByUserId(userId)).thenReturn(Optional.empty());
        when(loadGuestApplicationPort.findLatestByUserId(userId)).thenReturn(Optional.of(declined));

        assertThat(service.execute(userId)).contains(declined);
    }

    @Test
    void isEmptyWhenTheUserNeverApplied() {
        UUID userId = UUID.randomUUID();
        when(loadGuestApplicationPort.findActiveByUserId(userId)).thenReturn(Optional.empty());
        when(loadGuestApplicationPort.findLatestByUserId(userId)).thenReturn(Optional.empty());

        assertThat(service.execute(userId)).isEmpty();
    }
}
