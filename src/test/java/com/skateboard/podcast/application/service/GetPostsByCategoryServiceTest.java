package com.skateboard.podcast.application.service;

import com.skateboard.podcast.application.port.in.GetPostsByCategoryUseCase;
import com.skateboard.podcast.application.port.out.CategoryRepositoryPort;
import com.skateboard.podcast.application.port.out.PostCategoryPort;
import com.skateboard.podcast.domain.exception.CategoryNotFoundException;
import com.skateboard.podcast.domain.model.Category;
import com.skateboard.podcast.domain.model.EpisodeSearch;
import com.skateboard.podcast.domain.model.Post;
import com.skateboard.podcast.domain.model.PostStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GetPostsByCategoryServiceTest {

    @Mock private CategoryRepositoryPort categoryRepositoryPort;
    @Mock private PostCategoryPort postCategoryPort;

    private GetPostsByCategoryService service;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        service = new GetPostsByCategoryService(categoryRepositoryPort, postCategoryPort);
    }

    private Post post(String slug) {
        return Post.create(slug, slug, PostStatus.PUBLISHED, null, null, "[]", "[]", null);
    }

    @Test
    void returnsPostsAndTotalForAnEnabledCategory() {
        when(categoryRepositoryPort.findBySlug("podcasts"))
                .thenReturn(Optional.of(Category.createFromYoutube("podcasts", "PL1", "Podcasts", null, null, true)));
        when(postCategoryPort.findPublishedByCategorySlug("podcasts", 0, 10)).thenReturn(List.of(post("ep-1")));
        when(postCategoryPort.countPublishedByCategorySlug("podcasts")).thenReturn(1L);

        GetPostsByCategoryUseCase.Result result = service.execute("podcasts", null, 0, 10);

        assertThat(result.posts()).hasSize(1);
        assertThat(result.total()).isEqualTo(1L);
        verify(postCategoryPort, never()).searchPublishedByCategorySlug(any(), any(), anyInt(), anyInt());
    }

    @Test
    void blankSearchReturnsTheWholeCategory() {
        when(categoryRepositoryPort.findBySlug("podcasts"))
                .thenReturn(Optional.of(Category.createFromYoutube("podcasts", "PL1", "Podcasts", null, null, true)));
        when(postCategoryPort.findPublishedByCategorySlug("podcasts", 0, 10)).thenReturn(List.of(post("ep-1")));
        when(postCategoryPort.countPublishedByCategorySlug("podcasts")).thenReturn(1L);

        GetPostsByCategoryUseCase.Result result = service.execute("podcasts", "   ", 0, 10);

        assertThat(result.total()).isEqualTo(1L);
        verify(postCategoryPort, never()).searchPublishedByCategorySlug(any(), any(), anyInt(), anyInt());
    }

    @Test
    void nonBlankSearchFiltersWithinTheCategory() {
        when(categoryRepositoryPort.findBySlug("podcasts"))
                .thenReturn(Optional.of(Category.createFromYoutube("podcasts", "PL1", "Podcasts", null, null, true)));
        EpisodeSearch expected = new EpisodeSearch("42", 42);
        when(postCategoryPort.searchPublishedByCategorySlug("podcasts", expected, 0, 10)).thenReturn(List.of(post("ep-42")));
        when(postCategoryPort.countSearchPublishedByCategorySlug("podcasts", expected)).thenReturn(1L);

        GetPostsByCategoryUseCase.Result result = service.execute("podcasts", "42", 0, 10);

        assertThat(result.posts()).singleElement().satisfies(p -> assertThat(p.getSlug()).isEqualTo("ep-42"));
        assertThat(result.total()).isEqualTo(1L);
        verify(postCategoryPort, never()).findPublishedByCategorySlug(any(), anyInt(), anyInt());
    }

    @Test
    void searchStillRequiresAnEnabledCategory() {
        when(categoryRepositoryPort.findBySlug("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.execute("missing", "42", 0, 10))
                .isInstanceOf(CategoryNotFoundException.class);
    }

    @Test
    void throwsWhenCategoryDoesNotExist() {
        when(categoryRepositoryPort.findBySlug("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.execute("missing", null, 0, 10))
                .isInstanceOf(CategoryNotFoundException.class);
    }

    @Test
    void throwsWhenCategoryIsDisabled() {
        Category disabled = Category.createFromYoutube("events", "PL2", "Events", null, null, false);
        disabled.disable();
        when(categoryRepositoryPort.findBySlug("events")).thenReturn(Optional.of(disabled));

        assertThatThrownBy(() -> service.execute("events", null, 0, 10))
                .isInstanceOf(CategoryNotFoundException.class);
    }
}
