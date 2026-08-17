package com.skateboard.podcast.application.service;

import com.skateboard.podcast.application.port.out.LoadPostPort;
import com.skateboard.podcast.application.port.out.SavePostPort;
import com.skateboard.podcast.application.port.out.SpotifyContentPort;
import com.skateboard.podcast.application.port.out.SpotifyContentPort.SpotifyEpisode;
import com.skateboard.podcast.domain.model.Post;
import com.skateboard.podcast.domain.model.PostPlatform;
import com.skateboard.podcast.domain.model.PostPlatformLink;
import com.skateboard.podcast.infrastructure.spotify.SpotifyProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Enriches existing (YouTube-sourced) posts with a Spotify link, per
 * .docs/README_SPOTIFY_YOUTUBE_PODCAST_INTEGRATION.md §12-14. Deliberately
 * does not create posts for unmatched Spotify episodes — YouTube/categories
 * remain the single source of truth for what appears in the feed; an
 * unmatched episode is logged and retried on the next sync.
 */
@Service
public class MatchSpotifyEpisodeService {

    private static final Logger log = LoggerFactory.getLogger(MatchSpotifyEpisodeService.class);

    static final int SCORE_EPISODE_NUMBER = 50;
    static final int SCORE_TITLE = 30;
    static final int SCORE_PUBLISH_DATE = 15;
    static final int SCORE_DURATION = 5;
    static final int MATCH_THRESHOLD = 70;

    private static final Duration DATE_TOLERANCE = Duration.ofDays(2);
    private static final int DURATION_TOLERANCE_SECONDS = 30;

    private final SpotifyContentPort spotifyContentPort;
    private final LoadPostPort loadPostPort;
    private final SavePostPort savePostPort;
    private final SpotifyProperties properties;

    public MatchSpotifyEpisodeService(SpotifyContentPort spotifyContentPort, LoadPostPort loadPostPort,
                                      SavePostPort savePostPort, SpotifyProperties properties) {
        this.spotifyContentPort = spotifyContentPort;
        this.loadPostPort = loadPostPort;
        this.savePostPort = savePostPort;
        this.properties = properties;
    }

    public record Result(int matched, int unmatched) {}

    public Result execute() {
        String showId = properties.getShowId();
        if (showId == null || showId.isBlank()) {
            log.warn("spotifySync skipped: no spotify.show-id configured");
            return new Result(0, 0);
        }

        List<SpotifyEpisode> episodes = spotifyContentPort.getShowEpisodes(showId);
        List<Post> allPosts = loadPostPort.findAll(0, (int) Math.max(loadPostPort.countAll(), 1));
        Set<String> alreadyLinkedExternalIds = allPosts.stream()
                .flatMap(p -> p.getPlatformLinks().stream())
                .filter(link -> link.platform() == PostPlatform.SPOTIFY)
                .map(PostPlatformLink::externalId)
                .collect(Collectors.toSet());

        // Once a post is claimed by an episode in this run it's off the table
        // for the rest of the batch, so two close-together episodes can't
        // both land on the same post.
        Set<Post> claimed = new HashSet<>();
        int matched = 0;
        int unmatched = 0;
        for (SpotifyEpisode episode : episodes) {
            if (alreadyLinkedExternalIds.contains(episode.id())) {
                continue;
            }
            Post best = null;
            int bestScore = 0;
            for (Post candidate : allPosts) {
                if (claimed.contains(candidate) || hasSpotifyLink(candidate)) continue;
                int score = score(episode, candidate);
                if (score > bestScore) {
                    bestScore = score;
                    best = candidate;
                }
            }
            if (best != null && bestScore >= MATCH_THRESHOLD) {
                best.attachPlatformLink(new PostPlatformLink(PostPlatform.SPOTIFY, episode.id(), episode.externalUrl()));
                savePostPort.save(best);
                claimed.add(best);
                matched++;
                log.info("spotifySync episodeId={} postId={} score={} status=MATCHED", episode.id(), best.getId(), bestScore);
            } else {
                log.info("spotifySync episodeId={} title={} episodeNumber={} publishedAt={} bestScore={} status=UNMATCHED",
                        episode.id(), episode.title(), EpisodeNumberParser.parse(episode.title()), episode.releaseDate(), bestScore);
                unmatched++;
            }
        }
        log.info("spotifySync showId={} fetched={} matched={} unmatched={}", showId, episodes.size(), matched, unmatched);
        return new Result(matched, unmatched);
    }

    private boolean hasSpotifyLink(Post post) {
        return post.getPlatformLinks().stream().anyMatch(l -> l.platform() == PostPlatform.SPOTIFY);
    }

    int score(SpotifyEpisode episode, Post post) {
        int score = 0;
        Integer episodeNumber = EpisodeNumberParser.parse(episode.title());
        if (episodeNumber != null && episodeNumber.equals(post.getEpisodeNumber())) {
            score += SCORE_EPISODE_NUMBER;
        }
        if (!TitleNormalizer.normalize(episode.title()).isEmpty()
                && TitleNormalizer.normalize(episode.title()).equals(TitleNormalizer.normalize(post.getTitle()))) {
            score += SCORE_TITLE;
        }
        if (withinDateTolerance(episode.releaseDate(), effectivePublishDate(post))) {
            score += SCORE_PUBLISH_DATE;
        }
        if (withinDurationTolerance(episode.durationSeconds(), post.getDurationSeconds())) {
            score += SCORE_DURATION;
        }
        return score;
    }

    private Instant effectivePublishDate(Post post) {
        return post.getPublishAt() != null ? post.getPublishAt() : post.getCreatedAt();
    }

    private boolean withinDateTolerance(Instant a, Instant b) {
        if (a == null || b == null) return false;
        return Duration.between(a, b).abs().compareTo(DATE_TOLERANCE) <= 0;
    }

    private boolean withinDurationTolerance(Integer a, Integer b) {
        if (a == null || b == null) return false;
        return Math.abs(a - b) <= DURATION_TOLERANCE_SECONDS;
    }
}
