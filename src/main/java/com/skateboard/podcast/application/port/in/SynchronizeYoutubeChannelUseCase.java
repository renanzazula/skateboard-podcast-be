package com.skateboard.podcast.application.port.in;

public interface SynchronizeYoutubeChannelUseCase {

    record Result(int received, int created, int existing, int categoryChanges, boolean success,
                  int spotifyMatched, int spotifyUnmatched, int spotifyErrors) {

        /** YouTube-only convenience constructor — Spotify counts default to 0 (e.g. the sync failed before reaching Spotify). */
        public Result(int received, int created, int existing, int categoryChanges, boolean success) {
            this(received, created, existing, categoryChanges, success, 0, 0, 0);
        }
    }

    /** Never throws — a failed sync is reported via {@link Result#success()}, not an exception. */
    Result execute();
}
