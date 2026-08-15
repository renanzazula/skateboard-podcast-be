package com.skateboard.podcast.adapter.out.youtube;

import com.skateboard.podcast.application.port.out.YoutubeContentPort;
import com.skateboard.podcast.infrastructure.youtube.YoutubeProperties;
import io.netty.channel.ChannelOption;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.util.UriBuilder;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Blocking WebClient adapter over the YouTube Data API v3 — same
 * "classic MVC app, WebClient only for outbound calls" shape as
 * skateboard-ui-backend's PodcastApiConfig. The API key never appears in
 * anything logged: on failure we build our own message from the response
 * status only, never from the request URI or the raw WebClient exception
 * (which can otherwise embed the full request URI, key included).
 */
@Component
public class YoutubeClient implements YoutubeContentPort {

    private static final Logger log = LoggerFactory.getLogger(YoutubeClient.class);
    private static final List<String> THUMBNAIL_PREFERENCE = List.of("maxres", "high", "medium", "standard", "default");

    private final WebClient webClient;
    private final YoutubeProperties properties;

    public YoutubeClient(WebClient.Builder webClientBuilder, YoutubeProperties properties) {
        this.properties = properties;
        YoutubeProperties.Api api = properties.getApi();
        HttpClient httpClient = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, api.getConnectTimeoutMs())
                .responseTimeout(Duration.ofMillis(api.getReadTimeoutMs()));
        this.webClient = webClientBuilder
                .baseUrl(api.getBaseUrl())
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .build();
    }

    @Override
    public YoutubeChannel resolveChannel(String channelId) {
        YoutubeChannelListResponse response = get("/channels", uri -> uri
                .queryParam("part", "contentDetails,snippet")
                .queryParam("id", channelId), YoutubeChannelListResponse.class);

        if (response == null || response.items() == null || response.items().isEmpty()) {
            throw new YoutubeSyncException("YouTube channel not found: " + channelId);
        }
        YoutubeChannelListResponse.Item item = response.items().get(0);
        if (item.contentDetails() == null || item.contentDetails().relatedPlaylists() == null) {
            throw new YoutubeSyncException("YouTube channel has no uploads playlist: " + channelId);
        }
        return new YoutubeChannel(item.id(), item.snippet() != null ? item.snippet().title() : null,
                item.contentDetails().relatedPlaylists().uploads());
    }

    @Override
    public List<YoutubeVideo> getLatestVideos(String uploadsPlaylistId, int limit) {
        return fetchPlaylistItems(uploadsPlaylistId, limit);
    }

    @Override
    public List<YoutubeVideo> getAllPlaylistItems(String playlistId) {
        return fetchPlaylistItems(playlistId, null);
    }

    /** {@code limit == null} paginates until every item is fetched (README §9). */
    private List<YoutubeVideo> fetchPlaylistItems(String playlistId, Integer limit) {
        List<YoutubeVideo> videos = new ArrayList<>();
        String pageToken = null;
        do {
            int pageSize = limit != null ? Math.min(50, limit - videos.size()) : 50;
            String token = pageToken;
            YoutubePlaylistItemsResponse response = get("/playlistItems", uri -> {
                uri = uri.queryParam("part", "snippet,contentDetails")
                        .queryParam("playlistId", playlistId)
                        .queryParam("maxResults", pageSize);
                return token != null ? uri.queryParam("pageToken", token) : uri;
            }, YoutubePlaylistItemsResponse.class);

            if (response == null || response.items() == null) break;
            for (YoutubePlaylistItemsResponse.Item item : response.items()) {
                if (item.contentDetails() == null || item.snippet() == null) continue;
                videos.add(new YoutubeVideo(
                        item.contentDetails().videoId(),
                        item.snippet().title(),
                        item.snippet().description(),
                        parsePublishedAt(item.snippet().publishedAt()),
                        bestThumbnail(item.snippet().thumbnails())));
                if (limit != null && videos.size() >= limit) break;
            }
            pageToken = response.nextPageToken();
        } while (pageToken != null && (limit == null || videos.size() < limit));
        return videos;
    }

    @Override
    public List<YoutubePlaylist> getPlaylists(String channelId) {
        List<YoutubePlaylist> playlists = new ArrayList<>();
        String pageToken = null;
        do {
            String token = pageToken;
            YoutubePlaylistListResponse response = get("/playlists", uri -> {
                uri = uri.queryParam("part", "snippet")
                        .queryParam("channelId", channelId)
                        .queryParam("maxResults", 50);
                return token != null ? uri.queryParam("pageToken", token) : uri;
            }, YoutubePlaylistListResponse.class);

            if (response == null || response.items() == null) break;
            for (YoutubePlaylistListResponse.Item item : response.items()) {
                if (item.snippet() == null) continue;
                playlists.add(new YoutubePlaylist(item.id(), item.snippet().title(),
                        item.snippet().description(), bestThumbnail(item.snippet().thumbnails())));
            }
            pageToken = response.nextPageToken();
        } while (pageToken != null);
        return playlists;
    }

    @Override
    public List<YoutubeVideoDuration> getVideoDurations(List<String> videoIds) {
        List<YoutubeVideoDuration> durations = new ArrayList<>();
        for (List<String> chunk : partition(videoIds, 50)) {
            YoutubeVideoListResponse response = get("/videos", uri -> uri
                    .queryParam("part", "contentDetails")
                    .queryParam("id", String.join(",", chunk)), YoutubeVideoListResponse.class);
            if (response == null || response.items() == null) continue;
            for (YoutubeVideoListResponse.Item item : response.items()) {
                durations.add(new YoutubeVideoDuration(item.id(), parseDurationSeconds(item.contentDetails())));
            }
        }
        return durations;
    }

    private <T> T get(String path, Function<UriBuilder, UriBuilder> uriCustomizer, Class<T> responseType) {
        try {
            return webClient.get()
                    .uri(builder -> uriCustomizer.apply(builder.path(path))
                            .queryParam("key", properties.getApi().getKey())
                            .build())
                    .retrieve()
                    .bodyToMono(responseType)
                    .retryWhen(Retry.backoff(2, Duration.ofMillis(500))
                            .filter(YoutubeClient::isRetryable)
                            // Without this, Reactor wraps the final failure in
                            // RetryExhaustedException instead of the original
                            // WebClientResponseException/WebClientRequestException,
                            // which would bypass the catch clauses below.
                            .onRetryExhaustedThrow((spec, signal) -> signal.failure()))
                    .block();
        } catch (WebClientResponseException ex) {
            // Deliberately not chaining `ex` as the cause: WebClientResponseException
            // can embed the full request URI (including ?key=...) in its message.
            throw new YoutubeSyncException("YouTube API request to " + path + " failed with status " + ex.getStatusCode().value());
        } catch (WebClientRequestException ex) {
            throw new YoutubeSyncException("YouTube API request to " + path + " failed: connection/timeout error");
        }
    }

    private static boolean isRetryable(Throwable ex) {
        if (ex instanceof WebClientResponseException wcre) {
            return wcre.getStatusCode().value() == 429 || wcre.getStatusCode().is5xxServerError();
        }
        return ex instanceof WebClientRequestException;
    }

    private static Instant parsePublishedAt(String publishedAt) {
        return publishedAt != null ? Instant.parse(publishedAt) : null;
    }

    private static Integer parseDurationSeconds(YoutubeVideoListResponse.ContentDetails contentDetails) {
        if (contentDetails == null || contentDetails.duration() == null) return null;
        try {
            return (int) Duration.parse(contentDetails.duration()).getSeconds();
        } catch (Exception e) {
            log.warn("Unparseable YouTube duration format, leaving null");
            return null;
        }
    }

    private static String bestThumbnail(Map<String, YoutubePlaylistItemsResponse.Thumbnail> thumbnails) {
        if (thumbnails == null) return null;
        for (String quality : THUMBNAIL_PREFERENCE) {
            YoutubePlaylistItemsResponse.Thumbnail thumbnail = thumbnails.get(quality);
            if (thumbnail != null && thumbnail.url() != null) return thumbnail.url();
        }
        return null;
    }

    private static <T> List<List<T>> partition(List<T> items, int size) {
        List<List<T>> chunks = new ArrayList<>();
        for (int i = 0; i < items.size(); i += size) {
            chunks.add(items.subList(i, Math.min(items.size(), i + size)));
        }
        return chunks;
    }
}
