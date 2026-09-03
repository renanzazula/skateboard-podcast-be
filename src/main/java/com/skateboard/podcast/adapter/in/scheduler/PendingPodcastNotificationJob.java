package com.skateboard.podcast.adapter.in.scheduler;

import com.skateboard.podcast.application.port.out.LoadPostPort;
import com.skateboard.podcast.application.service.PodcastPublicationNotifier;
import com.skateboard.podcast.domain.model.Post;
import com.skateboard.podcast.infrastructure.messaging.PodcastNotificationProperties;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Re-emits PODCAST_PUBLISHED for posts that were published but never
 * successfully announced.
 *
 * <p>This is the transactional outbox, without an outbox table. A post is
 * saved and then an event is published; if the second step fails — the broker
 * is down, the network blips, the process dies in between — the podcast exists
 * and nobody is ever told. The posts table already records exactly that state
 * as {@code notified_at IS NULL}, so the recovery is a query rather than a
 * second table to keep consistent.
 *
 * <p>Safe to run repeatedly because the event id is derived from the post id:
 * a re-emission of something that did get through carries the same id, and
 * skateboard-notification-be's idempotency ledger drops it.
 *
 * <p>ShedLock, like {@link YoutubeSyncJob}, so multiple instances do not each
 * announce the same backlog.
 */
@Component
@ConditionalOnProperty(prefix = "podcast.notifications", name = "enabled", havingValue = "true")
public class PendingPodcastNotificationJob {

    private static final Logger log = LoggerFactory.getLogger(PendingPodcastNotificationJob.class);

    /** Bounds one pass, so a surprising backlog cannot become a push storm. */
    private static final int BATCH_LIMIT = 20;

    private final LoadPostPort loadPostPort;
    private final PodcastPublicationNotifier publicationNotifier;
    private final PodcastNotificationProperties properties;

    public PendingPodcastNotificationJob(LoadPostPort loadPostPort,
                                          PodcastPublicationNotifier publicationNotifier,
                                          PodcastNotificationProperties properties) {
        this.loadPostPort = loadPostPort;
        this.publicationNotifier = publicationNotifier;
        this.properties = properties;
    }

    @Scheduled(cron = "${podcast.notifications.cron}")
    @SchedulerLock(name = "pendingPodcastNotifications", lockAtMostFor = "4m")
    public void run() {
        Instant publishedAfter = Instant.now().minus(Duration.ofHours(properties.maxAgeHours()));
        List<Post> pending = loadPostPort.findPublishedAwaitingNotification(publishedAfter, BATCH_LIMIT);
        if (pending.isEmpty()) {
            return;
        }

        int announced = 0;
        for (Post post : pending) {
            if (publicationNotifier.notifyIfNewlyPublished(post)) {
                announced++;
            }
        }
        log.info("Reconciled podcast notifications: {} pending, {} announced", pending.size(), announced);
    }
}
