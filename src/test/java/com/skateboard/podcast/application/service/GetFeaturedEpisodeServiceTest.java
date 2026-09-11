package com.skateboard.podcast.application.service;

import com.skateboard.podcast.application.port.out.LoadPostPort;
import com.skateboard.podcast.domain.model.Post;
import com.skateboard.podcast.domain.model.PostStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Which post the Home Featured Player's AUTO selection mode should resolve
 * to: the latest of the bounded candidate pool whose title matches the
 * show's official numbering convention. The pool itself is expected to
 * already be published/YouTube-only/newest-first — that filtering is
 * {@link LoadPostPort#findLatestPublishedYoutubePosts} job, verified by
 * persistence-layer tests, not this one.
 */
class GetFeaturedEpisodeServiceTest {

    private static final int SCAN_LIMIT = 50;

    @Mock private LoadPostPort loadPostPort;

    private GetFeaturedEpisodeService service;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        service = new GetFeaturedEpisodeService(loadPostPort, SCAN_LIMIT);
    }

    @Test
    void picksTheLatestPostMatchingTheOfficialEpisodePattern() {
        // The exact scenario from the spec: a hashtag-only mention must not
        // qualify, while a properly titled episode further down the (still
        // newest-first) list does.
        Post short_ = post("Tom Yukio 12 é muita bagagem já 🎙️📡 #skateboardpodcast #obrigadoskateboard #skateboarding #podcast");
        Post episode = post("TOM YUKIO - Skateboard Podcast #124");
        when(loadPostPort.findLatestPublishedYoutubePosts(SCAN_LIMIT)).thenReturn(List.of(short_, episode));

        Optional<Post> result = service.execute();

        assertThat(result).contains(episode);
    }

    @Test
    void returnsTheFirstMatchWhenCandidatesAreAlreadyNewestFirst() {
        Post older = post("Skateboard Podcast #100");
        Post newer = post("Skateboard Podcast #101");
        when(loadPostPort.findLatestPublishedYoutubePosts(SCAN_LIMIT)).thenReturn(List.of(newer, older));

        Optional<Post> result = service.execute();

        assertThat(result).contains(newer);
    }

    @Test
    void returnsEmptyWhenNoCandidateMatches() {
        Post interview = post("TOM YUKIO - Interview");
        Post special = post("Skateboard Podcast Special");
        when(loadPostPort.findLatestPublishedYoutubePosts(SCAN_LIMIT)).thenReturn(List.of(interview, special));

        assertThat(service.execute()).isEmpty();
    }

    @Test
    void returnsEmptyWhenThereAreNoCandidatesAtAll() {
        when(loadPostPort.findLatestPublishedYoutubePosts(SCAN_LIMIT)).thenReturn(List.of());

        assertThat(service.execute()).isEmpty();
    }

    @Test
    void scansWithTheConfiguredLimit() {
        when(loadPostPort.findLatestPublishedYoutubePosts(SCAN_LIMIT)).thenReturn(List.of());

        service.execute();

        verify(loadPostPort).findLatestPublishedYoutubePosts(SCAN_LIMIT);
    }

    private Post post(String title) {
        String slug = title.toLowerCase().replaceAll("[^a-z0-9]+", "-").replaceAll("^-|-$", "");
        return Post.create(title, slug, PostStatus.PUBLISHED, Instant.now(), "cover.jpg", "[]", "[]", null);
    }
}
