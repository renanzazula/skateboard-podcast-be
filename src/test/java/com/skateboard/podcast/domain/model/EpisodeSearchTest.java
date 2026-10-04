package com.skateboard.podcast.domain.model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EpisodeSearchTest {

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "\t"})
    void blankInputMeansNoFilter(String raw) {
        assertThat(EpisodeSearch.parse(raw)).isNull();
    }

    @Test
    void textIsTrimmedAndLowerCased() {
        assertThat(EpisodeSearch.parse("  Skateboard Podcast "))
                .isEqualTo(new EpisodeSearch("skateboard podcast", null));
    }

    @ParameterizedTest
    @CsvSource({
            "42, 42",
            "#42, 42",
            "# 42, 42",
            "ep 42, 42",
            "EP.42, 42",
            "Episode 42, 42",
            "episode #7, 7",
            "007, 7"
    })
    void inputThatReadsAsAnEpisodeNumberCarriesIt(String raw, int expected) {
        assertThat(EpisodeSearch.parse(raw).episodeNumber()).isEqualTo(expected);
    }

    @ParameterizedTest
    @ValueSource(strings = {"Skateboard Podcast #42", "42 tricks", "4 2", "-42", "1234567890"})
    void inputThatIsNotOnlyAnEpisodeNumberIsTextOnly(String raw) {
        assertThat(EpisodeSearch.parse(raw).episodeNumber()).isNull();
    }

    @Test
    void rejectsInputOverTheMaximumLength() {
        assertThat(EpisodeSearch.parse("a".repeat(EpisodeSearch.MAX_LENGTH))).isNotNull();
        assertThatThrownBy(() -> EpisodeSearch.parse("a".repeat(EpisodeSearch.MAX_LENGTH + 1)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
