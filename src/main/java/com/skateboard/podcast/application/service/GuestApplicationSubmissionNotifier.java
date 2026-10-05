package com.skateboard.podcast.application.service;

import com.skateboard.podcast.application.port.out.PublishDomainEventPort;
import com.skateboard.podcast.application.port.out.SaveGuestApplicationPort;
import com.skateboard.podcast.domain.model.GuestApplication;
import com.skateboard.podcast.infrastructure.messaging.EventTopology;
import com.skateboard.podcast.infrastructure.messaging.GuestApplicationNotificationProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Emits GUEST_APPLICATION_SUBMITTED, the event skateboard-notification-be
 * reacts to for the applicant confirmation email and the administrator
 * notifications (spec §5). Mirrors {@link PodcastPublicationNotifier}'s
 * outbox-without-a-table shape: {@code notified_at} records whether the
 * broker ever confirmed the event, and
 * {@link com.skateboard.podcast.adapter.in.scheduler.PendingGuestApplicationNotificationJob}
 * retries whatever didn't.
 *
 * <p>Recipient selection, the confirmation email template and the feature
 * enable flag are not in this payload — they live in
 * skateboard-app-config-be and are resolved downstream (BFF /
 * notification-be), same boundary GuestApplicationConfig's javadoc there
 * documents. This event states only what happened.
 */
@Service
public class GuestApplicationSubmissionNotifier {

    private static final Logger log = LoggerFactory.getLogger(GuestApplicationSubmissionNotifier.class);

    private static final String EVENT_TYPE = "GUEST_APPLICATION_SUBMITTED";
    private static final int EVENT_VERSION = 1;

    private final PublishDomainEventPort publishDomainEventPort;
    private final SaveGuestApplicationPort saveGuestApplicationPort;
    private final GuestApplicationNotificationProperties properties;

    public GuestApplicationSubmissionNotifier(PublishDomainEventPort publishDomainEventPort,
                                              SaveGuestApplicationPort saveGuestApplicationPort,
                                              GuestApplicationNotificationProperties properties) {
        this.publishDomainEventPort = publishDomainEventPort;
        this.saveGuestApplicationPort = saveGuestApplicationPort;
        this.properties = properties;
    }

    /** Never throws: the application is already saved, and a broker problem must not fail that request. */
    public boolean notifyIfSubmitted(GuestApplication application) {
        if (!qualifies(application)) {
            return false;
        }

        boolean published = publishDomainEventPort.publish(
                eventIdFor(application),
                EVENT_TYPE,
                EVENT_VERSION,
                properties.tenantId(),
                Instant.now(),
                EventTopology.GUEST_APPLICATION_SUBMITTED_ROUTING_KEY,
                payloadFor(application));

        if (!published) {
            // Left un-notified on purpose: PendingGuestApplicationNotificationJob
            // will find it again.
            log.warn("applicationId={} stays owed a GUEST_APPLICATION_SUBMITTED event", application.getId());
            return false;
        }

        application.markNotified();
        saveGuestApplicationPort.save(application);
        return true;
    }

    public boolean qualifies(GuestApplication application) {
        if (!properties.enabled()) {
            return false;
        }
        return application.getNotifiedAt() == null;
    }

    /**
     * A version-3 UUID over the application id, so the inline call at
     * submission time and a later reconciliation pass carry the same event
     * id and the consumer's idempotency ledger can collapse them.
     */
    public UUID eventIdFor(GuestApplication application) {
        return UUID.nameUUIDFromBytes((EVENT_TYPE + ":" + application.getId()).getBytes(StandardCharsets.UTF_8));
    }

    private Map<String, Object> payloadFor(GuestApplication application) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("applicationId", application.getId().toString());
        payload.put("userId", application.getUserId().toString());
        payload.put("name", application.getName());
        payload.put("email", application.getEmail());
        payload.put("message", application.getMessage());
        payload.put("socialLinks", application.getSocialLinks());
        payload.put("submittedAt", application.getCreatedAt().toString());
        return payload;
    }
}
