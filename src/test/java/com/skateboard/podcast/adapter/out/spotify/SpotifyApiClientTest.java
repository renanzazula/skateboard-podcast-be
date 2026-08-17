package com.skateboard.podcast.adapter.out.spotify;

import com.skateboard.podcast.application.port.out.SpotifyContentPort;
import com.skateboard.podcast.infrastructure.spotify.SpotifyProperties;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.IOException;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Exercises SpotifyApiClient against a real local HTTP server, same approach
 * as YoutubeClientTest — the same server also serves the token endpoint (via
 * a real SpotifyTokenClient) so no concrete-class mocking is needed.
 */
class SpotifyApiClientTest {

    private MockWebServer server;
    private SpotifyApiClient client;
    private SpotifyProperties properties;

    @BeforeEach
    void setUp() throws IOException {
        server = new MockWebServer();
        server.start();
        properties = new SpotifyProperties();
        properties.getApi().setBaseUrl(server.url("/").toString());
        properties.getApi().setTokenUrl(server.url("/api/token").toString());
        properties.getApi().setClientId("test-client");
        properties.getApi().setClientSecret("test-secret");
        properties.getApi().setConnectTimeoutMs(2000);
        properties.getApi().setReadTimeoutMs(2000);
        properties.setShowId("693VmIGutJaAlUztFYF8dl");
        SpotifyTokenClient tokenClient = new SpotifyTokenClient(WebClient.builder(), properties);
        client = new SpotifyApiClient(WebClient.builder(), properties, tokenClient);
        enqueueTokenResponse();
    }

    @AfterEach
    void tearDown() throws IOException {
        server.shutdown();
    }

    private void enqueueTokenResponse() {
        server.enqueue(new MockResponse().setBody("""
                {"access_token":"test-token","token_type":"Bearer","expires_in":3600}
                """).addHeader("Content-Type", "application/json"));
    }

    @Test
    void getShowEpisodesParsesAndSendsBearerToken() throws InterruptedException {
        server.enqueue(new MockResponse().setBody("""
                {"items":[
                  {"id":"ep1","name":"EP 24 Skateboarding","description":"d","release_date":"2026-01-01",
                   "duration_ms":3723000,"external_urls":{"spotify":"https://open.spotify.com/episode/ep1"},
                   "images":[{"url":"http://img1"}]}
                ],"next":null}
                """).addHeader("Content-Type", "application/json"));

        List<SpotifyContentPort.SpotifyEpisode> episodes = client.getShowEpisodes("693VmIGutJaAlUztFYF8dl");

        assertThat(episodes).singleElement().satisfies(ep -> {
            assertThat(ep.id()).isEqualTo("ep1");
            assertThat(ep.title()).isEqualTo("EP 24 Skateboarding");
            assertThat(ep.releaseDate()).isEqualTo(Instant.parse("2026-01-01T00:00:00Z"));
            assertThat(ep.durationSeconds()).isEqualTo(3723);
            assertThat(ep.externalUrl()).isEqualTo("https://open.spotify.com/episode/ep1");
            assertThat(ep.imageUrl()).isEqualTo("http://img1");
        });

        server.takeRequest(); // token request
        RecordedRequest request = server.takeRequest();
        assertThat(request.getHeader("Authorization")).isEqualTo("Bearer test-token");
        assertThat(request.getPath()).contains("/v1/shows/693VmIGutJaAlUztFYF8dl/episodes");
    }

    @Test
    void paginatesUntilNextIsNull() {
        server.enqueue(new MockResponse().setBody("""
                {"items":[{"id":"ep1","name":"Ep 1","description":"d","release_date":"2026",
                  "duration_ms":60000,"external_urls":{"spotify":"http://u1"},"images":[]}],
                 "next":"http://more"}
                """).addHeader("Content-Type", "application/json"));
        server.enqueue(new MockResponse().setBody("""
                {"items":[{"id":"ep2","name":"Ep 2","description":"d","release_date":"2026-03",
                  "duration_ms":60000,"external_urls":{"spotify":"http://u2"},"images":[]}],
                 "next":null}
                """).addHeader("Content-Type", "application/json"));

        List<SpotifyContentPort.SpotifyEpisode> episodes = client.getShowEpisodes("693VmIGutJaAlUztFYF8dl");

        assertThat(episodes).extracting(SpotifyContentPort.SpotifyEpisode::id).containsExactly("ep1", "ep2");
    }

    @Test
    void fourXxIsNotRetried() {
        server.enqueue(new MockResponse().setResponseCode(404));

        assertThatThrownBy(() -> client.getShowEpisodes("missing"))
                .isInstanceOf(SpotifyContentPort.SpotifySyncException.class);
        assertThat(server.getRequestCount()).isEqualTo(2); // token request + the one unretried episode request
    }
}
