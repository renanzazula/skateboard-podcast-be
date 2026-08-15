package com.skateboard.podcast.application.port.out;

import java.time.Instant;
import java.util.List;

/**
 * Outbound port to the YouTube Data API v3. Implementations live in
 * adapter/out/youtube — Google-specific response shapes must never leak
 * past this interface.
 */
public interface YoutubeContentPort {

    record YoutubeChannel(String channelId, String title, String uploadsPlaylistId) {}

    record YoutubeVideo(String videoId, String title, String description, Instant publishedAt, String thumbnailUrl) {}

    record YoutubeVideoDuration(String videoId, Integer durationSeconds) {}

    /** @throws YoutubeSyncException if the channel can't be resolved (not found, invalid key, transport failure). */
    YoutubeChannel resolveChannel(String channelId);

    /** Latest {@code limit} uploads, newest first. */
    List<YoutubeVideo> getLatestVideos(String uploadsPlaylistId, int limit);

    /** Batched duration lookup (YouTube allows up to 50 ids per call); implementations handle chunking. */
    List<YoutubeVideoDuration> getVideoDurations(List<String> videoIds);

    class YoutubeSyncException extends RuntimeException {
        public YoutubeSyncException(String message, Throwable cause) {
            super(message, cause);
        }

        public YoutubeSyncException(String message) {
            super(message);
        }
    }
}
