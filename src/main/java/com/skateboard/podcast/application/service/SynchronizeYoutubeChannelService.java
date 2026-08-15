package com.skateboard.podcast.application.service;

import com.skateboard.podcast.adapter.in.rest.PodcastService;
import com.skateboard.podcast.application.port.in.CreatePostUseCase;
import com.skateboard.podcast.application.port.in.SynchronizeYoutubeChannelUseCase;
import com.skateboard.podcast.application.port.out.LoadPostPort;
import com.skateboard.podcast.application.port.out.YoutubeContentPort;
import com.skateboard.podcast.domain.model.PostStatus;
import com.skateboard.podcast.infrastructure.youtube.YoutubeProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Polls the configured YouTube channel and creates a {@code Post} for each
 * upload not already persisted (dedup via the {@code youtube_video_id}
 * unique column, checked through {@link LoadPostPort#findByYoutubeVideoId}).
 * Never throws: a failure here must not prevent the scheduler from trying
 * again next cycle, and must never touch already-persisted posts.
 */
@Service
public class SynchronizeYoutubeChannelService implements SynchronizeYoutubeChannelUseCase {

    private static final Logger log = LoggerFactory.getLogger(SynchronizeYoutubeChannelService.class);
    // Same convention FE's episodeMeta.ts uses today for the show's own episode numbering.
    private static final Pattern EPISODE_NUMBER = Pattern.compile("#(\\d+)\\s*$");

    private final YoutubeContentPort youtubeContentPort;
    private final LoadPostPort loadPostPort;
    private final CreatePostUseCase createPostUseCase;
    private final YoutubeProperties properties;

    private volatile String cachedUploadsPlaylistId;

    public SynchronizeYoutubeChannelService(YoutubeContentPort youtubeContentPort, LoadPostPort loadPostPort,
                                            CreatePostUseCase createPostUseCase, YoutubeProperties properties) {
        this.youtubeContentPort = youtubeContentPort;
        this.loadPostPort = loadPostPort;
        this.createPostUseCase = createPostUseCase;
        this.properties = properties;
    }

    // Bypassing PodcastService (the only other caller of CreatePostUseCase)
    // means this path would otherwise never evict podcast-post — the feed/
    // slug cache would keep serving pre-sync results until the 24h TTL
    // expires. Only evict when something actually changed.
    @Override
    @CacheEvict(cacheNames = PodcastService.POST_CACHE, allEntries = true, condition = "#result.created() > 0")
    public Result execute() {
        String channelId = properties.getChannelId();
        if (channelId == null || channelId.isBlank()) {
            log.warn("youtubeSync skipped: no youtube.channel-id configured");
            return new Result(0, 0, 0, false);
        }

        List<YoutubeContentPort.YoutubeVideo> latest;
        try {
            String uploadsPlaylistId = resolveUploadsPlaylistId(channelId);
            latest = youtubeContentPort.getLatestVideos(uploadsPlaylistId, properties.getSync().getInitialImportLimit());
        } catch (Exception e) {
            cachedUploadsPlaylistId = null;
            log.warn("youtubeSync channelId={} status=FAILURE reason={}", channelId, e.getMessage());
            return new Result(0, 0, 0, false);
        }

        List<YoutubeContentPort.YoutubeVideo> unseen = latest.stream()
                .filter(v -> loadPostPort.findByYoutubeVideoId(v.videoId()).isEmpty())
                .toList();

        Map<String, Integer> durationsByVideoId = fetchDurations(unseen);

        int created = 0;
        for (YoutubeContentPort.YoutubeVideo video : unseen) {
            try {
                createPost(video, durationsByVideoId.get(video.videoId()));
                created++;
            } catch (Exception e) {
                log.warn("youtubeSync channelId={} videoId={} status=SKIPPED reason={}",
                        channelId, video.videoId(), e.getMessage());
            }
        }

        int existing = latest.size() - unseen.size();
        log.info("youtubeSync channelId={} status=SUCCESS received={} created={} existing={}",
                channelId, latest.size(), created, existing);
        return new Result(latest.size(), created, existing, true);
    }

    private String resolveUploadsPlaylistId(String channelId) {
        String cached = cachedUploadsPlaylistId;
        if (cached != null) return cached;
        YoutubeContentPort.YoutubeChannel channel = youtubeContentPort.resolveChannel(channelId);
        cachedUploadsPlaylistId = channel.uploadsPlaylistId();
        return cachedUploadsPlaylistId;
    }

    private Map<String, Integer> fetchDurations(List<YoutubeContentPort.YoutubeVideo> unseen) {
        if (unseen.isEmpty()) return Map.of();
        Map<String, Integer> durations = new HashMap<>();
        try {
            List<String> ids = unseen.stream().map(YoutubeContentPort.YoutubeVideo::videoId).toList();
            for (YoutubeContentPort.YoutubeVideoDuration d : youtubeContentPort.getVideoDurations(ids)) {
                durations.put(d.videoId(), d.durationSeconds());
            }
        } catch (Exception e) {
            log.warn("youtubeSync duration lookup failed, proceeding without durations: {}", e.getMessage());
        }
        return durations;
    }

    private void createPost(YoutubeContentPort.YoutubeVideo video, Integer durationSeconds) {
        String slug = generateSlug(video.title());
        createPostUseCase.execute(new CreatePostUseCase.Input(
                video.title(), slug, PostStatus.PUBLISHED, video.publishedAt(),
                video.thumbnailUrl(), "[]", null, null,
                video.videoId(), video.description(), durationSeconds, parseEpisodeNumber(video.title())));
    }

    private Integer parseEpisodeNumber(String title) {
        if (title == null) return null;
        Matcher matcher = EPISODE_NUMBER.matcher(title.trim());
        return matcher.find() ? Integer.parseInt(matcher.group(1)) : null;
    }

    // Duplicated from PodcastService/ImportPostsService by existing project convention
    // (see CLAUDE.md) — keep all three in sync if the slugging rule changes.
    private String generateSlug(String title) {
        return title.toLowerCase()
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-|-$", "");
    }
}
