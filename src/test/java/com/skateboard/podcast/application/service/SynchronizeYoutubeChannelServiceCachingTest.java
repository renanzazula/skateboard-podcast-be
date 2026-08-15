package com.skateboard.podcast.application.service;

import com.skateboard.podcast.adapter.in.rest.PodcastService;
import com.skateboard.podcast.application.port.in.*;
import com.skateboard.podcast.application.port.out.CategoryRepositoryPort;
import com.skateboard.podcast.application.port.out.LoadPostPort;
import com.skateboard.podcast.application.port.out.PostCategoryPort;
import com.skateboard.podcast.application.port.out.YoutubeContentPort;
import com.skateboard.podcast.domain.model.Category;
import com.skateboard.podcast.domain.model.Post;
import com.skateboard.podcast.domain.model.PostStatus;
import com.skateboard.podcast.infrastructure.youtube.YoutubeProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
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

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * SynchronizeYoutubeChannelService bypasses PodcastService and calls
 * CreatePostUseCase directly, so it must own its own eviction of the same
 * podcast-post cache PodcastService's mutations evict — otherwise the
 * scheduler could create new posts while the feed/slug cache keeps serving
 * pre-sync results until the TTL expires (this is exactly what happened:
 * the cache was never wired up on this path).
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = SynchronizeYoutubeChannelServiceCachingTest.Config.class)
class SynchronizeYoutubeChannelServiceCachingTest {

    @Configuration
    @EnableCaching
    static class Config {

        @Bean
        CacheManager cacheManager() {
            return new ConcurrentMapCacheManager(PodcastService.POST_CACHE);
        }

        @Bean
        YoutubeContentPort youtubeContentPort() { return mock(YoutubeContentPort.class); }
        @Bean
        LoadPostPort loadPostPort() { return mock(LoadPostPort.class); }
        @Bean
        CreatePostUseCase createPostUseCase() { return mock(CreatePostUseCase.class); }
        @Bean
        GetPostUseCase getPostUseCase() { return mock(GetPostUseCase.class); }
        @Bean
        GetPostBySlugUseCase getPostBySlugUseCase() { return mock(GetPostBySlugUseCase.class); }
        @Bean
        UpdatePostUseCase updatePostUseCase() { return mock(UpdatePostUseCase.class); }
        @Bean
        DeletePostUseCase deletePostUseCase() { return mock(DeletePostUseCase.class); }
        @Bean
        ImportPostsUseCase importPostsUseCase() { return mock(ImportPostsUseCase.class); }
        @Bean
        CategoryRepositoryPort categoryRepositoryPort() { return mock(CategoryRepositoryPort.class); }
        @Bean
        PostCategoryPort postCategoryPort() { return mock(PostCategoryPort.class); }

        @Bean
        YoutubeProperties youtubeProperties() {
            YoutubeProperties properties = new YoutubeProperties();
            properties.setChannelId("UC_TEST_CHANNEL");
            return properties;
        }

        @Bean
        SynchronizeYoutubeChannelService synchronizeYoutubeChannelService(
                YoutubeContentPort youtubeContentPort, LoadPostPort loadPostPort,
                CreatePostUseCase createPostUseCase, CategoryRepositoryPort categoryRepositoryPort,
                PostCategoryPort postCategoryPort, YoutubeProperties properties) {
            return new SynchronizeYoutubeChannelService(youtubeContentPort, loadPostPort, createPostUseCase,
                    categoryRepositoryPort, postCategoryPort, properties);
        }

        @Bean
        GetCategoriesUseCase getCategoriesUseCase() { return mock(GetCategoriesUseCase.class); }
        @Bean
        GetPostsByCategoryUseCase getPostsByCategoryUseCase() { return mock(GetPostsByCategoryUseCase.class); }

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

    // Autowired by interface, not the concrete class: @EnableCaching proxies
    // this bean via a JDK dynamic proxy (it implements
    // SynchronizeYoutubeChannelUseCase), which can't be assigned to the
    // concrete type.
    @Autowired private SynchronizeYoutubeChannelUseCase syncService;
    @Autowired private PodcastService podcastService;
    @Autowired private CacheManager cacheManager;
    @Autowired private YoutubeContentPort youtubeContentPort;
    @Autowired private LoadPostPort loadPostPort;
    @Autowired private CreatePostUseCase createPostUseCase;
    @Autowired private GetPostUseCase getPostUseCase;
    @Autowired private CategoryRepositoryPort categoryRepositoryPort;
    @Autowired private PostCategoryPort postCategoryPort;

    @BeforeEach
    void setUp() {
        reset(youtubeContentPort, loadPostPort, createPostUseCase, getPostUseCase, categoryRepositoryPort, postCategoryPort);
        cacheManager.getCache(PodcastService.POST_CACHE).clear();
        when(getPostUseCase.execute(anyInt(), anyInt())).thenReturn(new GetPostUseCase.Result(List.of(), 0));
        when(youtubeContentPort.resolveChannel("UC_TEST_CHANNEL"))
                .thenReturn(new YoutubeContentPort.YoutubeChannel("UC_TEST_CHANNEL", "Show", "UU_TEST_UPLOADS"));
        when(youtubeContentPort.getVideoDurations(any())).thenReturn(List.of());
        when(youtubeContentPort.getLatestVideos(anyString(), anyInt())).thenReturn(List.of());
        when(youtubeContentPort.getPlaylists(anyString())).thenReturn(List.of());
        when(categoryRepositoryPort.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    private YoutubeContentPort.YoutubeVideo video(String id) {
        return new YoutubeContentPort.YoutubeVideo(id, "Ep " + id, "desc", Instant.parse("2026-01-01T00:00:00Z"), null);
    }

    private Post somePost() {
        return Post.create("Some Post", "some-post", PostStatus.PUBLISHED, null, null, "[]", "[]", null);
    }

    @Test
    void syncEvictsFeedCacheWhenItCreatesNewPosts() {
        when(youtubeContentPort.getLatestVideos(anyString(), anyInt())).thenReturn(List.of(video("v1")));
        when(loadPostPort.findByYoutubeVideoId("v1")).thenReturn(Optional.empty());
        when(createPostUseCase.execute(any())).thenReturn(somePost());

        podcastService.getPost(0, 10);
        syncService.execute();
        podcastService.getPost(0, 10);

        verify(getPostUseCase, times(2)).execute(0, 10);
    }

    @Test
    void syncEvictsFeedCacheWhenOnlyCategoriesChange() {
        // No new posts (created=0) but a new playlist -> category association appears.
        when(youtubeContentPort.getPlaylists(anyString()))
                .thenReturn(List.of(new YoutubeContentPort.YoutubePlaylist("PL1", "Podcasts", null, null)));
        Category category = Category.createFromYoutube("podcasts", "PL1", "Podcasts", null, null, false);
        when(categoryRepositoryPort.findByExternalId(anyString(), anyString())).thenReturn(Optional.of(category));
        when(youtubeContentPort.getAllPlaylistItems("PL1")).thenReturn(List.of(video("v1")));
        when(postCategoryPort.findVideoIdsByCategory(category.getId())).thenReturn(java.util.Set.of());
        Post existingPost = somePost();
        when(loadPostPort.findByYoutubeVideoId("v1")).thenReturn(Optional.of(existingPost));

        podcastService.getPost(0, 10);
        syncService.execute();
        podcastService.getPost(0, 10);

        verify(getPostUseCase, times(2)).execute(0, 10);
    }

    @Test
    void syncDoesNotEvictFeedCacheWhenNothingIsNew() {
        when(youtubeContentPort.getLatestVideos(anyString(), anyInt())).thenReturn(List.of(video("v1")));
        when(loadPostPort.findByYoutubeVideoId("v1")).thenReturn(Optional.of(somePost()));

        podcastService.getPost(0, 10);
        syncService.execute();
        podcastService.getPost(0, 10);

        verify(getPostUseCase, times(1)).execute(0, 10);
    }
}
