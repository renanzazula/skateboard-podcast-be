package com.skateboard.podcast.adapter.in.rest;

import com.skateboard.application.dto.CreateGuestApplicationRequest;
import com.skateboard.application.dto.GuestApplicationPageResponse;
import com.skateboard.application.dto.GuestApplicationResponse;
import com.skateboard.application.dto.UpdateGuestApplicationStatusRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GuestApplicationControllerTest {

    @Mock
    private GuestApplicationService guestApplicationService;

    private GuestApplicationController controller;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        controller = new GuestApplicationController(guestApplicationService);
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private void authenticateAs(String name) {
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        Authentication authentication = new TestingAuthenticationToken(name, "n/a");
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
    }

    @Test
    void submitGuestApplicationReturns201WithTheResolvedUserId() {
        UUID userId = UUID.randomUUID();
        authenticateAs(userId.toString());
        CreateGuestApplicationRequest req = new CreateGuestApplicationRequest();
        GuestApplicationResponse expected = new GuestApplicationResponse();
        when(guestApplicationService.submit(req, userId)).thenReturn(expected);

        ResponseEntity<GuestApplicationResponse> response = controller.submitGuestApplication(req);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isSameAs(expected);
    }

    @Test
    void submitGuestApplicationResolvesANullUserIdWhenUnauthenticated() {
        CreateGuestApplicationRequest req = new CreateGuestApplicationRequest();
        GuestApplicationResponse expected = new GuestApplicationResponse();
        when(guestApplicationService.submit(req, null)).thenReturn(expected);

        controller.submitGuestApplication(req);

        verify(guestApplicationService).submit(req, null);
    }

    @Test
    void getMyGuestApplicationReturnsTheApplicationWhenPresent() {
        UUID userId = UUID.randomUUID();
        authenticateAs(userId.toString());
        GuestApplicationResponse expected = new GuestApplicationResponse();
        when(guestApplicationService.getMine(userId)).thenReturn(expected);

        ResponseEntity<GuestApplicationResponse> response = controller.getMyGuestApplication();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isSameAs(expected);
    }

    @Test
    void getMyGuestApplicationThrows404WhenTheUserNeverApplied() {
        UUID userId = UUID.randomUUID();
        authenticateAs(userId.toString());
        when(guestApplicationService.getMine(userId)).thenReturn(null);

        assertThatThrownBy(() -> controller.getMyGuestApplication())
                .isInstanceOf(ResponseStatusException.class)
                .hasFieldOrPropertyWithValue("statusCode", HttpStatus.NOT_FOUND);
    }

    @Test
    void getGuestApplicationsDefaultsPageAndSizeAndClampsSize() {
        GuestApplicationPageResponse expected = new GuestApplicationPageResponse();
        when(guestApplicationService.getAll(null, 0, 10)).thenReturn(expected);

        ResponseEntity<GuestApplicationPageResponse> response = controller.getGuestApplications(null, null, null);

        assertThat(response.getBody()).isSameAs(expected);

        when(guestApplicationService.getAll("NEW", 2, 50)).thenReturn(expected);
        controller.getGuestApplications("NEW", 2, 500);
        verify(guestApplicationService).getAll("NEW", 2, 50);
    }

    @Test
    void getGuestApplicationByIdReturns404WhenMissing() {
        UUID id = UUID.randomUUID();
        when(guestApplicationService.getById(id)).thenReturn(null);

        assertThatThrownBy(() -> controller.getGuestApplicationById(id))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void getGuestApplicationByIdReturnsTheApplicationWhenFound() {
        UUID id = UUID.randomUUID();
        GuestApplicationResponse expected = new GuestApplicationResponse();
        when(guestApplicationService.getById(id)).thenReturn(expected);

        ResponseEntity<GuestApplicationResponse> response = controller.getGuestApplicationById(id);

        assertThat(response.getBody()).isSameAs(expected);
    }

    @Test
    void updateGuestApplicationStatusResolvesTheReviewerFromTheSecurityContext() {
        UUID id = UUID.randomUUID();
        UUID admin = UUID.randomUUID();
        authenticateAs(admin.toString());
        UpdateGuestApplicationStatusRequest req =
                new UpdateGuestApplicationStatusRequest(UpdateGuestApplicationStatusRequest.StatusEnum.ACCEPTED);
        GuestApplicationResponse expected = new GuestApplicationResponse();
        when(guestApplicationService.updateStatus(id, req, admin)).thenReturn(expected);

        ResponseEntity<GuestApplicationResponse> response = controller.updateGuestApplicationStatus(id, req);

        assertThat(response.getBody()).isSameAs(expected);
    }
}
