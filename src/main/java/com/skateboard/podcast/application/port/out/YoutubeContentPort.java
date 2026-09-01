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

    /** {@code publishedAt} is the video's actual publication time, not the time it was added to a playlist. */
    record YoutubeVideo(String videoId, String title, String description, Instant publishedAt,
                        String thumbnailUrl, Integer thumbnailWidth, Integer thumbnailHeight) {

        /** For callers that don't have (or don't care about) the thumbnail's pixel dimensions. */
        public YoutubeVideo(String videoId, String title, String description, Instant publishedAt, String thumbnailUrl) {
            this(videoId, title, description, publishedAt, thumbnailUrl, null, null);
        }
    }

    record YoutubeVideoDuration(String videoId, Integer durationSeconds) {}

    record YoutubePlaylist(String playlistId, String title, String description, String thumbnailUrl) {}

    /** @throws YoutubeSyncException if the channel can't be resolved (not found, invalid key, transport failure). */
    YoutubeChannel resolveChannel(String channelId);

    /** Latest {@code limit} uploads, newest first. */
    List<YoutubeVideo> getLatestVideos(String uploadsPlaylistId, int limit);

    /** Batched duration lookup (YouTube allows up to 50 ids per call); implementations handle chunking. */
    List<YoutubeVideoDuration> getVideoDurations(List<String> videoIds);

    /** All public playlists for the channel, fully paginated. */
    List<YoutubePlaylist> getPlaylists(String channelId);

    /** Every item in the playlist, fully paginated — unlike {@link #getLatestVideos}, no cap. */
    List<YoutubeVideo> getAllPlaylistItems(String playlistId);

    class YoutubeSyncException extends RuntimeException {
        public YoutubeSyncException(String message, Throwable cause) {
            super(message, cause);
        }

        public YoutubeSyncException(String message) {
            super(message);
        }
    }
}
