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
import static org.mockito.Mockito.when;

class GetGuestApplicationByIdServiceTest {

    @Mock private LoadGuestApplicationPort loadGuestApplicationPort;

    private GetGuestApplicationByIdService service;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        service = new GetGuestApplicationByIdService(loadGuestApplicationPort);
    }

    @Test
    void returnsTheApplicationWhenFound() {
        UUID id = UUID.randomUUID();
        GuestApplication application = GuestApplication.submit(UUID.randomUUID(), "Jane", "j@example.com", "msg", null);
        when(loadGuestApplicationPort.findById(id)).thenReturn(Optional.of(application));

        assertThat(service.execute(id)).contains(application);
    }

    @Test
    void isEmptyWhenNotFound() {
        UUID id = UUID.randomUUID();
        when(loadGuestApplicationPort.findById(id)).thenReturn(Optional.empty());

        assertThat(service.execute(id)).isEmpty();
    }
}
