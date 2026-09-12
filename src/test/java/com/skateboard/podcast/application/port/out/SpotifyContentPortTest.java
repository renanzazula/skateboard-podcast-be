package com.skateboard.podcast.application.port.out;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

import com.skateboard.podcast.application.port.out.SpotifyContentPort.SpotifySyncException;

class SpotifyContentPortTest {

    @Test
    void syncExceptionCarriesMessageAndCause() {
        Throwable cause = new IllegalStateException("upstream failure");

        SpotifySyncException exception = new SpotifySyncException("spotify sync failed", cause);

        assertThat(exception.getMessage()).isEqualTo("spotify sync failed");
        assertThat(exception.getCause()).isSameAs(cause);
    }

    @Test
    void syncExceptionCarriesMessageOnly() {
        SpotifySyncException exception = new SpotifySyncException("spotify sync failed");

        assertThat(exception.getMessage()).isEqualTo("spotify sync failed");
        assertThat(exception.getCause()).isNull();
    }
}
