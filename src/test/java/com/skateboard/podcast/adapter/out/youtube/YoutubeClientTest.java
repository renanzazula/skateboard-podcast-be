package com.skateboard.podcast.adapter.out.youtube;

import com.skateboard.podcast.application.port.out.YoutubeContentPort;
import com.skateboard.podcast.infrastructure.youtube.YoutubeProperties;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
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
 * Exercises YoutubeClient against a real local HTTP server rather than
 * mocking WebClient's reactive internals — closer to how the adapter
 * actually behaves over the wire.
 */
class YoutubeClientTest {

    private MockWebServer server;
    private YoutubeClient client;
    private YoutubeProperties properties;

    @BeforeEach
    void setUp() throws IOException {
        server = new MockWebServer();
        server.start();
        properties = new YoutubeProperties();
        properties.getApi().setBaseUrl(server.url("/").toString());
        properties.getApi().setKey("test-key");
        properties.getApi().setConnectTimeoutMs(2000);
        properties.getApi().setReadTimeoutMs(2000);
        client = new YoutubeClient(WebClient.builder(), properties);
    }

    @AfterEach
    void tearDown() throws IOException {
        server.shutdown();
    }

    @Test
    void resolveChannelParsesUploadsPlaylist() {
        server.enqueue(new MockResponse().setBody("""
                {"items":[{"id":"UC1","snippet":{"title":"Show"},
                "contentDetails":{"relatedPlaylists":{"uploads":"UU1"}}}]}
                """).addHeader("Content-Type", "application/json"));

        YoutubeContentPort.YoutubeChannel channel = client.resolveChannel("UC1");

        assertThat(channel.channelId()).isEqualTo("UC1");
        assertThat(channel.title()).isEqualTo("Show");
        assertThat(channel.uploadsPlaylistId()).isEqualTo("UU1");
    }

    @Test
    void resolveChannelThrowsWhenNotFound() {
        server.enqueue(new MockResponse().setBody("{\"items\":[]}")
                .addHeader("Content-Type", "application/json"));

        assertThatThrownBy(() -> client.resolveChannel("missing"))
                .isInstanceOf(YoutubeContentPort.YoutubeSyncException.class);
    }

    @Test
    void getLatestVideosPaginatesUntilLimitReached() {
        server.enqueue(new MockResponse().setBody("""
                {"items":[
                  {"snippet":{"title":"Ep 1","description":"d1","publishedAt":"2026-01-01T00:00:00Z",
                    "thumbnails":{"high":{"url":"http://t1"}}},"contentDetails":{"videoId":"v1"}},
                  {"snippet":{"title":"Ep 2","description":"d2","publishedAt":"2026-01-02T00:00:00Z",
                    "thumbnails":{"maxres":{"url":"http://t2"}}},"contentDetails":{"videoId":"v2"}}
                ],"nextPageToken":"page2"}
                """).addHeader("Content-Type", "application/json"));
        server.enqueue(new MockResponse().setBody("""
                {"items":[
                  {"snippet":{"title":"Ep 3","description":"d3","publishedAt":"2026-01-03T00:00:00Z",
                    "thumbnails":{}},"contentDetails":{"videoId":"v3"}}
                ]}
                """).addHeader("Content-Type", "application/json"));

        List<YoutubeContentPort.YoutubeVideo> videos = client.getLatestVideos("UU1", 3);

        assertThat(videos).hasSize(3);
        assertThat(videos.get(0).videoId()).isEqualTo("v1");
        assertThat(videos.get(0).publishedAt()).isEqualTo(Instant.parse("2026-01-01T00:00:00Z"));
        assertThat(videos.get(0).thumbnailUrl()).isEqualTo("http://t1");
        assertThat(videos.get(1).thumbnailUrl()).isEqualTo("http://t2");
        assertThat(videos.get(2).videoId()).isEqualTo("v3");
        assertThat(videos.get(2).thumbnailUrl()).isNull();
    }

    @Test
    void capturesThumbnailDimensionsFromTheChosenQuality() {
        server.enqueue(new MockResponse().setBody("""
                {"items":[
                  {"snippet":{"title":"Ep 1","description":"d1","publishedAt":"2026-01-01T00:00:00Z",
                    "thumbnails":{"high":{"url":"http://low","width":480,"height":360},
                                  "maxres":{"url":"http://best","width":1280,"height":720}}},
                   "contentDetails":{"videoId":"v1"}},
                  {"snippet":{"title":"Ep 2","description":"d2","publishedAt":"2026-01-02T00:00:00Z",
                    "thumbnails":{"high":{"url":"http://nodims"}}},"contentDetails":{"videoId":"v2"}}
                ]}
                """).addHeader("Content-Type", "application/json"));

        List<YoutubeContentPort.YoutubeVideo> videos = client.getAllPlaylistItems("PL1");

        // Dimensions come from whichever quality bestThumbnail picked, not the first listed.
        assertThat(videos.get(0).thumbnailUrl()).isEqualTo("http://best");
        assertThat(videos.get(0).thumbnailWidth()).isEqualTo(1280);
        assertThat(videos.get(0).thumbnailHeight()).isEqualTo(720);
        // A thumbnail without width/height stays null rather than failing the sync.
        assertThat(videos.get(1).thumbnailUrl()).isEqualTo("http://nodims");
        assertThat(videos.get(1).thumbnailWidth()).isNull();
        assertThat(videos.get(1).thumbnailHeight()).isNull();
    }

    @Test
    void getAllPlaylistItemsPaginatesWithNoCap() {
        server.enqueue(new MockResponse().setBody("""
                {"items":[
                  {"snippet":{"title":"Ep 1","description":"d1","publishedAt":"2026-01-01T00:00:00Z","thumbnails":{}},
                   "contentDetails":{"videoId":"v1"}}
                ],"nextPageToken":"page2"}
                """).addHeader("Content-Type", "application/json"));
        server.enqueue(new MockResponse().setBody("""
                {"items":[
                  {"snippet":{"title":"Ep 2","description":"d2","publishedAt":"2026-01-02T00:00:00Z","thumbnails":{}},
                   "contentDetails":{"videoId":"v2"}}
                ]}
                """).addHeader("Content-Type", "application/json"));

        List<YoutubeContentPort.YoutubeVideo> videos = client.getAllPlaylistItems("PL1");

        assertThat(videos).extracting(YoutubeContentPort.YoutubeVideo::videoId).containsExactly("v1", "v2");
    }

    @Test
    void getPlaylistsParsesSnippetAndPaginates() {
        server.enqueue(new MockResponse().setBody("""
                {"items":[
                  {"id":"PL1","snippet":{"title":"Podcasts","description":"d",
                    "thumbnails":{"high":{"url":"http://t1"}}}}
                ],"nextPageToken":"page2"}
                """).addHeader("Content-Type", "application/json"));
        server.enqueue(new MockResponse().setBody("""
                {"items":[
                  {"id":"PL2","snippet":{"title":"Events","description":null,"thumbnails":{}}}
                ]}
                """).addHeader("Content-Type", "application/json"));

        List<YoutubeContentPort.YoutubePlaylist> playlists = client.getPlaylists("UC1");

        assertThat(playlists).hasSize(2);
        assertThat(playlists.get(0).playlistId()).isEqualTo("PL1");
        assertThat(playlists.get(0).title()).isEqualTo("Podcasts");
        assertThat(playlists.get(0).thumbnailUrl()).isEqualTo("http://t1");
        assertThat(playlists.get(1).playlistId()).isEqualTo("PL2");
        assertThat(playlists.get(1).thumbnailUrl()).isNull();
    }

    @Test
    void getVideoDurationsParsesIso8601Duration() {
        server.enqueue(new MockResponse().setBody("""
                {"items":[{"id":"v1","contentDetails":{"duration":"PT1H2M3S"}}]}
                """).addHeader("Content-Type", "application/json"));

        List<YoutubeContentPort.YoutubeVideoDuration> durations = client.getVideoDurations(List.of("v1"));

        assertThat(durations).singleElement().satisfies(d -> {
            assertThat(d.videoId()).isEqualTo("v1");
            assertThat(d.durationSeconds()).isEqualTo(3723);
        });
    }

    @Test
    void fourXxIsNotRetried() throws InterruptedException {
        server.enqueue(new MockResponse().setResponseCode(404));

        assertThatThrownBy(() -> client.resolveChannel("UC1"))
                .isInstanceOf(YoutubeContentPort.YoutubeSyncException.class);
        assertThat(server.getRequestCount()).isEqualTo(1);
    }

    @Test
    void fiveXxIsRetriedThenThrows() {
        server.enqueue(new MockResponse().setResponseCode(503));
        server.enqueue(new MockResponse().setResponseCode(503));
        server.enqueue(new MockResponse().setResponseCode(503));

        assertThatThrownBy(() -> client.resolveChannel("UC1"))
                .isInstanceOf(YoutubeContentPort.YoutubeSyncException.class);
        assertThat(server.getRequestCount()).isEqualTo(3);
    }

    @Test
    void timeoutIsHandled() {
        properties.getApi().setReadTimeoutMs(150);
        client = new YoutubeClient(WebClient.builder(), properties);
        server.enqueue(new MockResponse().setBody("{}").setBodyDelay(3, java.util.concurrent.TimeUnit.SECONDS));
        server.enqueue(new MockResponse().setBody("{}").setBodyDelay(3, java.util.concurrent.TimeUnit.SECONDS));
        server.enqueue(new MockResponse().setBody("{}").setBodyDelay(3, java.util.concurrent.TimeUnit.SECONDS));

        assertThatThrownBy(() -> client.resolveChannel("UC1"))
                .isInstanceOf(YoutubeContentPort.YoutubeSyncException.class);
    }
}
