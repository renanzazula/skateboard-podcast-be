package com.skateboard.podcast.application.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class PodcastTitlePatternTest {

    @ParameterizedTest
    @ValueSource(strings = {
            "TOM YUKIO - Skateboard Podcast #124",
            "DANILO CEREZINI - Skateboard Podcast #114",
            "FERNANDO GRANJA - Skateboard Podcast #111",
            "Skateboard Podcast #1",
            "skateboard podcast # 42",
    })
    void matchesTitlesEndingInANumberedEpisode(String title) {
        assertThat(PodcastTitlePattern.isNumberedEpisode(title)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "TOM YUKIO - Interview",
            "Best Skateboarding Moments",
            "Skateboard Podcast Special",
            "Skateboard Podcast",
            "Skateboard Podcast Episode 124",
    })
    void rejectsTitlesThatAreNotNumberedEpisodes(String title) {
        assertThat(PodcastTitlePattern.isNumberedEpisode(title)).isFalse();
    }

    @Test
    void rejectsNull() {
        assertThat(PodcastTitlePattern.isNumberedEpisode(null)).isFalse();
    }
}
