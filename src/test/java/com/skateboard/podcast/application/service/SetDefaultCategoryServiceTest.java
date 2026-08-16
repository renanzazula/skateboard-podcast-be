package com.skateboard.podcast.application.service;

import com.skateboard.podcast.application.port.out.CategoryRepositoryPort;
import com.skateboard.podcast.domain.exception.CategoryNotFoundException;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SetDefaultCategoryServiceTest {

    @Mock private CategoryRepositoryPort categoryRepositoryPort;

    private SetDefaultCategoryService service;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        service = new SetDefaultCategoryService(categoryRepositoryPort);
        when(categoryRepositoryPort.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void setsTheSingleDefaultAndLocksEveryCategory() {
        Category oldDefault = Category.createFromYoutube("podcasts", "PL1", "Podcasts", null, null, true);
        Category newDefault = Category.createFromYoutube("events", "PL2", "Events", null, null, false);
        Category bystander = Category.createFromYoutube("talks", "PL3", "Talks", null, null, false);
        when(categoryRepositoryPort.findAll()).thenReturn(List.of(oldDefault, newDefault, bystander));

        Category result = service.execute(newDefault.getId());

        assertThat(result.isDefault()).isTrue();
        assertThat(result.isDefaultLocked()).isTrue();
        assertThat(oldDefault.isDefault()).isFalse();
        assertThat(oldDefault.isDefaultLocked()).isTrue();
        assertThat(bystander.isDefault()).isFalse();
        assertThat(bystander.isDefaultLocked()).isTrue();
    }

    @Test
    void unknownIdThrowsWithoutTouchingAnyCategory() {
        Category existing = Category.createFromYoutube("podcasts", "PL1", "Podcasts", null, null, true);
        when(categoryRepositoryPort.findAll()).thenReturn(List.of(existing));

        assertThatThrownBy(() -> service.execute(UUID.randomUUID()))
                .isInstanceOf(CategoryNotFoundException.class);
        assertThat(existing.isDefault()).isTrue();
        assertThat(existing.isDefaultLocked()).isFalse();
        verify(categoryRepositoryPort, never()).save(any());
    }
}
