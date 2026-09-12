package com.skateboard.podcast.application.service;

import com.skateboard.podcast.application.port.out.LoadPostPort;
import com.skateboard.podcast.domain.model.Post;
import com.skateboard.podcast.domain.model.PostStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

class GetPostBySlugServiceTest {

    @Mock
    private LoadPostPort loadPostPort;

    private GetPostBySlugService service;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        service = new GetPostBySlugService(loadPostPort);
    }

    @Test
    void returnsThePostWhenFound() {
        Post existing = Post.create("Episode", "episode", PostStatus.PUBLISHED, null, null, "[]", "[]", UUID.randomUUID());
        when(loadPostPort.findBySlug("episode")).thenReturn(Optional.of(existing));

        Optional<Post> result = service.execute("episode");

        assertThat(result).contains(existing);
    }

    @Test
    void returnsEmptyOptionalWhenNotFound() {
        when(loadPostPort.findBySlug("missing")).thenReturn(Optional.empty());

        Optional<Post> result = service.execute("missing");

        assertThat(result).isEmpty();
    }
}
