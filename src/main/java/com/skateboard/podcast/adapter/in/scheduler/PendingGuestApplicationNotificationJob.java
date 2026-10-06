package com.skateboard.podcast.adapter.in.scheduler;

import com.skateboard.podcast.application.port.out.LoadGuestApplicationPort;
import com.skateboard.podcast.application.service.GuestApplicationSubmissionNotifier;
import com.skateboard.podcast.domain.model.GuestApplication;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Re-emits GUEST_APPLICATION_SUBMITTED for applications whose event never
 * reached the broker — the same outbox-without-a-table recovery as
 * {@link PendingPodcastNotificationJob}, over {@code notified_at IS NULL}
 * instead of the posts table. Safe to run repeatedly: the event id is
 * derived from the application id, so a re-emission of something that did
 * get through carries the same id and notification-be's idempotency ledger
 * drops it.
 */
@Component
@ConditionalOnProperty(prefix = "podcast.guest-application-notifications", name = "enabled", havingValue = "true")
public class PendingGuestApplicationNotificationJob {

    private static final Logger log = LoggerFactory.getLogger(PendingGuestApplicationNotificationJob.class);

    /** Bounds one pass, so a surprising backlog cannot become a push storm. */
    private static final int BATCH_LIMIT = 20;

    private final LoadGuestApplicationPort loadGuestApplicationPort;
    private final GuestApplicationSubmissionNotifier submissionNotifier;

    public PendingGuestApplicationNotificationJob(LoadGuestApplicationPort loadGuestApplicationPort,
                                                  GuestApplicationSubmissionNotifier submissionNotifier) {
        this.loadGuestApplicationPort = loadGuestApplicationPort;
        this.submissionNotifier = submissionNotifier;
    }

    @Scheduled(cron = "${podcast.guest-application-notifications.cron}")
    @SchedulerLock(name = "pendingGuestApplicationNotifications", lockAtMostFor = "4m")
    public void run() {
        List<GuestApplication> pending = loadGuestApplicationPort.findAwaitingNotification(BATCH_LIMIT);
        if (pending.isEmpty()) {
            return;
        }

        int announced = 0;
        for (GuestApplication application : pending) {
            if (submissionNotifier.notifyIfSubmitted(application)) {
                announced++;
            }
        }
        log.info("Reconciled guest application notifications: {} pending, {} announced", pending.size(), announced);
    }
}
