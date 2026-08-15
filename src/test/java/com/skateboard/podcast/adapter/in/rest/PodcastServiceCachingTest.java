package com.skateboard.podcast.adapter.in.rest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.skateboard.application.dto.CreatePostRequest;
import com.skateboard.application.dto.ImportPostItem;
import com.skateboard.application.dto.ImportPostsRequest;
import com.skateboard.podcast.application.port.in.*;
import com.skateboard.podcast.domain.model.Post;
import com.skateboard.podcast.domain.model.PostStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Verifies the cache annotation semantics on {@link PodcastService} (keys,
 * eviction, the null-result guard) against an in-memory cache manager —
 * Redis-specific serialization is covered by manual verification.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = PodcastServiceCachingTest.Config.class)
class PodcastServiceCachingTest {

    @Configuration
    @EnableCaching
    static class Config {

        @Bean
        CacheManager cacheManager() {
            return new ConcurrentMapCacheManager(PodcastService.POST_CACHE);
        }

        @Bean
        CreatePostUseCase createPostUseCase() { return mock(CreatePostUseCase.class); }
        @Bean
        GetPostUseCase getFeedUseCase() { return mock(GetPostUseCase.class); }
        @Bean
        GetPostBySlugUseCase getPostBySlugUseCase() { return mock(GetPostBySlugUseCase.class); }
        @Bean
        UpdatePostUseCase updatePostUseCase() { return mock(UpdatePostUseCase.class); }
        @Bean DeletePostUseCase deletePostUseCase() { return mock(DeletePostUseCase.class); }
        @Bean
        ImportPostsUseCase importPostsUseCase() { return mock(ImportPostsUseCase.class); }
        @Bean
        GetCategoriesUseCase getCategoriesUseCase() { return mock(GetCategoriesUseCase.class); }
        @Bean
        GetPostsByCategoryUseCase getPostsByCategoryUseCase() { return mock(GetPostsByCategoryUseCase.class); }
        @Bean
        SynchronizeYoutubeChannelUseCase synchronizeYoutubeChannelUseCase() { return mock(SynchronizeYoutubeChannelUseCase.class); }

        @Bean
        PodcastService podcastService(CreatePostUseCase create, GetPostUseCase feed,
                                      GetPostBySlugUseCase bySlug, UpdatePostUseCase update,
                                      DeletePostUseCase delete, ImportPostsUseCase importPosts,
                                      GetCategoriesUseCase categories, GetPostsByCategoryUseCase postsByCategory,
                                      SynchronizeYoutubeChannelUseCase sync) {
            return new PodcastService(create, feed, bySlug, update, delete, importPosts,
                    categories, postsByCategory, sync, new ObjectMapper());
        }
    }

    @Autowired private PodcastService service;
    @Autowired private CacheManager cacheManager;
    @Autowired private CreatePostUseCase createPostUseCase;
    @Autowired private GetPostUseCase getPostUseCase;
    @Autowired private GetPostBySlugUseCase getPostBySlugUseCase;
    @Autowired private DeletePostUseCase deletePostUseCase;
    @Autowired private ImportPostsUseCase importPostsUseCase;
    @Autowired private GetCategoriesUseCase getCategoriesUseCase;
    @Autowired private GetPostsByCategoryUseCase getPostsByCategoryUseCase;

    @BeforeEach
    void setUp() {
        reset(createPostUseCase, getPostUseCase, getPostBySlugUseCase,
                deletePostUseCase, importPostsUseCase, getCategoriesUseCase, getPostsByCategoryUseCase);
        cacheManager.getCache(PodcastService.POST_CACHE).clear();
        when(getPostUseCase.execute(anyInt(), anyInt()))
                .thenReturn(new GetPostUseCase.Result(List.of(), 0));
    }

    @Test
    void feedIsCachedPerPageAndSize() {
        service.getPost(0, 10);
        service.getPost(0, 10);
        verify(getPostUseCase, times(1)).execute(0, 10);

        service.getPost(1, 10);
        verify(getPostUseCase, times(1)).execute(1, 10);
    }

    @Test
    void postBySlugIsCached() {
        when(getPostBySlugUseCase.execute("ep-1")).thenReturn(Optional.of(publishedPost()));

        service.getPostBySlug("ep-1");
        service.getPostBySlug("ep-1");

        verify(getPostBySlugUseCase, times(1)).execute("ep-1");
    }

    @Test
    void nullSlugResultIsNotCached() {
        when(getPostBySlugUseCase.execute("missing")).thenReturn(Optional.empty());

        service.getPostBySlug("missing");
        service.getPostBySlug("missing");

        verify(getPostBySlugUseCase, times(2)).execute("missing");
    }

    @Test
    void categoriesAreCached() {
        when(getCategoriesUseCase.execute()).thenReturn(new GetCategoriesUseCase.Result(List.of()));

        service.getCategories();
        service.getCategories();

        verify(getCategoriesUseCase, times(1)).execute();
    }

    @Test
    void categoryPostsAreCachedPerSlugPageAndSize() {
        when(getPostsByCategoryUseCase.execute(anyString(), anyInt(), anyInt()))
                .thenReturn(new GetPostsByCategoryUseCase.Result(List.of(), 0));

        service.getPostsByCategory("podcasts", 0, 10);
        service.getPostsByCategory("podcasts", 0, 10);
        verify(getPostsByCategoryUseCase, times(1)).execute("podcasts", 0, 10);

        service.getPostsByCategory("events", 0, 10);
        verify(getPostsByCategoryUseCase, times(1)).execute("events", 0, 10);
    }

    @Test
    void createEvictsBothCaches() {
        when(getPostBySlugUseCase.execute("ep-1")).thenReturn(Optional.of(publishedPost()));
        when(createPostUseCase.execute(any())).thenReturn(publishedPost());
        service.getPost(0, 10);
        service.getPostBySlug("ep-1");

        CreatePostRequest createPostRequest = new CreatePostRequest();
        createPostRequest.setTitle("New Episode");
        service.createPost(createPostRequest, UUID.randomUUID());

        service.getPost(0, 10);
        service.getPostBySlug("ep-1");
        verify(getPostUseCase, times(2)).execute(0, 10);
        verify(getPostBySlugUseCase, times(2)).execute("ep-1");
    }

    @Test
    void deleteEvictsFeedCache() {
        service.getPost(0, 10);

        service.deletePost(UUID.randomUUID());

        service.getPost(0, 10);
        verify(getPostUseCase, times(2)).execute(0, 10);
    }

    @Test
    void importEvictsFeedCache() {
        when(importPostsUseCase.execute(any()))
                .thenReturn(new ImportPostsUseCase.Result(1, 0, List.of()));
        service.getPost(0, 10);

        ImportPostsRequest importPostsRequest = new ImportPostsRequest();
        importPostsRequest.posts(List.of(new ImportPostItem().title("Ep")));

        service.importPosts(importPostsRequest, UUID.randomUUID());

        service.getPost(0, 10);
        verify(getPostUseCase, times(2)).execute(0, 10);
    }

    private Post publishedPost() {
        return Post.create("Episode 1", "ep-1", PostStatus.PUBLISHED,
                null, null, "[]", "[]", UUID.randomUUID());
    }
}
