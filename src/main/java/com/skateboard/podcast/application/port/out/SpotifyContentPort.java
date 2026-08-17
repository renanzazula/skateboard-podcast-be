package com.skateboard.podcast.application.port.out;

import java.time.Instant;
import java.util.List;

/**
 * Outbound port to the Spotify Web API. Implementations live in
 * adapter/out/spotify — Spotify-specific response shapes must never leak
 * past this interface (see .docs/README_SPOTIFY_YOUTUBE_PODCAST_INTEGRATION.md §7).
 */
public interface SpotifyContentPort {

    record SpotifyEpisode(String id, String title, String description, Instant releaseDate,
                          Integer durationSeconds, String externalUrl, String imageUrl) {}

    /** All episodes for the configured show, fully paginated. */
    List<SpotifyEpisode> getShowEpisodes(String showId);

    class SpotifySyncException extends RuntimeException {
        public SpotifySyncException(String message, Throwable cause) {
            super(message, cause);
        }

        public SpotifySyncException(String message) {
            super(message);
        }
    }
}
