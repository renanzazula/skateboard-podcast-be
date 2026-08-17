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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SpotifyTokenClientTest {

    private MockWebServer server;
    private SpotifyTokenClient client;
    private SpotifyProperties properties;

    @BeforeEach
    void setUp() throws IOException {
        server = new MockWebServer();
        server.start();
        properties = new SpotifyProperties();
        properties.getApi().setTokenUrl(server.url("/api/token").toString());
        properties.getApi().setClientId("test-client");
        properties.getApi().setClientSecret("test-secret");
        properties.getApi().setConnectTimeoutMs(2000);
        properties.getApi().setReadTimeoutMs(2000);
        client = new SpotifyTokenClient(WebClient.builder(), properties);
    }

    @AfterEach
    void tearDown() throws IOException {
        server.shutdown();
    }

    @Test
    void fetchesAndCachesToken() throws InterruptedException {
        server.enqueue(new MockResponse().setBody("""
                {"access_token":"tok-1","token_type":"Bearer","expires_in":3600}
                """).addHeader("Content-Type", "application/json"));

        String first = client.getAccessToken();
        String second = client.getAccessToken();

        assertThat(first).isEqualTo("tok-1");
        assertThat(second).isEqualTo("tok-1");
        assertThat(server.getRequestCount()).isEqualTo(1); // second call served from cache

        RecordedRequest request = server.takeRequest();
        assertThat(request.getHeader("Authorization")).startsWith("Basic ");
        assertThat(request.getBody().readUtf8()).isEqualTo("grant_type=client_credentials");
    }

    @Test
    void refreshesTokenAfterExpiry() {
        server.enqueue(new MockResponse().setBody("""
                {"access_token":"tok-1","token_type":"Bearer","expires_in":1}
                """).addHeader("Content-Type", "application/json"));
        server.enqueue(new MockResponse().setBody("""
                {"access_token":"tok-2","token_type":"Bearer","expires_in":3600}
                """).addHeader("Content-Type", "application/json"));

        String first = client.getAccessToken();
        String second = client.getAccessToken(); // expires_in=1 is already inside the refresh buffer

        assertThat(first).isEqualTo("tok-1");
        assertThat(second).isEqualTo("tok-2");
        assertThat(server.getRequestCount()).isEqualTo(2);
    }

    @Test
    void failureIsWrappedAndDoesNotLeakCredentials() {
        server.enqueue(new MockResponse().setResponseCode(401));

        assertThatThrownBy(() -> client.getAccessToken())
                .isInstanceOf(SpotifyContentPort.SpotifySyncException.class)
                .hasMessageNotContaining("test-secret");
    }
}
