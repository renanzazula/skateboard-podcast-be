package com.skateboard.podcast.application.service;

import com.skateboard.podcast.application.port.in.GetGuestApplicationsUseCase;
import com.skateboard.podcast.application.port.out.LoadGuestApplicationPort;
import com.skateboard.podcast.domain.model.GuestApplication;
import com.skateboard.podcast.domain.model.GuestApplicationStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

class GetGuestApplicationsServiceTest {

    @Mock private LoadGuestApplicationPort loadGuestApplicationPort;

    private GetGuestApplicationsService service;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        service = new GetGuestApplicationsService(loadGuestApplicationPort);
    }

    @Test
    void returnsThePageAndTotalFromThePort() {
        GuestApplication application = GuestApplication.submit(UUID.randomUUID(), "Jane", "j@example.com", "msg", null);
        when(loadGuestApplicationPort.findAll(GuestApplicationStatus.NEW, 0, 10)).thenReturn(List.of(application));
        when(loadGuestApplicationPort.countAll(GuestApplicationStatus.NEW)).thenReturn(1L);

        GetGuestApplicationsUseCase.Result result = service.execute(
                new GetGuestApplicationsUseCase.Input(GuestApplicationStatus.NEW, 0, 10));

        assertThat(result.applications()).containsExactly(application);
        assertThat(result.total()).isEqualTo(1L);
    }

    @Test
    void passesANullStatusThroughAsEveryStatus() {
        when(loadGuestApplicationPort.findAll(null, 0, 10)).thenReturn(List.of());
        when(loadGuestApplicationPort.countAll(null)).thenReturn(0L);

        GetGuestApplicationsUseCase.Result result = service.execute(
                new GetGuestApplicationsUseCase.Input(null, 0, 10));

        assertThat(result.applications()).isEmpty();
        assertThat(result.total()).isZero();
    }
}
