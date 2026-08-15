package com.skateboard.podcast.application.service;

import com.skateboard.podcast.application.port.in.CreatePostUseCase;
import com.skateboard.podcast.application.port.in.SynchronizeYoutubeChannelUseCase;
import com.skateboard.podcast.application.port.out.LoadPostPort;
import com.skateboard.podcast.application.port.out.YoutubeContentPort;
import com.skateboard.podcast.domain.model.Post;
import com.skateboard.podcast.domain.model.PostStatus;
import com.skateboard.podcast.infrastructure.youtube.YoutubeProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class SynchronizeYoutubeChannelServiceTest {

    @Mock
    private YoutubeContentPort youtubeContentPort;

    @Mock
    private LoadPostPort loadPostPort;

    @Mock
    private CreatePostUseCase createPostUseCase;

    private YoutubeProperties properties;
    private SynchronizeYoutubeChannelService service;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        properties = new YoutubeProperties();
        properties.setChannelId("UC_TEST_CHANNEL");
        properties.getSync().setInitialImportLimit(20);
        service = new SynchronizeYoutubeChannelService(youtubeContentPort, loadPostPort, createPostUseCase, properties);
    }

    private YoutubeContentPort.YoutubeVideo video(String id, String title) {
        return new YoutubeContentPort.YoutubeVideo(id, title, "desc " + id, Instant.parse("2026-01-01T00:00:00Z"), "http://thumb/" + id);
    }

    private void stubChannelResolution() {
        when(youtubeContentPort.resolveChannel("UC_TEST_CHANNEL"))
                .thenReturn(new YoutubeContentPort.YoutubeChannel("UC_TEST_CHANNEL", "Show", "UU_TEST_UPLOADS"));
    }

    // A real (lightweight) Post rather than a Mockito mock — Post has no
    // behavior worth stubbing here, and mocking concrete classes needs the
    // inline mock maker's bytecode instrumentation, which is unnecessary risk
    // for a value the test never calls a method on.
    private Post somePost() {
        return Post.create("Some Post", "some-post", PostStatus.PUBLISHED, null, null, "[]", "[]", null);
    }

    @Test
    void newVideoIsPersisted() {
        stubChannelResolution();
        when(youtubeContentPort.getLatestVideos("UU_TEST_UPLOADS", 20)).thenReturn(List.of(video("v1", "Episode #1")));
        when(loadPostPort.findByYoutubeVideoId("v1")).thenReturn(Optional.empty());
        when(youtubeContentPort.getVideoDurations(List.of("v1")))
                .thenReturn(List.of(new YoutubeContentPort.YoutubeVideoDuration("v1", 120)));
        when(createPostUseCase.execute(any())).thenReturn(somePost());

        SynchronizeYoutubeChannelUseCase.Result result = service.execute();

        assertThat(result.success()).isTrue();
        assertThat(result.received()).isEqualTo(1);
        assertThat(result.created()).isEqualTo(1);
        assertThat(result.existing()).isEqualTo(0);

        ArgumentCaptor<CreatePostUseCase.Input> captor = ArgumentCaptor.forClass(CreatePostUseCase.Input.class);
        verify(createPostUseCase).execute(captor.capture());
        CreatePostUseCase.Input input = captor.getValue();
        assertThat(input.youtubeVideoId()).isEqualTo("v1");
        assertThat(input.description()).isEqualTo("desc v1");
        assertThat(input.durationSeconds()).isEqualTo(120);
        assertThat(input.episodeNumber()).isEqualTo(1);
        assertThat(input.status()).isEqualTo(PostStatus.PUBLISHED);
    }

    @Test
    void existingVideoIsNotDuplicated() {
        stubChannelResolution();
        when(youtubeContentPort.getLatestVideos("UU_TEST_UPLOADS", 20)).thenReturn(List.of(video("v1", "Episode #1")));
        when(loadPostPort.findByYoutubeVideoId("v1")).thenReturn(Optional.of(somePost()));

        SynchronizeYoutubeChannelUseCase.Result result = service.execute();

        assertThat(result.created()).isEqualTo(0);
        assertThat(result.existing()).isEqualTo(1);
        verifyNoInteractions(createPostUseCase);
    }

    @Test
    void multipleNewVideosAreAllPersisted() {
        stubChannelResolution();
        when(youtubeContentPort.getLatestVideos("UU_TEST_UPLOADS", 20))
                .thenReturn(List.of(video("v1", "Ep #1"), video("v2", "Ep #2")));
        when(loadPostPort.findByYoutubeVideoId(anyString())).thenReturn(Optional.empty());
        when(youtubeContentPort.getVideoDurations(any())).thenReturn(List.of());
        when(createPostUseCase.execute(any())).thenReturn(somePost());

        SynchronizeYoutubeChannelUseCase.Result result = service.execute();

        assertThat(result.created()).isEqualTo(2);
        verify(createPostUseCase, times(2)).execute(any());
    }

    @Test
    void disabledChannelIsSkipped() {
        properties.setChannelId(null);

        SynchronizeYoutubeChannelUseCase.Result result = service.execute();

        assertThat(result.success()).isFalse();
        assertThat(result.received()).isEqualTo(0);
        verifyNoInteractions(youtubeContentPort, createPostUseCase);
    }

    @Test
    void youtubeFailureIsHandledWithoutThrowing() {
        when(youtubeContentPort.resolveChannel("UC_TEST_CHANNEL"))
                .thenThrow(new YoutubeContentPort.YoutubeSyncException("boom"));

        SynchronizeYoutubeChannelUseCase.Result result = service.execute();

        assertThat(result.success()).isFalse();
        verifyNoInteractions(createPostUseCase);
    }

    @Test
    void oneVideoFailureDoesNotBlockTheRest() {
        stubChannelResolution();
        when(youtubeContentPort.getLatestVideos("UU_TEST_UPLOADS", 20))
                .thenReturn(List.of(video("bad", "Bad Episode"), video("good", "Good Episode")));
        when(loadPostPort.findByYoutubeVideoId(anyString())).thenReturn(Optional.empty());
        when(youtubeContentPort.getVideoDurations(any())).thenReturn(List.of());
        when(createPostUseCase.execute(any()))
                .thenThrow(new RuntimeException("slug collision"))
                .thenReturn(somePost());

        SynchronizeYoutubeChannelUseCase.Result result = service.execute();

        assertThat(result.created()).isEqualTo(1);
        verify(createPostUseCase, times(2)).execute(any());
    }
}
