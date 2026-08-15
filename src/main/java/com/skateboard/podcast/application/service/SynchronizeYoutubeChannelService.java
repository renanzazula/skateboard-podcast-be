package com.skateboard.podcast.application.service;

import com.skateboard.podcast.adapter.in.rest.PodcastService;
import com.skateboard.podcast.application.port.in.CreatePostUseCase;
import com.skateboard.podcast.application.port.in.SynchronizeYoutubeChannelUseCase;
import com.skateboard.podcast.application.port.out.CategoryRepositoryPort;
import com.skateboard.podcast.application.port.out.LoadPostPort;
import com.skateboard.podcast.application.port.out.PostCategoryPort;
import com.skateboard.podcast.application.port.out.YoutubeContentPort;
import com.skateboard.podcast.domain.model.Category;
import com.skateboard.podcast.domain.model.Post;
import com.skateboard.podcast.domain.model.PostStatus;
import com.skateboard.podcast.infrastructure.youtube.YoutubeProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Polls the configured YouTube channel and:
 * <ol>
 *   <li>mirrors every public playlist as a {@code Category} (external id = playlist id);</li>
 *   <li>syncs each playlist's items into {@code Post}s (dedup via {@code youtube_video_id})
 *       and diffs their {@code post_category} associations against the playlist's current
 *       membership;</li>
 *   <li>disables categories whose playlist disappeared;</li>
 *   <li>still imports channel uploads that aren't (yet) in any playlist, uncategorized,
 *       via the pre-existing bounded incremental poll.</li>
 * </ol>
 * Never throws: a failure in one playlist, or in the uploads catch-all, must not prevent
 * the others from being processed or the scheduler from trying again next cycle.
 */
@Service
public class SynchronizeYoutubeChannelService implements SynchronizeYoutubeChannelUseCase {

    private static final Logger log = LoggerFactory.getLogger(SynchronizeYoutubeChannelService.class);
    private static final String SOURCE_YOUTUBE = "YOUTUBE";
    // Same convention FE's episodeMeta.ts uses today for the show's own episode numbering.
    private static final Pattern EPISODE_NUMBER = Pattern.compile("#(\\d+)\\s*$");

    private final YoutubeContentPort youtubeContentPort;
    private final LoadPostPort loadPostPort;
    private final CreatePostUseCase createPostUseCase;
    private final CategoryRepositoryPort categoryRepositoryPort;
    private final PostCategoryPort postCategoryPort;
    private final YoutubeProperties properties;

    private volatile String cachedUploadsPlaylistId;

    public SynchronizeYoutubeChannelService(YoutubeContentPort youtubeContentPort, LoadPostPort loadPostPort,
                                            CreatePostUseCase createPostUseCase,
                                            CategoryRepositoryPort categoryRepositoryPort,
                                            PostCategoryPort postCategoryPort,
                                            YoutubeProperties properties) {
        this.youtubeContentPort = youtubeContentPort;
        this.loadPostPort = loadPostPort;
        this.createPostUseCase = createPostUseCase;
        this.categoryRepositoryPort = categoryRepositoryPort;
        this.postCategoryPort = postCategoryPort;
        this.properties = properties;
    }

    @Override
    @CacheEvict(cacheNames = PodcastService.POST_CACHE, allEntries = true,
            condition = "#result.created() > 0 || #result.categoryChanges() > 0")
    public Result execute() {
        String channelId = properties.getChannelId();
        if (channelId == null || channelId.isBlank()) {
            log.warn("youtubeSync skipped: no youtube.channel-id configured");
            return new Result(0, 0, 0, 0, false);
        }

        String uploadsPlaylistId;
        try {
            uploadsPlaylistId = resolveUploadsPlaylistId(channelId);
        } catch (Exception e) {
            cachedUploadsPlaylistId = null;
            log.warn("youtubeSync channelId={} status=FAILURE reason={}", channelId, e.getMessage());
            return new Result(0, 0, 0, 0, false);
        }

        int created = 0;
        int categoryChanges = 0;
        try {
            CategorySyncOutcome outcome = syncPlaylistCategories(channelId);
            created += outcome.created();
            categoryChanges += outcome.categoryChanges();
        } catch (Exception e) {
            log.warn("youtubeSync channelId={} status=PLAYLISTS_FAILED reason={}", channelId, e.getMessage());
        }

        int received = 0;
        int existing = 0;
        try {
            UploadsSyncOutcome outcome = syncUploadsCatchAll(uploadsPlaylistId);
            received = outcome.received();
            created += outcome.created();
            existing = outcome.existing();
        } catch (Exception e) {
            log.warn("youtubeSync channelId={} status=UPLOADS_FAILED reason={}", channelId, e.getMessage());
        }

        log.info("youtubeSync channelId={} status=SUCCESS received={} created={} existing={} categoryChanges={}",
                channelId, received, created, existing, categoryChanges);
        return new Result(received, created, existing, categoryChanges, true);
    }

    // ── Playlists -> categories -> post associations ────────────────────────

    private record CategorySyncOutcome(int created, int categoryChanges) {}

    private CategorySyncOutcome syncPlaylistCategories(String channelId) {
        List<YoutubeContentPort.YoutubePlaylist> playlists = youtubeContentPort.getPlaylists(channelId);
        Set<String> activeExternalIds = new HashSet<>();

        int created = 0;
        int categoryChanges = 0;
        for (YoutubeContentPort.YoutubePlaylist playlist : playlists) {
            activeExternalIds.add(playlist.playlistId());
            try {
                Category category = upsertCategory(playlist);
                PlaylistPostSyncOutcome outcome = syncPlaylistPosts(category, playlist.playlistId());
                created += outcome.created();
                categoryChanges += outcome.categoryChanges();
            } catch (Exception e) {
                log.warn("youtubeSync playlistId={} status=SKIPPED reason={}", playlist.playlistId(), e.getMessage());
            }
        }

        for (Category category : categoryRepositoryPort.findAll()) {
            if (category.isEnabled() && !activeExternalIds.contains(category.getExternalId())) {
                category.disable();
                categoryRepositoryPort.save(category);
                log.info("youtubeSync categoryId={} status=DISABLED reason=playlist_missing", category.getId());
            }
        }
        return new CategorySyncOutcome(created, categoryChanges);
    }

    private Category upsertCategory(YoutubeContentPort.YoutubePlaylist playlist) {
        boolean isDefault = playlist.playlistId().equals(properties.getDefaultPlaylistId());
        Optional<Category> existing = categoryRepositoryPort.findByExternalId(SOURCE_YOUTUBE, playlist.playlistId());
        if (existing.isPresent()) {
            Category category = existing.get();
            category.updateFromYoutube(playlist.title(), playlist.description(), playlist.thumbnailUrl(), isDefault);
            log.info("youtubeSync categoryId={} externalId={} status=UPDATED", category.getId(), playlist.playlistId());
            return categoryRepositoryPort.save(category);
        }
        String slug = ensureUniqueCategorySlug(generateSlug(playlist.title()));
        Category category = Category.createFromYoutube(slug, playlist.playlistId(), playlist.title(),
                playlist.description(), playlist.thumbnailUrl(), isDefault);
        category = categoryRepositoryPort.save(category);
        log.info("youtubeSync categoryId={} externalId={} status=CREATED", category.getId(), playlist.playlistId());
        return category;
    }

    private record PlaylistPostSyncOutcome(int created, int categoryChanges) {}

    private PlaylistPostSyncOutcome syncPlaylistPosts(Category category, String playlistId) {
        List<YoutubeContentPort.YoutubeVideo> items = youtubeContentPort.getAllPlaylistItems(playlistId);
        Map<String, YoutubeContentPort.YoutubeVideo> byVideoId = new HashMap<>();
        for (YoutubeContentPort.YoutubeVideo video : items) {
            byVideoId.put(video.videoId(), video);
        }

        Set<String> currentVideoIds = byVideoId.keySet();
        Set<String> existingVideoIds = postCategoryPort.findVideoIdsByCategory(category.getId());

        Set<String> toAdd = new HashSet<>(currentVideoIds);
        toAdd.removeAll(existingVideoIds);
        Set<String> toRemove = new HashSet<>(existingVideoIds);
        toRemove.removeAll(currentVideoIds);

        // One lookup per candidate video, reused for both the duration-fetch filter and creation.
        Map<String, Post> existingPosts = new HashMap<>();
        for (String videoId : toAdd) {
            loadPostPort.findByYoutubeVideoId(videoId).ifPresent(post -> existingPosts.put(videoId, post));
        }
        List<YoutubeContentPort.YoutubeVideo> newVideos = toAdd.stream()
                .filter(videoId -> !existingPosts.containsKey(videoId))
                .map(byVideoId::get)
                .toList();
        Map<String, Integer> durations = fetchDurations(newVideos);

        int created = 0;
        for (String videoId : toAdd) {
            try {
                Post post = existingPosts.get(videoId);
                if (post == null) {
                    post = createPost(byVideoId.get(videoId), durations.get(videoId));
                    created++;
                }
                postCategoryPort.addAssociation(post.getId(), category.getId());
            } catch (Exception e) {
                log.warn("youtubeSync playlistId={} videoId={} status=SKIPPED reason={}", playlistId, videoId, e.getMessage());
            }
        }
        for (String videoId : toRemove) {
            loadPostPort.findByYoutubeVideoId(videoId)
                    .ifPresent(post -> postCategoryPort.removeAssociation(post.getId(), category.getId()));
        }

        log.info("youtubeSync playlistId={} categoryId={} posts={} associationsCreated={} associationsRemoved={}",
                playlistId, category.getId(), items.size(), toAdd.size(), toRemove.size());
        return new PlaylistPostSyncOutcome(created, toAdd.size() + toRemove.size());
    }

    // ── Uploads catch-all (existing behavior, unchanged) ────────────────────

    private record UploadsSyncOutcome(int received, int created, int existing) {}

    private UploadsSyncOutcome syncUploadsCatchAll(String uploadsPlaylistId) {
        List<YoutubeContentPort.YoutubeVideo> latest =
                youtubeContentPort.getLatestVideos(uploadsPlaylistId, properties.getSync().getInitialImportLimit());
        List<YoutubeContentPort.YoutubeVideo> unseen = latest.stream()
                .filter(v -> loadPostPort.findByYoutubeVideoId(v.videoId()).isEmpty())
                .toList();
        Map<String, Integer> durations = fetchDurations(unseen);

        int created = 0;
        for (YoutubeContentPort.YoutubeVideo video : unseen) {
            try {
                createPost(video, durations.get(video.videoId()));
                created++;
            } catch (Exception e) {
                log.warn("youtubeSync videoId={} status=SKIPPED reason={}", video.videoId(), e.getMessage());
            }
        }
        return new UploadsSyncOutcome(latest.size(), created, latest.size() - unseen.size());
    }

    // ── Shared helpers ───────────────────────────────────────────────────────

    private String resolveUploadsPlaylistId(String channelId) {
        String cached = cachedUploadsPlaylistId;
        if (cached != null) return cached;
        YoutubeContentPort.YoutubeChannel channel = youtubeContentPort.resolveChannel(channelId);
        cachedUploadsPlaylistId = channel.uploadsPlaylistId();
        return cachedUploadsPlaylistId;
    }

    private Map<String, Integer> fetchDurations(List<YoutubeContentPort.YoutubeVideo> videos) {
        if (videos.isEmpty()) return Map.of();
        Map<String, Integer> durations = new HashMap<>();
        try {
            List<String> ids = videos.stream().map(YoutubeContentPort.YoutubeVideo::videoId).toList();
            for (YoutubeContentPort.YoutubeVideoDuration d : youtubeContentPort.getVideoDurations(ids)) {
                durations.put(d.videoId(), d.durationSeconds());
            }
        } catch (Exception e) {
            log.warn("youtubeSync duration lookup failed, proceeding without durations: {}", e.getMessage());
        }
        return durations;
    }

    private Post createPost(YoutubeContentPort.YoutubeVideo video, Integer durationSeconds) {
        String slug = generateSlug(video.title());
        return createPostUseCase.execute(new CreatePostUseCase.Input(
                video.title(), slug, PostStatus.PUBLISHED, video.publishedAt(),
                video.thumbnailUrl(), "[]", null, null,
                video.videoId(), video.description(), durationSeconds, parseEpisodeNumber(video.title())));
    }

    private Integer parseEpisodeNumber(String title) {
        if (title == null) return null;
        Matcher matcher = EPISODE_NUMBER.matcher(title.trim());
        return matcher.find() ? Integer.parseInt(matcher.group(1)) : null;
    }

    private String ensureUniqueCategorySlug(String base) {
        String slug = base;
        int counter = 1;
        while (categoryRepositoryPort.findBySlug(slug).isPresent()) {
            slug = base + "-" + counter++;
        }
        return slug;
    }

    // Duplicated from PodcastService/ImportPostsService by existing project convention
    // (see CLAUDE.md) — keep all in sync if the slugging rule changes.
    private String generateSlug(String title) {
        return title.toLowerCase()
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-|-$", "");
    }
}
