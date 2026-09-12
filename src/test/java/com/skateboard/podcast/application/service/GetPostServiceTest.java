package com.skateboard.podcast.application.service;

import com.skateboard.podcast.application.port.in.GetPostUseCase;
import com.skateboard.podcast.application.port.out.LoadPostPort;
import com.skateboard.podcast.domain.model.Post;
import com.skateboard.podcast.domain.model.PostStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GetPostServiceTest {

    @Mock
    private LoadPostPort loadPostPort;

    private GetPostService service;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        service = new GetPostService(loadPostPort);
    }

    private Post post(String title) {
        return Post.create(title, title.toLowerCase(), PostStatus.PUBLISHED, null, null, "[]", "[]", UUID.randomUUID());
    }

    @Test
    void nullSearchReturnsThePublishedFeed() {
        List<Post> posts = List.of(post("Episode One"), post("Episode Two"));
        when(loadPostPort.findPublished(0, 10)).thenReturn(posts);
        when(loadPostPort.countPublished()).thenReturn(2L);

        GetPostUseCase.Result result = service.execute(null, 0, 10);

        assertThat(result.posts()).isEqualTo(posts);
        assertThat(result.total()).isEqualTo(2L);
        verify(loadPostPort, never()).searchPublished(any(), anyInt(), anyInt());
    }

    @Test
    void blankSearchReturnsThePublishedFeed() {
        List<Post> posts = List.of(post("Episode One"));
        when(loadPostPort.findPublished(1, 5)).thenReturn(posts);
        when(loadPostPort.countPublished()).thenReturn(1L);

        GetPostUseCase.Result result = service.execute("   ", 1, 5);

        assertThat(result.posts()).isEqualTo(posts);
        assertThat(result.total()).isEqualTo(1L);
    }

    @Test
    void nonBlankSearchDelegatesToSearchPublished() {
        List<Post> posts = List.of(post("Skateboard Podcast #70"));
        when(loadPostPort.searchPublished("skate", 0, 10)).thenReturn(posts);
        when(loadPostPort.countSearchPublished("skate")).thenReturn(1L);

        GetPostUseCase.Result result = service.execute("skate", 0, 10);

        assertThat(result.posts()).isEqualTo(posts);
        assertThat(result.total()).isEqualTo(1L);
        verify(loadPostPort, never()).findPublished(anyInt(), anyInt());
    }
}
