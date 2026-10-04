package com.skateboard.podcast.adapter.out.persistence;

import com.skateboard.podcast.domain.model.EpisodeSearch;

/**
 * Turns an {@link EpisodeSearch} into the two bind parameters the search
 * {@code @Query}s take, so both feeds (all posts, one category) bind them
 * identically.
 */
final class EpisodeSearchParams {

    /**
     * Bound when the search names no episode number. Episode numbers are
     * positive, so {@code episodeNumber = -1} never matches — this keeps the
     * parameter a plain non-null integer rather than relying on how the driver
     * types a null in {@code :n IS NULL OR ...}.
     */
    static final int NO_EPISODE = -1;

    private EpisodeSearchParams() {}

    /**
     * {@code %text%} with LIKE's own wildcards escaped (escape char {@code \}),
     * so a search for "100%" or "a_b" matches those characters literally
     * instead of acting as a pattern.
     */
    static String likePattern(EpisodeSearch search) {
        String escaped = search.text()
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
        return "%" + escaped + "%";
    }

    static int episodeNumber(EpisodeSearch search) {
        return search.episodeNumber() != null ? search.episodeNumber() : NO_EPISODE;
    }
}
