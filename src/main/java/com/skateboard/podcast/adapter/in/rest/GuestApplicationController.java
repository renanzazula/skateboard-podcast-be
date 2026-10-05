package com.skateboard.podcast.adapter.in.rest;

import com.skateboard.application.dto.CreateGuestApplicationRequest;
import com.skateboard.application.dto.GuestApplicationPageResponse;
import com.skateboard.application.dto.GuestApplicationResponse;
import com.skateboard.application.dto.UpdateGuestApplicationStatusRequest;
import com.skateboard.infrastructure.web.api.GuestApplicationsApi;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

@RestController
public class GuestApplicationController implements GuestApplicationsApi {

    private final GuestApplicationService guestApplicationService;

    public GuestApplicationController(GuestApplicationService guestApplicationService) {
        this.guestApplicationService = guestApplicationService;
    }

    @Override
    @PreAuthorize("hasAuthority('FUNC_GUEST_APPLICATION_CREATE')")
    public ResponseEntity<GuestApplicationResponse> submitGuestApplication(CreateGuestApplicationRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(guestApplicationService.submit(req, resolveCurrentUserId()));
    }

    @Override
    @PreAuthorize("hasAuthority('FUNC_GUEST_APPLICATION_READ_OWN')")
    public ResponseEntity<GuestApplicationResponse> getMyGuestApplication() {
        GuestApplicationResponse response = guestApplicationService.getMine(resolveCurrentUserId());
        if (response == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No guest application for this user");
        }
        return ResponseEntity.ok(response);
    }

    @Override
    @PreAuthorize("hasAuthority('FUNC_GUEST_APPLICATION_MANAGE')")
    public ResponseEntity<GuestApplicationPageResponse> getGuestApplications(String status, Integer page, Integer size) {
        int p = page != null ? page : 0;
        int s = size != null ? Math.min(size, 50) : 10;
        return ResponseEntity.ok(guestApplicationService.getAll(status, p, s));
    }

    @Override
    @PreAuthorize("hasAuthority('FUNC_GUEST_APPLICATION_MANAGE')")
    public ResponseEntity<GuestApplicationResponse> getGuestApplicationById(UUID id) {
        GuestApplicationResponse response = guestApplicationService.getById(id);
        if (response == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Guest application not found");
        }
        return ResponseEntity.ok(response);
    }

    @Override
    @PreAuthorize("hasAuthority('FUNC_GUEST_APPLICATION_MANAGE')")
    public ResponseEntity<GuestApplicationResponse> updateGuestApplicationStatus(UUID id, UpdateGuestApplicationStatusRequest req) {
        return ResponseEntity.ok(guestApplicationService.updateStatus(id, req, resolveCurrentUserId()));
    }

    private UUID resolveCurrentUserId() {
        try {
            String name = SecurityContextHolder.getContext().getAuthentication().getName();
            return UUID.fromString(name);
        } catch (Exception e) {
            return null;
        }
    }
}
