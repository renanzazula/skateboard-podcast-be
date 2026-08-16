package com.skateboard.podcast.application.service;

import com.skateboard.podcast.application.port.in.UpdateCategoryUseCase;
import com.skateboard.podcast.application.port.out.CategoryRepositoryPort;
import com.skateboard.podcast.domain.exception.CategoryNotFoundException;
import com.skateboard.podcast.domain.model.Category;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UpdateCategoryServiceTest {

    @Mock private CategoryRepositoryPort categoryRepositoryPort;

    private UpdateCategoryService service;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        service = new UpdateCategoryService(categoryRepositoryPort);
        when(categoryRepositoryPort.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void renameSetsCustomNameWithoutTouchingTheYoutubeTitle() {
        Category category = Category.createFromYoutube("podcasts", "PL1", "Podcasts", null, null, false);
        when(categoryRepositoryPort.findById(category.getId())).thenReturn(Optional.of(category));

        Category result = service.execute(new UpdateCategoryUseCase.Input(category.getId(), "Interviews"));

        assertThat(result.getCustomName()).isEqualTo("Interviews");
        assertThat(result.getEffectiveName()).isEqualTo("Interviews");
        assertThat(result.getName()).isEqualTo("Podcasts");
        verify(categoryRepositoryPort).save(category);
    }

    @Test
    void blankNameResetsToTheYoutubeTitle() {
        Category category = Category.createFromYoutube("podcasts", "PL1", "Podcasts", null, null, false);
        category.rename("Interviews");
        when(categoryRepositoryPort.findById(category.getId())).thenReturn(Optional.of(category));

        Category result = service.execute(new UpdateCategoryUseCase.Input(category.getId(), "  "));

        assertThat(result.getCustomName()).isNull();
        assertThat(result.getEffectiveName()).isEqualTo("Podcasts");
    }

    @Test
    void unknownIdThrowsCategoryNotFound() {
        UUID id = UUID.randomUUID();
        when(categoryRepositoryPort.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.execute(new UpdateCategoryUseCase.Input(id, "X")))
                .isInstanceOf(CategoryNotFoundException.class);
        verify(categoryRepositoryPort, never()).save(any());
    }
}
