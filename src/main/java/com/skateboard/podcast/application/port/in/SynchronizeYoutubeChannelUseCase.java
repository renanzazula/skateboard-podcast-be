package com.skateboard.podcast.application.port.in;

public interface SynchronizeYoutubeChannelUseCase {

    record Result(int received, int created, int existing, int categoryChanges, boolean success) {}

    /** Never throws — a failed sync is reported via {@link Result#success()}, not an exception. */
    Result execute();
}
