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
import org.springframework.stereotype.Service;

import java.net.URI;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

/**
 * Maps between the generated guest-application DTOs and the domain, same
 * role {@link PodcastService} plays for podcasts. Not cached: unlike the
 * podcast feed this data is per-user/per-admin-query, not global content.
 */
@Service
public class GuestApplicationService {

    private final SubmitGuestApplicationUseCase submitGuestApplicationUseCase;
    private final GetMyGuestApplicationUseCase getMyGuestApplicationUseCase;
    private final GetGuestApplicationsUseCase getGuestApplicationsUseCase;
    private final GetGuestApplicationByIdUseCase getGuestApplicationByIdUseCase;
    private final UpdateGuestApplicationStatusUseCase updateGuestApplicationStatusUseCase;

    public GuestApplicationService(SubmitGuestApplicationUseCase submitGuestApplicationUseCase,
                                   GetMyGuestApplicationUseCase getMyGuestApplicationUseCase,
                                   GetGuestApplicationsUseCase getGuestApplicationsUseCase,
                                   GetGuestApplicationByIdUseCase getGuestApplicationByIdUseCase,
                                   UpdateGuestApplicationStatusUseCase updateGuestApplicationStatusUseCase) {
        this.submitGuestApplicationUseCase = submitGuestApplicationUseCase;
        this.getMyGuestApplicationUseCase = getMyGuestApplicationUseCase;
        this.getGuestApplicationsUseCase = getGuestApplicationsUseCase;
        this.getGuestApplicationByIdUseCase = getGuestApplicationByIdUseCase;
        this.updateGuestApplicationStatusUseCase = updateGuestApplicationStatusUseCase;
    }

    public GuestApplicationResponse submit(CreateGuestApplicationRequest req, UUID userId) {
        List<String> socialLinks = req.getSocialLinks() == null ? null
                : req.getSocialLinks().stream().map(URI::toString).toList();
        GuestApplication application = submitGuestApplicationUseCase.execute(new SubmitGuestApplicationUseCase.Input(
                userId, req.getName(), req.getEmail(), req.getMessage(), socialLinks));
        return toDto(application);
    }

    /** Returns {@code null} when the user never applied; the controller maps null to 404. */
    public GuestApplicationResponse getMine(UUID userId) {
        return getMyGuestApplicationUseCase.execute(userId).map(this::toDto).orElse(null);
    }

    public GuestApplicationPageResponse getAll(String status, int page, int size) {
        GetGuestApplicationsUseCase.Result result = getGuestApplicationsUseCase.execute(
                new GetGuestApplicationsUseCase.Input(parseStatus(status), page, size));
        return new GuestApplicationPageResponse()
                .applications(result.applications().stream().map(this::toDto).toList())
                .total(result.total())
                .page(page)
                .size(size);
    }

    /** Returns {@code null} when no application matches; the controller maps null to 404. */
    public GuestApplicationResponse getById(UUID id) {
        return getGuestApplicationByIdUseCase.execute(id).map(this::toDto).orElse(null);
    }

    public GuestApplicationResponse updateStatus(UUID id, UpdateGuestApplicationStatusRequest req, UUID reviewedBy) {
        GuestApplication application = updateGuestApplicationStatusUseCase.execute(
                new UpdateGuestApplicationStatusUseCase.Input(id, parseStatus(req.getStatus().getValue()), reviewedBy));
        return toDto(application);
    }

    private GuestApplicationStatus parseStatus(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return GuestApplicationStatus.valueOf(raw.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Unknown guest application status: " + raw);
        }
    }

    private GuestApplicationResponse toDto(GuestApplication a) {
        return new GuestApplicationResponse()
                .id(a.getId())
                .userId(a.getUserId())
                .name(a.getName())
                .email(a.getEmail())
                .message(a.getMessage())
                .socialLinks(a.getSocialLinks())
                .status(GuestApplicationResponse.StatusEnum.fromValue(a.getStatus().name()))
                .createdAt(a.getCreatedAt().atOffset(ZoneOffset.UTC))
                .updatedAt(a.getUpdatedAt().atOffset(ZoneOffset.UTC))
                .reviewedBy(a.getReviewedBy());
    }
}
