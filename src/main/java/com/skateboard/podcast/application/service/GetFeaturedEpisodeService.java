package com.skateboard.podcast.application.service;

import com.skateboard.podcast.application.port.in.GetFeaturedEpisodeUseCase;
import com.skateboard.podcast.application.port.out.LoadPostPort;
import com.skateboard.podcast.domain.model.Post;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * Resolves the latest published, YouTube-sourced post whose title matches the
 * show's official numbering convention ("Skateboard Podcast #&lt;n&gt;",
 * {@link PodcastTitlePattern}) — the candidate the Home Featured Player's
 * AUTO selection mode shows when an admin has opted out of picking a video by
 * hand (the mode itself lives in skateboard-app-config-be; the pick is
 * resolved live by skateboard-ui-backend against this use case's endpoint).
 *
 * <p>Shorts, interviews, highlight reels and other off-pattern uploads are
 * still synced and stored like any other post by {@code
 * SynchronizeYoutubeChannelService} — they're only skipped here, at read
 * time, same as MANUAL selection is unaffected by any of this.
 *
 * <p>The scan is bounded to the {@code candidateScanLimit} most recent
 * published YouTube posts rather than the whole table — at the channel's
 * actual upload cadence a real numbered episode is expected well within that
 * window, and this mirrors the bounded-scan style already used elsewhere
 * (e.g. {@code PendingPodcastNotificationJob}'s batch limit).
 */
@Service
public class GetFeaturedEpisodeService implements GetFeaturedEpisodeUseCase {

    private final LoadPostPort loadPostPort;
    private final int candidateScanLimit;

    public GetFeaturedEpisodeService(LoadPostPort loadPostPort,
                                      @Value("${podcast.featured.candidate-scan-limit:50}") int candidateScanLimit) {
        this.loadPostPort = loadPostPort;
        this.candidateScanLimit = candidateScanLimit;
    }

    @Override
    public Optional<Post> execute() {
        List<Post> candidates = loadPostPort.findLatestPublishedYoutubePosts(candidateScanLimit);
        // Candidates already arrive newest-first, so the first pattern match
        // is the latest qualifying episode.
        return candidates.stream()
                .filter(post -> PodcastTitlePattern.isNumberedEpisode(post.getTitle()))
                .findFirst();
    }
}
