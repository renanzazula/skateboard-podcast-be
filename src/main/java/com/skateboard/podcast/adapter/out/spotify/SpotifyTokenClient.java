package com.skateboard.podcast.adapter.out.spotify;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.skateboard.podcast.application.port.out.SpotifyContentPort.SpotifySyncException;
import com.skateboard.podcast.infrastructure.spotify.SpotifyProperties;
import io.netty.channel.ChannelOption;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;
import java.time.Instant;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * Spotify Client Credentials flow (backend-to-backend, no user context —
 * see .docs/README_SPOTIFY_YOUTUBE_PODCAST_INTEGRATION.md §6). The token and
 * client secret never appear in anything logged or in any exception message,
 * same rule YoutubeClient follows for its API key.
 */
@Component
public class SpotifyTokenClient {

    // Refresh a bit before actual expiry so a request never races an
    // about-to-expire token.
    private static final Duration EXPIRY_BUFFER = Duration.ofSeconds(30);

    private final WebClient webClient;
    private final SpotifyProperties properties;

    private volatile CachedToken cachedToken;

    public SpotifyTokenClient(WebClient.Builder webClientBuilder, SpotifyProperties properties) {
        this.properties = properties;
        SpotifyProperties.Api api = properties.getApi();
        HttpClient httpClient = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, api.getConnectTimeoutMs())
                .responseTimeout(Duration.ofMillis(api.getReadTimeoutMs()));
        this.webClient = webClientBuilder
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .build();
    }

    public synchronized String getAccessToken() {
        CachedToken current = cachedToken;
        if (current != null && current.expiresAt().isAfter(Instant.now().plus(EXPIRY_BUFFER))) {
            return current.token();
        }
        CachedToken fresh = requestToken();
        cachedToken = fresh;
        return fresh.token();
    }

    private CachedToken requestToken() {
        SpotifyProperties.Api api = properties.getApi();
        String credentials = Base64.getEncoder().encodeToString(
                (api.getClientId() + ":" + api.getClientSecret()).getBytes(StandardCharsets.UTF_8));

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "client_credentials");

        TokenResponse response;
        try {
            response = webClient.post()
                    .uri(api.getTokenUrl())
                    .header(HttpHeaders.AUTHORIZATION, "Basic " + credentials)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .bodyValue(form)
                    .retrieve()
                    .bodyToMono(TokenResponse.class)
                    .block();
        } catch (WebClientResponseException ex) {
            throw new SpotifySyncException("Spotify token request failed with status " + ex.getStatusCode().value());
        } catch (WebClientRequestException ex) {
            throw new SpotifySyncException("Spotify token request failed: connection/timeout error");
        }

        if (response == null || response.accessToken() == null) {
            throw new SpotifySyncException("Spotify token request returned no access token");
        }
        int expiresInSeconds = response.expiresIn() != null ? response.expiresIn() : 3600;
        return new CachedToken(response.accessToken(), Instant.now().plusSeconds(expiresInSeconds));
    }

    private record CachedToken(String token, Instant expiresAt) {}

    private record TokenResponse(
            @JsonProperty("access_token") String accessToken,
            @JsonProperty("token_type") String tokenType,
            @JsonProperty("expires_in") Integer expiresIn) {}
}
