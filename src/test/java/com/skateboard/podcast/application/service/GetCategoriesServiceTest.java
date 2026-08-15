package com.skateboard.podcast.application.service;

import com.skateboard.podcast.application.port.in.GetCategoriesUseCase;
import com.skateboard.podcast.application.port.out.CategoryRepositoryPort;
import com.skateboard.podcast.application.port.out.PostCategoryPort;
import com.skateboard.podcast.domain.model.Category;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

class GetCategoriesServiceTest {

    @Mock private CategoryRepositoryPort categoryRepositoryPort;
    @Mock private PostCategoryPort postCategoryPort;

    private GetCategoriesService service;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        service = new GetCategoriesService(categoryRepositoryPort, postCategoryPort);
    }

    private Category category(String slug, String externalId) {
        return Category.createFromYoutube(slug, externalId, slug, null, null, false);
    }

    @Test
    void returnsEnabledCategoriesWithTheirPostCounts() {
        Category podcasts = category("podcasts", "PL1");
        Category events = category("events", "PL2");
        when(categoryRepositoryPort.findAllEnabled()).thenReturn(List.of(podcasts, events));
        when(postCategoryPort.countPublishedByCategory()).thenReturn(Map.of(podcasts.getId(), 5L));

        GetCategoriesUseCase.Result result = service.execute();

        assertThat(result.categories()).hasSize(2);
        assertThat(result.categories()).anySatisfy(c -> {
            assertThat(c.category().getSlug()).isEqualTo("podcasts");
            assertThat(c.postCount()).isEqualTo(5L);
        });
        assertThat(result.categories()).anySatisfy(c -> {
            assertThat(c.category().getSlug()).isEqualTo("events");
            assertThat(c.postCount()).isEqualTo(0L); // no entry in the count map -> defaults to 0
        });
    }

    @Test
    void returnsEmptyListWhenNoCategoriesExist() {
        when(categoryRepositoryPort.findAllEnabled()).thenReturn(List.of());
        when(postCategoryPort.countPublishedByCategory()).thenReturn(Map.of());

        assertThat(service.execute().categories()).isEmpty();
    }
}
