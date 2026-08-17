package com.skateboard.podcast.application.service;

import com.skateboard.podcast.application.port.out.LoadPostPort;
import com.skateboard.podcast.application.port.out.SavePostPort;
import com.skateboard.podcast.application.port.out.SpotifyContentPort;
import com.skateboard.podcast.application.port.out.SpotifyContentPort.SpotifyEpisode;
import com.skateboard.podcast.domain.model.Post;
import com.skateboard.podcast.domain.model.PostPlatform;
import com.skateboard.podcast.domain.model.PostStatus;
import com.skateboard.podcast.infrastructure.spotify.SpotifyProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class MatchSpotifyEpisodeServiceTest {

    @Mock private SpotifyContentPort spotifyContentPort;
    @Mock private LoadPostPort loadPostPort;
    @Mock private SavePostPort savePostPort;

    private SpotifyProperties properties;
    private MatchSpotifyEpisodeService service;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        properties = new SpotifyProperties();
        properties.setShowId("693VmIGutJaAlUztFYF8dl");
        service = new MatchSpotifyEpisodeService(spotifyContentPort, loadPostPort, savePostPort, properties);
        when(savePostPort.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    private Post youtubePost(String title, Integer episodeNumber, Instant publishAt, Integer durationSeconds) {
        Post post = Post.create(title, "slug-" + UUID.randomUUID(), PostStatus.PUBLISHED, publishAt,
                null, "[]", "[]", null);
        post.attachYoutubeMetadata("yt-" + UUID.randomUUID(), "desc", durationSeconds, episodeNumber);
        return post;
    }

    private SpotifyEpisode episode(String id, String title, Instant releaseDate, Integer durationSeconds) {
        return new SpotifyEpisode(id, title, "desc", releaseDate, durationSeconds,
                "https://open.spotify.com/episode/" + id, null);
    }

    @Test
    void episodeNumberAndTitleMatchAboveThreshold() {
        Instant publishAt = Instant.parse("2026-01-01T00:00:00Z");
        Post post = youtubePost("EP. 24 - Skateboarding in Barcelona | Skateboard Podcast", 24, publishAt, 3822);
        when(loadPostPort.findAll(0, 1)).thenReturn(List.of(post));
        when(loadPostPort.countAll()).thenReturn(1L);
        when(spotifyContentPort.getShowEpisodes("693VmIGutJaAlUztFYF8dl")).thenReturn(List.of(
                episode("spot-1", "EP 24 Skateboarding in Barcelona", publishAt, 3815)));

        MatchSpotifyEpisodeService.Result result = service.execute();

        assertThat(result.matched()).isEqualTo(1);
        assertThat(result.unmatched()).isEqualTo(0);
        assertThat(post.getPlatformLinks()).singleElement()
                .satisfies(link -> {
                    assertThat(link.platform()).isEqualTo(PostPlatform.SPOTIFY);
                    assertThat(link.externalId()).isEqualTo("spot-1");
                });
        verify(savePostPort).save(post);
    }

    @Test
    void differentEpisodeNumbersDoNotMatch() {
        Instant publishAt = Instant.parse("2026-01-01T00:00:00Z");
        Post post = youtubePost("Episode 24 - Skateboarding", 24, publishAt, 3600);
        when(loadPostPort.findAll(0, 1)).thenReturn(List.of(post));
        when(loadPostPort.countAll()).thenReturn(1L);
        when(spotifyContentPort.getShowEpisodes("693VmIGutJaAlUztFYF8dl")).thenReturn(List.of(
                episode("spot-2", "Episode 25 - Skateboarding", publishAt, 3600)));

        MatchSpotifyEpisodeService.Result result = service.execute();

        assertThat(result.matched()).isEqualTo(0);
        assertThat(result.unmatched()).isEqualTo(1);
        assertThat(post.getPlatformLinks()).isEmpty();
        verifyNoInteractions(savePostPort);
    }

    @Test
    void alreadyLinkedEpisodeIsSkippedIdempotently() {
        Instant publishAt = Instant.parse("2026-01-01T00:00:00Z");
        Post post = youtubePost("EP 24 Skateboarding", 24, publishAt, 3600);
        post.attachPlatformLink(new com.skateboard.podcast.domain.model.PostPlatformLink(
                PostPlatform.SPOTIFY, "spot-1", "https://open.spotify.com/episode/spot-1"));
        when(loadPostPort.findAll(0, 1)).thenReturn(List.of(post));
        when(loadPostPort.countAll()).thenReturn(1L);
        when(spotifyContentPort.getShowEpisodes("693VmIGutJaAlUztFYF8dl")).thenReturn(List.of(
                episode("spot-1", "EP 24 Skateboarding", publishAt, 3600)));

        MatchSpotifyEpisodeService.Result result = service.execute();

        assertThat(result.matched()).isEqualTo(0);
        assertThat(result.unmatched()).isEqualTo(0);
        verify(savePostPort, never()).save(any());
    }

    @Test
    void noShowIdConfiguredSkipsSync() {
        properties.setShowId(null);

        MatchSpotifyEpisodeService.Result result = service.execute();

        assertThat(result.matched()).isEqualTo(0);
        assertThat(result.unmatched()).isEqualTo(0);
        verifyNoInteractions(spotifyContentPort, loadPostPort, savePostPort);
    }

    @Test
    void titleNormalizationMatchesDespitePunctuationAndSuffix() {
        assertThat(TitleNormalizer.normalize("EP. 24 - Skateboarding in Barcelona | Skateboard Podcast"))
                .isEqualTo("ep 24 skateboarding in barcelona");
        assertThat(TitleNormalizer.normalize("EP 24 Skateboarding in Barcelona"))
                .isEqualTo("ep 24 skateboarding in barcelona");
    }

    @Test
    void episodeNumberParserExtractsTrailingHashAndLeadingEp() {
        assertThat(EpisodeNumberParser.parse("Skateboard Podcast #87")).isEqualTo(87);
        assertThat(EpisodeNumberParser.parse("EP 24 Skateboarding in Barcelona")).isEqualTo(24);
        assertThat(EpisodeNumberParser.parse("No number here")).isNull();
    }
}
