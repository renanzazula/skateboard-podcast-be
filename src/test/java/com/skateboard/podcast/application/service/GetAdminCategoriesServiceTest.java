package com.skateboard.podcast.application.service;

import com.skateboard.podcast.application.port.in.GetAdminCategoriesUseCase;
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

class GetAdminCategoriesServiceTest {

    @Mock
    private CategoryRepositoryPort categoryRepositoryPort;

    @Mock
    private PostCategoryPort postCategoryPort;

    private GetAdminCategoriesService service;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        service = new GetAdminCategoriesService(categoryRepositoryPort, postCategoryPort);
    }

    private Category category(String slug, boolean isDefault) {
        return Category.createFromYoutube(slug, "PL-" + slug, slug, null, null, isDefault);
    }

    @Test
    void includesDisabledCategoriesAndAttachesPublishedCounts() {
        Category enabled = category("podcasts", false);
        Category disabled = category("events", false);
        disabled.disable();
        when(categoryRepositoryPort.findAll()).thenReturn(List.of(enabled, disabled));
        when(postCategoryPort.countPublishedByCategory()).thenReturn(Map.of(enabled.getId(), 3L));

        GetAdminCategoriesUseCase.Result result = service.execute();

        assertThat(result.categories()).hasSize(2);
        assertThat(result.categories()).extracting(GetAdminCategoriesUseCase.CategoryWithCount::category)
                .contains(enabled, disabled);
        assertThat(result.categories()).anySatisfy(c -> {
            assertThat(c.category().getSlug()).isEqualTo("podcasts");
            assertThat(c.postCount()).isEqualTo(3L);
        });
        assertThat(result.categories()).anySatisfy(c -> {
            assertThat(c.category().getSlug()).isEqualTo("events");
            assertThat(c.category().isEnabled()).isFalse();
            assertThat(c.postCount()).isEqualTo(0L); // no entry in the count map -> defaults to 0
        });
    }

    @Test
    void ordersByDisplayOrderThenDefaultThenCreatedAt() {
        Category noOrderNonDefault = category("no-order", false);
        Category noOrderDefault = category("no-order-default", true);
        Category ordered = category("ordered", false);
        ordered.setDisplayOrder(0);

        // Both noOrderNonDefault/noOrderDefault have null displayOrder, so they
        // tie-break on isDefault (default first), after the explicitly ordered one.
        when(categoryRepositoryPort.findAll())
                .thenReturn(List.of(noOrderNonDefault, noOrderDefault, ordered));
        when(postCategoryPort.countPublishedByCategory()).thenReturn(Map.of());

        GetAdminCategoriesUseCase.Result result = service.execute();

        assertThat(result.categories())
                .extracting(c -> c.category().getSlug())
                .containsExactly("ordered", "no-order-default", "no-order");
    }

    @Test
    void returnsEmptyResultWhenNoCategoriesExist() {
        when(categoryRepositoryPort.findAll()).thenReturn(List.of());
        when(postCategoryPort.countPublishedByCategory()).thenReturn(Map.of());

        assertThat(service.execute().categories()).isEmpty();
    }
}
