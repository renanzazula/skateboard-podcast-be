package com.skateboard.podcast.application.service;

import com.skateboard.podcast.application.port.in.SubmitGuestApplicationUseCase;
import com.skateboard.podcast.application.port.out.LoadGuestApplicationPort;
import com.skateboard.podcast.application.port.out.SaveGuestApplicationPort;
import com.skateboard.podcast.domain.exception.DuplicateActiveApplicationException;
import com.skateboard.podcast.domain.model.GuestApplication;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class SubmitGuestApplicationService implements SubmitGuestApplicationUseCase {

    private static final int MAX_SOCIAL_LINKS = 3;

    private final LoadGuestApplicationPort loadGuestApplicationPort;
    private final SaveGuestApplicationPort saveGuestApplicationPort;
    private final GuestApplicationSubmissionNotifier submissionNotifier;

    public SubmitGuestApplicationService(LoadGuestApplicationPort loadGuestApplicationPort,
                                         SaveGuestApplicationPort saveGuestApplicationPort,
                                         GuestApplicationSubmissionNotifier submissionNotifier) {
        this.loadGuestApplicationPort = loadGuestApplicationPort;
        this.saveGuestApplicationPort = saveGuestApplicationPort;
        this.submissionNotifier = submissionNotifier;
    }

    @Override
    public GuestApplication execute(Input input) {
        validateSocialLinks(input.socialLinks());

        // Fast-fail pre-check for the common case; the active-status partial
        // unique index (ux_guest_applications_active_user) is what actually
        // makes this atomic under a concurrent resubmit — see
        // GuestApplicationPersistenceAdapter.save.
        if (loadGuestApplicationPort.findActiveByUserId(input.userId()).isPresent()) {
            throw new DuplicateActiveApplicationException(input.userId());
        }

        GuestApplication application = GuestApplication.submit(
                input.userId(), input.name(), input.email(), input.message(), input.socialLinks());
        GuestApplication saved = saveGuestApplicationPort.save(application);
        // Every submission funnels through here, mirroring CreatePostService's
        // single notify point.
        submissionNotifier.notifyIfSubmitted(saved);
        return saved;
    }

    private void validateSocialLinks(List<String> socialLinks) {
        if (socialLinks == null) {
            return;
        }
        if (socialLinks.size() > MAX_SOCIAL_LINKS) {
            throw new IllegalArgumentException("At most " + MAX_SOCIAL_LINKS + " social links are allowed");
        }
        for (String link : socialLinks) {
            if (link == null || !link.matches("^https?://.+")) {
                throw new IllegalArgumentException("Social links must be http or https URLs: " + link);
            }
        }
    }
}
