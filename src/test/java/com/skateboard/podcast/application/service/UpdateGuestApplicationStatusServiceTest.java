package com.skateboard.podcast.application.service;

import com.skateboard.podcast.application.port.in.UpdateGuestApplicationStatusUseCase;
import com.skateboard.podcast.application.port.out.LoadGuestApplicationPort;
import com.skateboard.podcast.application.port.out.SaveGuestApplicationPort;
import com.skateboard.podcast.domain.exception.GuestApplicationNotFoundException;
import com.skateboard.podcast.domain.model.GuestApplication;
import com.skateboard.podcast.domain.model.GuestApplicationStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UpdateGuestApplicationStatusServiceTest {

    @Mock private LoadGuestApplicationPort loadGuestApplicationPort;
    @Mock private SaveGuestApplicationPort saveGuestApplicationPort;

    private UpdateGuestApplicationStatusService service;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        service = new UpdateGuestApplicationStatusService(loadGuestApplicationPort, saveGuestApplicationPort);
        when(saveGuestApplicationPort.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void appliesTheNewStatusAndReviewerThenSaves() {
        UUID id = UUID.randomUUID();
        UUID admin = UUID.randomUUID();
        GuestApplication application = GuestApplication.submit(UUID.randomUUID(), "Jane", "j@example.com", "msg", null);
        when(loadGuestApplicationPort.findById(id)).thenReturn(Optional.of(application));

        GuestApplication result = service.execute(
                new UpdateGuestApplicationStatusUseCase.Input(id, GuestApplicationStatus.CONTACTED, admin));

        assertThat(result.getStatus()).isEqualTo(GuestApplicationStatus.CONTACTED);
        assertThat(result.getReviewedBy()).isEqualTo(admin);
        verify(saveGuestApplicationPort).save(application);
    }

    @Test
    void throwsWhenTheApplicationDoesNotExist() {
        UUID id = UUID.randomUUID();
        when(loadGuestApplicationPort.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.execute(
                new UpdateGuestApplicationStatusUseCase.Input(id, GuestApplicationStatus.ACCEPTED, UUID.randomUUID())))
                .isInstanceOf(GuestApplicationNotFoundException.class);
    }
}
