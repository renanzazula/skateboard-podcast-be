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
 * Emits PODCAST_PUBLISHED for posts that are published but not yet announced.
 * This is the <em>only</em> thing that announces an episode — every route into
 * the feed (the admin screen, the JSON import, the YouTube sync) just leaves a
 * row for it to find.
 *
 * <p>This is the transactional outbox, without an outbox table. Announcing
 * from the request that saved the post would put a broker call in the admin's
 * path, and would still need this job for the case where that call fails. The
 * posts table already records what is owed as {@code notified_at IS NULL}, so
 * one query covers both, and creating a post stays a database write that
 * cannot be broken by RabbitMQ being down.
 *
 * <p>The cost is latency: an episode is announced on the next pass rather than
 * the instant it is saved, which for "a new podcast is out" is not a deadline
 * worth complicating the write path for.
 *
 * <p>Safe to run repeatedly because the event id is derived from the post id:
 * a pass that emitted but died before committing {@code notifiedAt} emits the
 * same id next time, and skateboard-notification-be's idempotency ledger drops
 * it.
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
