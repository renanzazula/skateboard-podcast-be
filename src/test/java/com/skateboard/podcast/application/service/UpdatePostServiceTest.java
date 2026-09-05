package com.skateboard.podcast.application.service;

import com.skateboard.podcast.application.port.in.UpdatePostUseCase;
import com.skateboard.podcast.application.port.out.LoadPostPort;
import com.skateboard.podcast.application.port.out.SavePostPort;
import com.skateboard.podcast.domain.exception.PostNotFoundException;
import com.skateboard.podcast.domain.model.Post;
import com.skateboard.podcast.domain.model.PostStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

class UpdatePostServiceTest {

    @Mock
    private LoadPostPort loadPostPort;

    @Mock
    private SavePostPort savePostPort;

    private UpdatePostService service;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        service = new UpdatePostService(loadPostPort, savePostPort);
    }

    @Test
    void omittedStatusPreservesExistingStatus() {
        Post existing = Post.create("Draft Episode", "draft-episode", PostStatus.DRAFT,
                null, null, "[]", "[]", UUID.randomUUID());
        when(loadPostPort.findById(existing.getId().toString())).thenReturn(Optional.of(existing));
        when(savePostPort.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Post updated = service.execute(new UpdatePostUseCase.Input(
                existing.getId().toString(), "New Title", "new-title", null,
                null, null, "[]", "[]"));

        assertThat(updated.getStatus()).isEqualTo(PostStatus.DRAFT);
        assertThat(updated.getTitle()).isEqualTo("New Title");
    }

    @Test
    void explicitStatusOverridesExistingStatus() {
        Post existing = Post.create("Draft Episode", "draft-episode", PostStatus.DRAFT,
                null, null, "[]", "[]", UUID.randomUUID());
        when(loadPostPort.findById(existing.getId().toString())).thenReturn(Optional.of(existing));
        when(savePostPort.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Post updated = service.execute(new UpdatePostUseCase.Input(
                existing.getId().toString(), "New Title", "new-title", PostStatus.PUBLISHED,
                null, null, "[]", "[]"));

        assertThat(updated.getStatus()).isEqualTo(PostStatus.PUBLISHED);
    }

    @Test
    void missingPostThrowsPostNotFoundException() {
        String id = UUID.randomUUID().toString();
        when(loadPostPort.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.execute(new UpdatePostUseCase.Input(
                id, "Title", "title", null, null, null, "[]", "[]")))
                .isInstanceOf(PostNotFoundException.class);
    }

    @Test
    void omittedPublishAtKeepsTheExistingValue() {
        Instant originalPublishAt = Instant.parse("2024-01-01T00:00:00Z");
        Post existing = Post.reconstitute(UUID.randomUUID(), "ep-70", "Skateboard Podcast #70",
                PostStatus.PUBLISHED, originalPublishAt, null, null, null, "[]", "[]",
                Instant.now(), Instant.now(), UUID.randomUUID(), null, null, null, 70, null, List.of());
        when(loadPostPort.findById(existing.getId().toString())).thenReturn(Optional.of(existing));
        when(savePostPort.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Post updated = service.execute(new UpdatePostUseCase.Input(
                existing.getId().toString(), "Skateboard Podcast #70 (fixed typo)", "ep-70", null,
                null, "cover.jpg", "[]", "[]"));

        assertThat(updated.getPublishAt()).isEqualTo(originalPublishAt);
    }
}
