package com.skateboard.podcast.adapter.in.rest;

import com.skateboard.application.dto.AdminCategoryResponse;
import com.skateboard.application.dto.CategoryResponse;
import com.skateboard.application.dto.CreatePostRequest;
import com.skateboard.application.dto.FeedPageResponse;
import com.skateboard.application.dto.ImportPostsRequest;
import com.skateboard.application.dto.ImportResult;
import com.skateboard.application.dto.PostResponse;
import com.skateboard.application.dto.ReorderCategoriesRequest;
import com.skateboard.application.dto.SyncResultResponse;
import com.skateboard.application.dto.UpdateCategoryRequest;
import com.skateboard.application.dto.UpdatePostRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PodcastControllerTest {

    @Mock
    private PodcastService podcastService;

    private PodcastController controller;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        controller = new PodcastController(podcastService);
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private void authenticateAs(String name) {
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        Authentication authentication = new TestingAuthenticationToken(name, "n/a");
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
    }

    // ── getPodcastFeed: clamping behaviour lives in the controller itself ────

    @Test
    void getPodcastFeedDefaultsPageAndSizeWhenNull() {
        FeedPageResponse expected = new FeedPageResponse();
        when(podcastService.getPost(null, 0, 10)).thenReturn(expected);

        ResponseEntity<FeedPageResponse> response = controller.getPodcastFeed(null, null, null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isSameAs(expected);
        verify(podcastService).getPost(null, 0, 10);
    }

    @Test
    void getPodcastFeedClampsSizeAbove50() {
        FeedPageResponse expected = new FeedPageResponse();
        when(podcastService.getPost("term", 2, 50)).thenReturn(expected);

        ResponseEntity<FeedPageResponse> response = controller.getPodcastFeed(2, 500, "term");

        assertThat(response.getBody()).isSameAs(expected);
        verify(podcastService).getPost("term", 2, 50);
    }

    @Test
    void getPodcastFeedPassesThroughValidPageAndSize() {
        FeedPageResponse expected = new FeedPageResponse();
        when(podcastService.getPost(null, 3, 20)).thenReturn(expected);

        controller.getPodcastFeed(3, 20, null);

        verify(podcastService).getPost(null, 3, 20);
    }

    // ── getPodcastPostBySlug: found vs. null-means-404 ───────────────────────

    @Test
    void getPodcastPostBySlugReturnsOkWhenFound() {
        PostResponse post = new PostResponse();
        when(podcastService.getPostBySlug("episode-1")).thenReturn(post);

        ResponseEntity<PostResponse> response = controller.getPodcastPostBySlug("episode-1");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isSameAs(post);
    }

    @Test
    void getPodcastPostBySlugThrowsNotFoundWhenNull() {
        when(podcastService.getPostBySlug("missing")).thenReturn(null);

        assertThatThrownBy(() -> controller.getPodcastPostBySlug("missing"))
                .isInstanceOf(ResponseStatusException.class)
                .hasFieldOrPropertyWithValue("statusCode", HttpStatus.NOT_FOUND);
    }

    // ── getPodcastFeaturedEpisode: found vs. null-means-404 ──────────────────

    @Test
    void getPodcastFeaturedEpisodeReturnsOkWhenFound() {
        PostResponse post = new PostResponse();
        when(podcastService.getFeaturedEpisode()).thenReturn(post);

        ResponseEntity<PostResponse> response = controller.getPodcastFeaturedEpisode();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isSameAs(post);
    }

    @Test
    void getPodcastFeaturedEpisodeThrowsNotFoundWhenNull() {
        when(podcastService.getFeaturedEpisode()).thenReturn(null);

        assertThatThrownBy(() -> controller.getPodcastFeaturedEpisode())
                .isInstanceOf(ResponseStatusException.class)
                .hasFieldOrPropertyWithValue("statusCode", HttpStatus.NOT_FOUND);
    }

    // ── getPodcastPostById: found vs. null-means-404 ─────────────────────────

    @Test
    void getPodcastPostByIdReturnsOkWhenFound() {
        UUID id = UUID.randomUUID();
        PostResponse post = new PostResponse();
        when(podcastService.getPostById(id)).thenReturn(post);

        ResponseEntity<PostResponse> response = controller.getPodcastPostById(id);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isSameAs(post);
    }

    @Test
    void getPodcastPostByIdThrowsNotFoundWhenNull() {
        UUID id = UUID.randomUUID();
        when(podcastService.getPostById(id)).thenReturn(null);

        assertThatThrownBy(() -> controller.getPodcastPostById(id))
                .isInstanceOf(ResponseStatusException.class)
                .hasFieldOrPropertyWithValue("statusCode", HttpStatus.NOT_FOUND);
    }

    // ── createPodcastPost: 201 + resolveCurrentUserId (success + failure) ───

    @Test
    void createPodcastPostReturnsCreatedAndResolvesUserIdFromJwtSub() {
        UUID userId = UUID.randomUUID();
        authenticateAs(userId.toString());
        CreatePostRequest req = new CreatePostRequest().title("Hello");
        PostResponse created = new PostResponse();
        when(podcastService.createPost(eq(req), eq(userId))).thenReturn(created);

        ResponseEntity<PostResponse> response = controller.createPodcastPost(req);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isSameAs(created);
        verify(podcastService).createPost(req, userId);
    }

    @Test
    void createPodcastPostResolvesNullUserIdWhenAuthenticationMissing() {
        // No SecurityContext authentication set up -> NPE inside resolveCurrentUserId,
        // swallowed, falls back to null.
        CreatePostRequest req = new CreatePostRequest().title("Hello");
        when(podcastService.createPost(any(), isNull())).thenReturn(new PostResponse());

        controller.createPodcastPost(req);

        verify(podcastService).createPost(req, null);
    }

    @Test
    void createPodcastPostResolvesNullUserIdWhenSubIsNotAUuid() {
        authenticateAs("not-a-uuid");
        CreatePostRequest req = new CreatePostRequest().title("Hello");
        when(podcastService.createPost(any(), isNull())).thenReturn(new PostResponse());

        controller.createPodcastPost(req);

        verify(podcastService).createPost(req, null);
    }

    // ── updatePodcastPost ─────────────────────────────────────────────────────

    @Test
    void updatePodcastPostReturnsOkWithBody() {
        UUID id = UUID.randomUUID();
        UpdatePostRequest req = new UpdatePostRequest().title("Updated");
        PostResponse updated = new PostResponse();
        when(podcastService.updatePost(id, req)).thenReturn(updated);

        ResponseEntity<PostResponse> response = controller.updatePodcastPost(id, req);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isSameAs(updated);
    }

    // ── deletePodcastPost ─────────────────────────────────────────────────────

    @Test
    void deletePodcastPostReturnsNoContentAndDelegates() {
        UUID id = UUID.randomUUID();

        ResponseEntity<Void> response = controller.deletePodcastPost(id);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(response.getBody()).isNull();
        verify(podcastService).deletePost(id);
    }

    // ── importPodcastPosts: 200 + resolveCurrentUserId ───────────────────────

    @Test
    void importPodcastPostsReturnsOkAndResolvesUserId() {
        UUID userId = UUID.randomUUID();
        authenticateAs(userId.toString());
        ImportPostsRequest req = new ImportPostsRequest();
        ImportResult result = new ImportResult().imported(1).failed(0);
        when(podcastService.importPosts(eq(req), eq(userId))).thenReturn(result);

        ResponseEntity<ImportResult> response = controller.importPodcastPosts(req);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isSameAs(result);
        verify(podcastService).importPosts(req, userId);
    }

    // ── syncPodcastFromYoutube ────────────────────────────────────────────────

    @Test
    void syncPodcastFromYoutubeReturnsOkWithBody() {
        SyncResultResponse result = new SyncResultResponse().received(3);
        when(podcastService.triggerSync()).thenReturn(result);

        ResponseEntity<SyncResultResponse> response = controller.syncPodcastFromYoutube();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isSameAs(result);
    }

    // ── getCategories ────────────────────────────────────────────────────────

    @Test
    void getCategoriesReturnsOkWithBody() {
        List<CategoryResponse> categories = List.of(new CategoryResponse());
        when(podcastService.getCategories()).thenReturn(categories);

        ResponseEntity<List<CategoryResponse>> response = controller.getCategories();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isSameAs(categories);
    }

    // ── getCategoryPosts: same clamping as getPodcastFeed ────────────────────

    @Test
    void getCategoryPostsDefaultsPageAndClampsSize() {
        FeedPageResponse expected = new FeedPageResponse();
        when(podcastService.getPostsByCategory("podcasts", 0, 50)).thenReturn(expected);

        ResponseEntity<FeedPageResponse> response = controller.getCategoryPosts("podcasts", null, 1000);

        assertThat(response.getBody()).isSameAs(expected);
        verify(podcastService).getPostsByCategory("podcasts", 0, 50);
    }

    @Test
    void getCategoryPostsPassesThroughValidPageAndSize() {
        FeedPageResponse expected = new FeedPageResponse();
        when(podcastService.getPostsByCategory("events", 1, 5)).thenReturn(expected);

        controller.getCategoryPosts("events", 1, 5);

        verify(podcastService).getPostsByCategory("events", 1, 5);
    }

    // ── admin categories ─────────────────────────────────────────────────────

    @Test
    void getAdminCategoriesReturnsOkWithBody() {
        List<AdminCategoryResponse> categories = List.of(new AdminCategoryResponse());
        when(podcastService.getAdminCategories()).thenReturn(categories);

        ResponseEntity<List<AdminCategoryResponse>> response = controller.getAdminCategories();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isSameAs(categories);
    }

    @Test
    void updateCategoryReturnsOkWithBody() {
        UUID id = UUID.randomUUID();
        UpdateCategoryRequest req = new UpdateCategoryRequest().name("New Name");
        AdminCategoryResponse updated = new AdminCategoryResponse();
        when(podcastService.updateCategory(id, req)).thenReturn(updated);

        ResponseEntity<AdminCategoryResponse> response = controller.updateCategory(id, req);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isSameAs(updated);
    }

    @Test
    void reorderCategoriesReturnsOkWithBody() {
        ReorderCategoriesRequest req = new ReorderCategoriesRequest(List.of(UUID.randomUUID()));
        List<AdminCategoryResponse> reordered = List.of(new AdminCategoryResponse());
        when(podcastService.reorderCategories(req)).thenReturn(reordered);

        ResponseEntity<List<AdminCategoryResponse>> response = controller.reorderCategories(req);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isSameAs(reordered);
    }

    @Test
    void setDefaultCategoryReturnsOkWithBody() {
        UUID id = UUID.randomUUID();
        AdminCategoryResponse updated = new AdminCategoryResponse();
        when(podcastService.setDefaultCategory(id)).thenReturn(updated);

        ResponseEntity<AdminCategoryResponse> response = controller.setDefaultCategory(id);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isSameAs(updated);
    }
}
