package com.skateboard.podcast.adapter.in.rest;

import com.skateboard.application.dto.CreateGuestApplicationRequest;
import com.skateboard.application.dto.GuestApplicationPageResponse;
import com.skateboard.application.dto.GuestApplicationResponse;
import com.skateboard.application.dto.UpdateGuestApplicationStatusRequest;
import com.skateboard.podcast.application.port.in.GetGuestApplicationByIdUseCase;
import com.skateboard.podcast.application.port.in.GetGuestApplicationsUseCase;
import com.skateboard.podcast.application.port.in.GetMyGuestApplicationUseCase;
import com.skateboard.podcast.application.port.in.SubmitGuestApplicationUseCase;
import com.skateboard.podcast.application.port.in.UpdateGuestApplicationStatusUseCase;
import com.skateboard.podcast.domain.model.GuestApplication;
import com.skateboard.podcast.domain.model.GuestApplicationStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.net.URI;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

class GuestApplicationServiceTest {

    @Mock private SubmitGuestApplicationUseCase submitGuestApplicationUseCase;
    @Mock private GetMyGuestApplicationUseCase getMyGuestApplicationUseCase;
    @Mock private GetGuestApplicationsUseCase getGuestApplicationsUseCase;
    @Mock private GetGuestApplicationByIdUseCase getGuestApplicationByIdUseCase;
    @Mock private UpdateGuestApplicationStatusUseCase updateGuestApplicationStatusUseCase;

    private GuestApplicationService service;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        service = new GuestApplicationService(submitGuestApplicationUseCase, getMyGuestApplicationUseCase,
                getGuestApplicationsUseCase, getGuestApplicationByIdUseCase, updateGuestApplicationStatusUseCase);
    }

    private GuestApplication application(GuestApplicationStatus status) {
        GuestApplication a = GuestApplication.submit(UUID.randomUUID(), "Jane Doe", "jane@example.com",
                "I love skating", List.of("https://instagram.com/jane"));
        if (status != GuestApplicationStatus.NEW) {
            a.updateStatus(status, UUID.randomUUID());
        }
        return a;
    }

    @Test
    void submitConvertsUriSocialLinksToStringsForTheUseCase() {
        UUID userId = UUID.randomUUID();
        CreateGuestApplicationRequest req = new CreateGuestApplicationRequest()
                .name("Jane Doe").email("jane@example.com").message("I love skating")
                .socialLinks(List.of(URI.create("https://instagram.com/jane")));
        when(submitGuestApplicationUseCase.execute(any())).thenReturn(application(GuestApplicationStatus.NEW));

        service.submit(req, userId);

        ArgumentCaptor<SubmitGuestApplicationUseCase.Input> captor =
                ArgumentCaptor.forClass(SubmitGuestApplicationUseCase.Input.class);
        org.mockito.Mockito.verify(submitGuestApplicationUseCase).execute(captor.capture());
        assertThat(captor.getValue().userId()).isEqualTo(userId);
        assertThat(captor.getValue().socialLinks()).containsExactly("https://instagram.com/jane");
    }

    @Test
    void submitMapsTheDomainResultToADto() {
        GuestApplication application = application(GuestApplicationStatus.NEW);
        when(submitGuestApplicationUseCase.execute(any())).thenReturn(application);

        GuestApplicationResponse response = service.submit(
                new CreateGuestApplicationRequest().name("Jane Doe").email("jane@example.com").message("msg"),
                application.getUserId());

        assertThat(response.getId()).isEqualTo(application.getId());
        assertThat(response.getStatus()).isEqualTo(GuestApplicationResponse.StatusEnum.NEW);
        assertThat(response.getSocialLinks()).containsExactly("https://instagram.com/jane");
    }

    @Test
    void getMineReturnsNullWhenTheUserNeverApplied() {
        UUID userId = UUID.randomUUID();
        when(getMyGuestApplicationUseCase.execute(userId)).thenReturn(Optional.empty());

        assertThat(service.getMine(userId)).isNull();
    }

    @Test
    void getAllBuildsAPageResponse() {
        GuestApplication application = application(GuestApplicationStatus.NEW);
        when(getGuestApplicationsUseCase.execute(any())).thenReturn(
                new GetGuestApplicationsUseCase.Result(List.of(application), 1L));

        GuestApplicationPageResponse response = service.getAll("NEW", 0, 10);

        assertThat(response.getApplications()).hasSize(1);
        assertThat(response.getTotal()).isEqualTo(1L);
        assertThat(response.getPage()).isZero();
        assertThat(response.getSize()).isEqualTo(10);
    }

    @Test
    void getAllParsesTheStatusFilterCaseInsensitively() {
        when(getGuestApplicationsUseCase.execute(any())).thenReturn(new GetGuestApplicationsUseCase.Result(List.of(), 0));

        service.getAll("declined", 0, 10);

        ArgumentCaptor<GetGuestApplicationsUseCase.Input> captor =
                ArgumentCaptor.forClass(GetGuestApplicationsUseCase.Input.class);
        org.mockito.Mockito.verify(getGuestApplicationsUseCase).execute(captor.capture());
        assertThat(captor.getValue().status()).isEqualTo(GuestApplicationStatus.DECLINED);
    }

    @Test
    void getAllWithABlankStatusMeansEveryStatus() {
        when(getGuestApplicationsUseCase.execute(any())).thenReturn(new GetGuestApplicationsUseCase.Result(List.of(), 0));

        service.getAll(null, 0, 10);

        ArgumentCaptor<GetGuestApplicationsUseCase.Input> captor =
                ArgumentCaptor.forClass(GetGuestApplicationsUseCase.Input.class);
        org.mockito.Mockito.verify(getGuestApplicationsUseCase).execute(captor.capture());
        assertThat(captor.getValue().status()).isNull();
    }

    @Test
    void getAllRejectsAnUnknownStatus() {
        assertThatThrownBy(() -> service.getAll("not-a-status", 0, 10))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void getByIdReturnsNullWhenNotFound() {
        UUID id = UUID.randomUUID();
        when(getGuestApplicationByIdUseCase.execute(id)).thenReturn(Optional.empty());

        assertThat(service.getById(id)).isNull();
    }

    @Test
    void updateStatusMapsRequestAndResultThroughTheUseCase() {
        UUID id = UUID.randomUUID();
        UUID reviewer = UUID.randomUUID();
        GuestApplication updated = application(GuestApplicationStatus.CONTACTED);
        when(updateGuestApplicationStatusUseCase.execute(any())).thenReturn(updated);

        GuestApplicationResponse response = service.updateStatus(id,
                new UpdateGuestApplicationStatusRequest(UpdateGuestApplicationStatusRequest.StatusEnum.CONTACTED),
                reviewer);

        ArgumentCaptor<UpdateGuestApplicationStatusUseCase.Input> captor =
                ArgumentCaptor.forClass(UpdateGuestApplicationStatusUseCase.Input.class);
        org.mockito.Mockito.verify(updateGuestApplicationStatusUseCase).execute(captor.capture());
        assertThat(captor.getValue().id()).isEqualTo(id);
        assertThat(captor.getValue().status()).isEqualTo(GuestApplicationStatus.CONTACTED);
        assertThat(captor.getValue().reviewedBy()).isEqualTo(reviewer);
        assertThat(response.getStatus()).isEqualTo(GuestApplicationResponse.StatusEnum.CONTACTED);
    }
}
