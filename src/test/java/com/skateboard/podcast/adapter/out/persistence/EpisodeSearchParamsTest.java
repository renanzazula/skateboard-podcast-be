package com.skateboard.podcast.adapter.out.persistence;

import com.skateboard.podcast.domain.model.EpisodeSearch;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class EpisodeSearchParamsTest {

    @Test
    void wrapsTextInWildcards() {
        assertThat(EpisodeSearchParams.likePattern(EpisodeSearch.parse("Skate"))).isEqualTo("%skate%");
    }

    @Test
    void escapesLikeWildcardsAndTheEscapeCharacterItself() {
        assertThat(EpisodeSearchParams.likePattern(EpisodeSearch.parse("100%_a\\b")))
                .isEqualTo("%100\\%\\_a\\\\b%");
    }

    @Test
    void bindsTheEpisodeNumberOrTheNeverMatchingSentinel() {
        assertThat(EpisodeSearchParams.episodeNumber(EpisodeSearch.parse("ep 42"))).isEqualTo(42);
        assertThat(EpisodeSearchParams.episodeNumber(EpisodeSearch.parse("skate")))
                .isEqualTo(EpisodeSearchParams.NO_EPISODE);
    }
}
