package com.skateboard.podcast.adapter.out.spotify;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.skateboard.podcast.application.port.out.SpotifyContentPort;
import com.skateboard.podcast.infrastructure.spotify.SpotifyProperties;
import io.netty.channel.ChannelOption;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.util.UriBuilder;
import reactor.netty.http.client.HttpClient;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Blocking WebClient adapter over the Spotify Web API — same shape as
 * {@code YoutubeClient}. Never logs the bearer token or embeds it in an
 * exception message (see {@link SpotifyTokenClient}'s javadoc).
 */
@Component
public class SpotifyApiClient implements SpotifyContentPort {

    private static final int PAGE_SIZE = 50;

    private final WebClient webClient;
    private final SpotifyProperties properties;
    private final SpotifyTokenClient tokenClient;

    public SpotifyApiClient(WebClient.Builder webClientBuilder, SpotifyProperties properties,
                            SpotifyTokenClient tokenClient) {
        this.properties = properties;
        this.tokenClient = tokenClient;
        SpotifyProperties.Api api = properties.getApi();
        HttpClient httpClient = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, api.getConnectTimeoutMs())
                .responseTimeout(Duration.ofMillis(api.getReadTimeoutMs()));
        this.webClient = webClientBuilder
                .baseUrl(api.getBaseUrl())
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .build();
    }

    @Override
    public List<SpotifyEpisode> getShowEpisodes(String showId) {
        List<SpotifyEpisode> episodes = new ArrayList<>();
        int offset = 0;
        EpisodesPage page;
        do {
            int currentOffset = offset;
            page = get("/v1/shows/" + showId + "/episodes", uri -> {
                uri = uri.queryParam("limit", PAGE_SIZE).queryParam("offset", currentOffset);
                return properties.getMarket() != null && !properties.getMarket().isBlank()
                        ? uri.queryParam("market", properties.getMarket()) : uri;
            }, EpisodesPage.class);

            if (page == null || page.items() == null) break;
            for (EpisodeItem item : page.items()) {
                episodes.add(new SpotifyEpisode(item.id(), item.name(), item.description(),
                        parseReleaseDate(item.releaseDate()), parseDurationSeconds(item.durationMs()),
                        externalUrl(item), item.images() != null && !item.images().isEmpty()
                                ? item.images().get(0).url() : null));
            }
            offset += PAGE_SIZE;
        } while (page.next() != null);
        return episodes;
    }

    private <T> T get(String path, Function<UriBuilder, UriBuilder> uriCustomizer, Class<T> responseType) {
        try {
            return webClient.get()
                    .uri(builder -> uriCustomizer.apply(builder.path(path)).build())
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenClient.getAccessToken())
                    .retrieve()
                    .bodyToMono(responseType)
                    .retryWhen(Retry.backoff(2, Duration.ofMillis(500))
                            .filter(SpotifyApiClient::isRetryable)
                            .onRetryExhaustedThrow((spec, signal) -> signal.failure()))
                    .block();
        } catch (WebClientResponseException ex) {
            throw new SpotifySyncException("Spotify API request to " + path + " failed with status " + ex.getStatusCode().value());
        } catch (WebClientRequestException ex) {
            throw new SpotifySyncException("Spotify API request to " + path + " failed: connection/timeout error");
        }
    }

    private static boolean isRetryable(Throwable ex) {
        if (ex instanceof WebClientResponseException wcre) {
            return wcre.getStatusCode().value() == 429 || wcre.getStatusCode().is5xxServerError();
        }
        return ex instanceof WebClientRequestException;
    }

    private static String externalUrl(EpisodeItem item) {
        return item.externalUrls() != null ? item.externalUrls().get("spotify") : null;
    }

    /** Spotify releaseDate can be year, year-month, or full date depending on the show's precision. */
    private static Instant parseReleaseDate(String releaseDate) {
        if (releaseDate == null) return null;
        try {
            return switch (releaseDate.length()) {
                case 4 -> LocalDate.of(Integer.parseInt(releaseDate), 1, 1).atStartOfDay(ZoneOffset.UTC).toInstant();
                case 7 -> LocalDate.parse(releaseDate + "-01").atStartOfDay(ZoneOffset.UTC).toInstant();
                default -> LocalDate.parse(releaseDate).atStartOfDay(ZoneOffset.UTC).toInstant();
            };
        } catch (Exception e) {
            return null;
        }
    }

    private static Integer parseDurationSeconds(Long durationMs) {
        return durationMs != null ? (int) (durationMs / 1000) : null;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record EpisodesPage(List<EpisodeItem> items, String next) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record EpisodeItem(
            String id,
            String name,
            String description,
            @JsonProperty("release_date") String releaseDate,
            @JsonProperty("duration_ms") Long durationMs,
            @JsonProperty("external_urls") Map<String, String> externalUrls,
            List<Image> images) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record Image(String url) {}
}
