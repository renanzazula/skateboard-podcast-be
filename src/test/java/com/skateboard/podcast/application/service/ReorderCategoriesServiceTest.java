package com.skateboard.podcast.application.service;

import com.skateboard.podcast.application.port.in.ReorderCategoriesUseCase;
import com.skateboard.podcast.application.port.out.CategoryRepositoryPort;
import com.skateboard.podcast.domain.model.Category;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ReorderCategoriesServiceTest {

    @Mock private CategoryRepositoryPort categoryRepositoryPort;

    private ReorderCategoriesService service;

    private Category podcasts;
    private Category events;
    private Category talks;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        service = new ReorderCategoriesService(categoryRepositoryPort);
        podcasts = Category.createFromYoutube("podcasts", "PL1", "Podcasts", null, null, false);
        events = Category.createFromYoutube("events", "PL2", "Events", null, null, false);
        talks = Category.createFromYoutube("talks", "PL3", "Talks", null, null, false);
        when(categoryRepositoryPort.findAll()).thenReturn(List.of(podcasts, events, talks));
        when(categoryRepositoryPort.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void writesContiguousPositionsInTheSubmittedOrder() {
        ReorderCategoriesUseCase.Result result = service.execute(new ReorderCategoriesUseCase.Input(
                List.of(talks.getId(), podcasts.getId(), events.getId())));

        assertThat(talks.getDisplayOrder()).isEqualTo(0);
        assertThat(podcasts.getDisplayOrder()).isEqualTo(1);
        assertThat(events.getDisplayOrder()).isEqualTo(2);
        assertThat(result.categories()).containsExactly(talks, podcasts, events);
        verify(categoryRepositoryPort, times(3)).save(any());
    }

    @Test
    void duplicateIdIsRejected() {
        assertThatThrownBy(() -> service.execute(new ReorderCategoriesUseCase.Input(
                List.of(podcasts.getId(), podcasts.getId(), events.getId()))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Duplicate");
        verify(categoryRepositoryPort, never()).save(any());
    }

    @Test
    void unknownIdIsRejected() {
        assertThatThrownBy(() -> service.execute(new ReorderCategoriesUseCase.Input(
                List.of(podcasts.getId(), events.getId(), UUID.randomUUID()))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown");
        verify(categoryRepositoryPort, never()).save(any());
    }

    @Test
    void staleListMissingACategoryIsRejected() {
        assertThatThrownBy(() -> service.execute(new ReorderCategoriesUseCase.Input(
                List.of(podcasts.getId(), events.getId()))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("every category");
        verify(categoryRepositoryPort, never()).save(any());
    }
}
