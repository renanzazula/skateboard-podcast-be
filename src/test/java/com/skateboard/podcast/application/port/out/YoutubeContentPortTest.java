package com.skateboard.podcast.application.port.out;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

import com.skateboard.podcast.application.port.out.YoutubeContentPort.YoutubeSyncException;
import com.skateboard.podcast.application.port.out.YoutubeContentPort.YoutubeVideo;

import java.time.Instant;

class YoutubeContentPortTest {

    @Test
    void syncExceptionCarriesMessageAndCause() {
        Throwable cause = new IllegalStateException("upstream failure");

        YoutubeSyncException exception = new YoutubeSyncException("youtube sync failed", cause);

        assertThat(exception.getMessage()).isEqualTo("youtube sync failed");
        assertThat(exception.getCause()).isSameAs(cause);
    }

    @Test
    void syncExceptionCarriesMessageOnly() {
        YoutubeSyncException exception = new YoutubeSyncException("youtube sync failed");

        assertThat(exception.getMessage()).isEqualTo("youtube sync failed");
        assertThat(exception.getCause()).isNull();
    }

    @Test
    void videoConvenienceConstructorDefaultsThumbnailDimensionsToNull() {
        Instant publishedAt = Instant.parse("2026-01-01T00:00:00Z");

        YoutubeVideo video = new YoutubeVideo("v1", "title", "description", publishedAt, "https://thumb");

        assertThat(video.thumbnailWidth()).isNull();
        assertThat(video.thumbnailHeight()).isNull();
        assertThat(video.videoId()).isEqualTo("v1");
        assertThat(video.thumbnailUrl()).isEqualTo("https://thumb");
    }
}
