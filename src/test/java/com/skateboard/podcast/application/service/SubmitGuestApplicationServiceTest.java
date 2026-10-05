package com.skateboard.podcast.application.service;

import com.skateboard.podcast.application.port.in.SubmitGuestApplicationUseCase;
import com.skateboard.podcast.application.port.out.LoadGuestApplicationPort;
import com.skateboard.podcast.application.port.out.SaveGuestApplicationPort;
import com.skateboard.podcast.domain.exception.DuplicateActiveApplicationException;
import com.skateboard.podcast.domain.model.GuestApplication;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SubmitGuestApplicationServiceTest {

    @Mock private LoadGuestApplicationPort loadGuestApplicationPort;
    @Mock private SaveGuestApplicationPort saveGuestApplicationPort;
    @Mock private GuestApplicationSubmissionNotifier submissionNotifier;

    private SubmitGuestApplicationService service;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        service = new SubmitGuestApplicationService(loadGuestApplicationPort, saveGuestApplicationPort, submissionNotifier);
        when(loadGuestApplicationPort.findActiveByUserId(any())).thenReturn(Optional.empty());
        when(saveGuestApplicationPort.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void submitsAndNotifiesWhenTheUserHasNoActiveApplication() {
        UUID userId = UUID.randomUUID();

        GuestApplication saved = service.execute(new SubmitGuestApplicationUseCase.Input(
                userId, "Jane Doe", "jane@example.com", "I love skating", List.of("https://instagram.com/jane")));

        assertThat(saved.getUserId()).isEqualTo(userId);
        assertThat(saved.getName()).isEqualTo("Jane Doe");
        verify(saveGuestApplicationPort).save(any());
        verify(submissionNotifier).notifyIfSubmitted(saved);
    }

    @Test
    void rejectsASecondSubmissionWhileOneIsAlreadyActive() {
        UUID userId = UUID.randomUUID();
        when(loadGuestApplicationPort.findActiveByUserId(userId)).thenReturn(Optional.of(
                GuestApplication.submit(userId, "Jane Doe", "jane@example.com", "existing", null)));

        assertThatThrownBy(() -> service.execute(new SubmitGuestApplicationUseCase.Input(
                userId, "Jane Doe", "jane@example.com", "I love skating", null)))
                .isInstanceOf(DuplicateActiveApplicationException.class);

        verify(saveGuestApplicationPort, never()).save(any());
    }

    @Test
    void rejectsANonHttpSocialLink() {
        assertThatThrownBy(() -> service.execute(new SubmitGuestApplicationUseCase.Input(
                UUID.randomUUID(), "Jane Doe", "jane@example.com", "I love skating",
                List.of("ftp://not-allowed.example"))))
                .isInstanceOf(IllegalArgumentException.class);

        verify(saveGuestApplicationPort, never()).save(any());
    }

    @Test
    void rejectsMoreThanThreeSocialLinks() {
        assertThatThrownBy(() -> service.execute(new SubmitGuestApplicationUseCase.Input(
                UUID.randomUUID(), "Jane Doe", "jane@example.com", "I love skating",
                List.of("https://a.example", "https://b.example", "https://c.example", "https://d.example"))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void acceptsHttpAndHttpsLinksWithNoLimitViolation() {
        GuestApplication saved = service.execute(new SubmitGuestApplicationUseCase.Input(
                UUID.randomUUID(), "Jane Doe", "jane@example.com", "I love skating",
                List.of("http://a.example", "https://b.example")));

        assertThat(saved.getSocialLinks()).containsExactly("http://a.example", "https://b.example");
    }
}
