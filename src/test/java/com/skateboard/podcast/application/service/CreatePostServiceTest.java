package com.skateboard.podcast.application.service;

import com.skateboard.podcast.application.port.in.CreatePostUseCase;
import com.skateboard.podcast.application.port.out.LoadPostPort;
import com.skateboard.podcast.application.port.out.SavePostPort;
import com.skateboard.podcast.domain.model.Post;
import com.skateboard.podcast.domain.model.PostPlatform;
import com.skateboard.podcast.domain.model.PostStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CreatePostServiceTest {

    @Mock
    private LoadPostPort loadPostPort;

    @Mock
    private SavePostPort savePostPort;

    @Mock
    private PodcastPublicationNotifier publicationNotifier;

    private CreatePostService service;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        service = new CreatePostService(loadPostPort, savePostPort, publicationNotifier);
        when(savePostPort.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void createsAPostWithTheGivenSlugWhenItIsAlreadyUnique() {
        when(loadPostPort.existsBySlug("episode")).thenReturn(false);

        Post created = service.execute(new CreatePostUseCase.Input(
                "Episode", "episode", PostStatus.DRAFT, null, null, "[]", "[]", UUID.randomUUID()));

        assertThat(created.getSlug()).isEqualTo("episode");
        assertThat(created.getPlatformLinks()).isEmpty();
        verify(publicationNotifier).notifyIfNewlyPublished(created);
    }

    @Test
    void appendsACounterToTheSlugOnCollisionUntilUnique() {
        when(loadPostPort.existsBySlug("episode")).thenReturn(true);
        when(loadPostPort.existsBySlug("episode-1")).thenReturn(true);
        when(loadPostPort.existsBySlug("episode-2")).thenReturn(false);

        Post created = service.execute(new CreatePostUseCase.Input(
                "Episode", "episode", PostStatus.DRAFT, null, null, "[]", "[]", UUID.randomUUID()));

        assertThat(created.getSlug()).isEqualTo("episode-2");
    }

    @Test
    void attachesYoutubeMetadataAndPlatformLinkWhenAVideoIdIsGiven() {
        when(loadPostPort.existsBySlug("episode")).thenReturn(false);

        Post created = service.execute(new CreatePostUseCase.Input(
                "Episode", "episode", PostStatus.PUBLISHED, null, null, "[]", "[]", UUID.randomUUID(),
                "yt-123", "description", 600, 42));

        assertThat(created.getYoutubeVideoId()).isEqualTo("yt-123");
        assertThat(created.getDescription()).isEqualTo("description");
        assertThat(created.getDurationSeconds()).isEqualTo(600);
        assertThat(created.getEpisodeNumber()).isEqualTo(42);
        assertThat(created.getPlatformLinks()).hasSize(1);
        assertThat(created.getPlatformLinks().get(0).platform()).isEqualTo(PostPlatform.YOUTUBE);
        assertThat(created.getPlatformLinks().get(0).externalId()).isEqualTo("yt-123");
        assertThat(created.getPlatformLinks().get(0).externalUrl()).isEqualTo("https://www.youtube.com/watch?v=yt-123");
    }

    @Test
    void savesTheCreatedPostThroughSavePostPort() {
        when(loadPostPort.existsBySlug("episode")).thenReturn(false);

        service.execute(new CreatePostUseCase.Input(
                "Episode", "episode", PostStatus.DRAFT, null, null, "[]", "[]", UUID.randomUUID()));

        verify(savePostPort).save(any());
    }
}
