package com.skateboard.podcast.application.service;

import com.skateboard.podcast.application.port.out.LoadPostPort;
import com.skateboard.podcast.application.port.out.SavePostPort;
import com.skateboard.podcast.domain.exception.PostNotFoundException;
import com.skateboard.podcast.domain.model.Post;
import com.skateboard.podcast.domain.model.PostStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DeletePostServiceTest {

    @Mock
    private LoadPostPort loadPostPort;

    @Mock
    private SavePostPort savePostPort;

    private DeletePostService service;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        service = new DeletePostService(loadPostPort, savePostPort);
    }

    @Test
    void deletesTheUnderlyingPostWhenItExists() {
        Post existing = Post.create("Episode", "episode", PostStatus.PUBLISHED, null, null, "[]", "[]", UUID.randomUUID());
        String id = existing.getId().toString();
        when(loadPostPort.findById(id)).thenReturn(Optional.of(existing));

        service.execute(id);

        verify(savePostPort).deleteById(id);
    }

    @Test
    void missingPostThrowsPostNotFoundExceptionAndNeverDeletes() {
        String id = UUID.randomUUID().toString();
        when(loadPostPort.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.execute(id))
                .isInstanceOf(PostNotFoundException.class)
                .hasMessageContaining(id);

        verify(savePostPort, never()).deleteById(anyString());
    }
}
