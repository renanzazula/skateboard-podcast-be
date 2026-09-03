package com.skateboard.podcast.application.service;

import com.skateboard.podcast.application.port.out.PublishDomainEventPort;
import com.skateboard.podcast.application.port.out.SavePostPort;
import com.skateboard.podcast.domain.model.Post;
import com.skateboard.podcast.domain.model.PostStatus;
import com.skateboard.podcast.infrastructure.messaging.EventTopology;
import com.skateboard.podcast.infrastructure.messaging.PodcastNotificationProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The single place that decides whether publishing a podcast should tell
 * anyone about it.
 *
 * <p>Three gates, and each one exists because of a specific way this goes
 * wrong:
 * <ul>
 *   <li>{@code enabled} — a fresh deployment must be silent until someone
 *       chooses otherwise.</li>
 *   <li>{@code notifiedAt} — an edit of a published post, a re-sync, or a
 *       replayed reconciliation pass must not notify a second time.</li>
 *   <li>the recency window — the YouTube sync hard-codes {@code PUBLISHED} and
 *       carries each video's real publication date, so without this the first
 *       run against an existing channel would push the entire back
 *       catalogue.</li>
 * </ul>
 *
 * <p>The event id is derived from the post id rather than random. Two things
 * can emit for the same post — the inline call at publish time and the
 * reconciliation job — and a stable id is what lets the consumer recognise the
 * second one as a duplicate instead of sending twice.
 */
@Service
public class PodcastPublicationNotifier {

    private static final Logger log = LoggerFactory.getLogger(PodcastPublicationNotifier.class);

    private static final String EVENT_TYPE = "PODCAST_PUBLISHED";
    private static final int EVENT_VERSION = 1;

    private final PublishDomainEventPort publishDomainEventPort;
    private final SavePostPort savePostPort;
    private final PodcastNotificationProperties properties;

    public PodcastPublicationNotifier(PublishDomainEventPort publishDomainEventPort,
                                       SavePostPort savePostPort,
                                       PodcastNotificationProperties properties) {
        this.publishDomainEventPort = publishDomainEventPort;
        this.savePostPort = savePostPort;
        this.properties = properties;
    }

    /**
     * Emits PODCAST_PUBLISHED for a post if it qualifies, and records that it
     * did. Never throws: the post is already saved, and a broker problem must
     * not fail the request that saved it.
     *
     * @return true if an event was published and the post marked notified
     */
    public boolean notifyIfNewlyPublished(Post post) {
        if (!qualifies(post)) {
            return false;
        }

        boolean published = publishDomainEventPort.publish(
                eventIdFor(post),
                EVENT_TYPE,
                EVENT_VERSION,
                properties.tenantId(),
                Instant.now(),
                EventTopology.PODCAST_PUBLISHED_ROUTING_KEY,
                payloadFor(post));

        if (!published) {
            // Left un-notified on purpose: PendingPodcastNotificationJob will
            // find it again. Marking it here would lose the notification for
            // good in exchange for nothing.
            log.warn("postId={} stays owed a PODCAST_PUBLISHED event", post.getId());
            return false;
        }

        post.markNotified();
        savePostPort.save(post);
        return true;
    }

    public boolean qualifies(Post post) {
        if (!properties.enabled()) {
            return false;
        }
        if (post.getStatus() != PostStatus.PUBLISHED) {
            return false;
        }
        if (post.getNotifiedAt() != null) {
            return false;
        }
        return isRecent(post.getPublishAt());
    }

    /**
     * A post with no publishAt is not treated as recent. The feed sorts by it
     * and the sync always sets it, so a null one is an oddity — and "notify
     * about something with no publication date" is not a call worth making
     * automatically.
     */
    private boolean isRecent(Instant publishAt) {
        if (publishAt == null) {
            return false;
        }
        Instant cutoff = Instant.now().minus(Duration.ofHours(properties.maxAgeHours()));
        return publishAt.isAfter(cutoff);
    }

    /**
     * A version-3 UUID over the post id, so every emission for a given post
     * carries the same event id and the consumer's idempotency ledger can
     * collapse them.
     */
    public UUID eventIdFor(Post post) {
        return UUID.nameUUIDFromBytes((EVENT_TYPE + ":" + post.getId()).getBytes(StandardCharsets.UTF_8));
    }

    private Map<String, Object> payloadFor(Post post) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("podcastId", post.getId().toString());
        // The app routes by slug, not by id — without this the notification
        // cannot deep-link anywhere.
        payload.put("slug", post.getSlug());
        payload.put("title", post.getTitle());
        payload.put("imageUrl", post.getCoverUrl());
        payload.put("publishedAt", post.getPublishAt() == null ? null : post.getPublishAt().toString());
        return payload;
    }
}
