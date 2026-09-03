package com.skateboard.podcast.application.port.out;

import com.skateboard.podcast.domain.model.Post;

import java.util.List;
import java.util.Optional;

public interface LoadPostPort {
    Optional<Post> findById(String id);
    Optional<Post> findBySlug(String slug);
    Optional<Post> findByYoutubeVideoId(String youtubeVideoId);
    List<Post> findPublished(int page, int size);
    long countPublished();
    List<Post> searchPublished(String query, int page, int size);
    long countSearchPublished(String query);
    List<Post> findAll(int page, int size);
    long countAll();
    boolean existsBySlug(String slug);

    /**
     * Published posts that were never successfully announced and are still
     * recent enough to be worth announcing.
     *
     * <p>This is the outbox, using the posts table itself: a post that was
     * saved but whose event never reached the broker is exactly a row with
     * {@code notified_at IS NULL}, so no second table is needed to find it.
     */
    List<Post> findPublishedAwaitingNotification(java.time.Instant publishedAfter, int limit);
}
