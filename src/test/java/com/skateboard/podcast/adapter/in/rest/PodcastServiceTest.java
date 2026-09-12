package com.skateboard.podcast.adapter.in.rest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.skateboard.application.dto.*;
import com.skateboard.podcast.application.port.in.*;

import com.skateboard.podcast.domain.model.Category;
import com.skateboard.podcast.domain.model.Post;
import com.skateboard.podcast.domain.model.PostStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PodcastServiceTest {

    @Mock
    private CreatePostUseCase createPostUseCase;

    @Mock
    private GetPostUseCase getPostUseCase;

    @Mock
    private GetPostBySlugUseCase getPostBySlugUseCase;

    @Mock
    private GetPostByIdUseCase getPostByIdUseCase;

    @Mock
    private UpdatePostUseCase updatePostUseCase;

    @Mock
    private DeletePostUseCase deletePostUseCase;

    @Mock
    private ImportPostsUseCase importPostsUseCase;

    @Mock
    private GetCategoriesUseCase getCategoriesUseCase;

    @Mock
    private GetPostsByCategoryUseCase getPostsByCategoryUseCase;

    @Mock
    private SynchronizeYoutubeChannelUseCase synchronizeYoutubeChannelUseCase;

    @Mock
    private GetAdminCategoriesUseCase getAdminCategoriesUseCase;

    @Mock
    private UpdateCategoryUseCase updateCategoryUseCase;

    @Mock
    private ReorderCategoriesUseCase reorderCategoriesUseCase;

    @Mock
    private SetDefaultCategoryUseCase setDefaultCategoryUseCase;

    @Mock
    private GetFeaturedEpisodeUseCase getFeaturedEpisodeUseCase;

    private PodcastService service;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        service = new PodcastService(createPostUseCase, getPostUseCase, getPostBySlugUseCase, getPostByIdUseCase,
                updatePostUseCase, deletePostUseCase, importPostsUseCase, getCategoriesUseCase,
                getPostsByCategoryUseCase, getAdminCategoriesUseCase, updateCategoryUseCase,
                reorderCategoriesUseCase, setDefaultCategoryUseCase,
                synchronizeYoutubeChannelUseCase, getFeaturedEpisodeUseCase, new ObjectMapper());
    }

    @Test
    void getPostMapsUseCaseResultToDto() {
        UUID createdBy = UUID.randomUUID();
        Post post = Post.create("Episode 1", "episode-1", PostStatus.PUBLISHED,
                Instant.parse("2026-01-01T00:00:00Z"), "http://cover.png",
                "[{\"type\":\"text\",\"value\":\"hi\"}]",
                "[{\"platform\":\"youtube\",\"url\":\"http://yt\"}]", createdBy);
        when(getPostUseCase.execute(null, 0, 10)).thenReturn(new GetPostUseCase.Result(List.of(post), 42));

        FeedPageResponse response = service.getPost(null, 0, 10);

        assertThat(response.getTotal()).isEqualTo(42);
        assertThat(response.getPage()).isEqualTo(0);
        assertThat(response.getSize()).isEqualTo(10);
        assertThat(response.getPosts()).hasSize(1);
        PostResponse dto = response.getPosts().get(0);
        assertThat(dto.getSlug()).isEqualTo("episode-1");
        assertThat(dto.getStatus()).isEqualTo(PostResponse.StatusEnum.PUBLISHED);
        assertThat(dto.getPublishAt())
                .isEqualTo(OffsetDateTime.of(2026, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC));
        assertThat(dto.getBlocks()).singleElement()
                .satisfies(block -> assertThat(block).containsEntry("type", "text"));
        assertThat(dto.getSocialMediaLinks()).singleElement()
                .satisfies(link -> assertThat(link.getPlatform()).isEqualTo("youtube"));
        assertThat(dto.getCreatedBy()).isEqualTo(createdBy);
    }

    @Test
    void getPostMapsYoutubeFieldsToDto() {
        Post post = Post.create("Skateboard Podcast #87", "skateboard-podcast-87", PostStatus.PUBLISHED,
                Instant.parse("2026-01-01T00:00:00Z"), "http://thumb.jpg", "[]", "[]", null);
        post.attachYoutubeMetadata("dQw4w9WgXcQ", "Episode description", 3725, 87);
        when(getPostUseCase.execute(null, 0, 10)).thenReturn(new GetPostUseCase.Result(List.of(post), 1));

        PostResponse dto = service.getPost(null, 0, 10).getPosts().get(0);

        assertThat(dto.getYoutubeVideoId()).isEqualTo("dQw4w9WgXcQ");
        assertThat(dto.getYoutubeUrl()).isEqualTo("https://www.youtube.com/watch?v=dQw4w9WgXcQ");
        assertThat(dto.getDescription()).isEqualTo("Episode description");
        assertThat(dto.getDurationSeconds()).isEqualTo(3725);
        assertThat(dto.getEpisodeNumber()).isEqualTo(87);
    }

    @Test
    void getPostMapsPlatformLinksToDto() {
        Post post = Post.create("Skateboard Podcast #87", "skateboard-podcast-87", PostStatus.PUBLISHED,
                Instant.parse("2026-01-01T00:00:00Z"), "http://thumb.jpg", "[]", "[]", null);
        post.attachPlatformLink(new com.skateboard.podcast.domain.model.PostPlatformLink(
                com.skateboard.podcast.domain.model.PostPlatform.YOUTUBE, "dQw4w9WgXcQ",
                "https://www.youtube.com/watch?v=dQw4w9WgXcQ"));
        post.attachPlatformLink(new com.skateboard.podcast.domain.model.PostPlatformLink(
                com.skateboard.podcast.domain.model.PostPlatform.SPOTIFY, "6xyz789",
                "https://open.spotify.com/episode/6xyz789"));
        when(getPostUseCase.execute(null, 0, 10)).thenReturn(new GetPostUseCase.Result(List.of(post), 1));

        PostResponse dto = service.getPost(null, 0, 10).getPosts().get(0);

        assertThat(dto.getPlatforms()).hasSize(2);
        assertThat(dto.getPlatforms())
                .filteredOn(p -> p.getPlatform() == PostPlatformResponse.PlatformEnum.SPOTIFY)
                .singleElement()
                .satisfies(p -> assertThat(p.getExternalUrl()).isEqualTo("https://open.spotify.com/episode/6xyz789"));
    }

    @Test
    void getPostLeavesYoutubeFieldsNullForManuallyCreatedPosts() {
        Post post = Post.create("Manual Post", "manual-post", PostStatus.PUBLISHED,
                null, null, "[]", "[]", null);
        when(getPostUseCase.execute(null, 0, 10)).thenReturn(new GetPostUseCase.Result(List.of(post), 1));

        PostResponse dto = service.getPost(null, 0, 10).getPosts().get(0);

        assertThat(dto.getYoutubeVideoId()).isNull();
        assertThat(dto.getYoutubeUrl()).isNull();
        assertThat(dto.getDescription()).isNull();
        assertThat(dto.getDurationSeconds()).isNull();
        assertThat(dto.getEpisodeNumber()).isNull();
        assertThat(dto.getPlatforms()).isEmpty();
    }

    @Test
    void getPostBySlugReturnsNullWhenNotFound() {
        when(getPostBySlugUseCase.execute("missing")).thenReturn(Optional.empty());

        assertThat(service.getPostBySlug("missing")).isNull();
    }

    @Test
    void getPostByIdReturnsNullWhenNotFound() {
        UUID id = UUID.randomUUID();
        when(getPostByIdUseCase.execute(id.toString())).thenReturn(Optional.empty());

        assertThat(service.getPostById(id)).isNull();
    }

    @Test
    void getPostByIdMapsUseCaseResultToDtoRegardlessOfStatus() {
        UUID id = UUID.randomUUID();
        Post post = Post.create("Draft Episode", "draft-episode", PostStatus.DRAFT,
                null, null, "[]", "[]", null);
        when(getPostByIdUseCase.execute(id.toString())).thenReturn(Optional.of(post));

        PostResponse dto = service.getPostById(id);

        assertThat(dto.getSlug()).isEqualTo("draft-episode");
        assertThat(dto.getStatus()).isEqualTo(PostResponse.StatusEnum.DRAFT);
    }

    @Test
    void createPostDefaultsStatusToPublishedAndSlugifiesTitle() {
        UUID createdBy = UUID.randomUUID();
        Post post = Post.create("Hello World!", "hello-world", PostStatus.PUBLISHED,
                null, null, "[]", "[]", createdBy);
        when(createPostUseCase.execute(any())).thenReturn(post);

        service.createPost(new CreatePostRequest().title("Hello World!"), createdBy);

        ArgumentCaptor<CreatePostUseCase.Input> captor =
                ArgumentCaptor.forClass(CreatePostUseCase.Input.class);
        verify(createPostUseCase).execute(captor.capture());
        assertThat(captor.getValue().slug()).isEqualTo("hello-world");
        assertThat(captor.getValue().status()).isEqualTo(PostStatus.PUBLISHED);
        assertThat(captor.getValue().createdBy()).isEqualTo(createdBy);
    }

    @Test
    void importPostsMapsItemsAndPassesImportedBy() {
        UUID importedBy = UUID.randomUUID();
        when(importPostsUseCase.execute(any()))
                .thenReturn(new ImportPostsUseCase.Result(2, 1, List.of("'Bad': boom")));

        ImportResult result = service.importPosts(new ImportPostsRequest()
                .posts(List.of(
                        new ImportPostItem().title("Ep 1").status(ImportPostItem.StatusEnum.DRAFT),
                        new ImportPostItem().title("Ep 2"))), importedBy);

        assertThat(result.getImported()).isEqualTo(2);
        assertThat(result.getFailed()).isEqualTo(1);
        assertThat(result.getErrors()).containsExactly("'Bad': boom");

        ArgumentCaptor<ImportPostsUseCase.Input> captor =
                ArgumentCaptor.forClass(ImportPostsUseCase.Input.class);
        verify(importPostsUseCase).execute(captor.capture());
        assertThat(captor.getValue().importedBy()).isEqualTo(importedBy);
        assertThat(captor.getValue().items()).hasSize(2);
        assertThat(captor.getValue().items().get(0).status()).isEqualTo("draft");
        // The generated ImportPostItem defaults status to "published" (api.yml default)
        assertThat(captor.getValue().items().get(1).status()).isEqualTo("published");
    }

    @Test
    void getCategoriesUsesStoredDefaultWhenPresent() {
        var podcasts = com.skateboard.podcast.domain.model.Category.createFromYoutube(
                "podcasts", "PL1", "Podcasts", null, null, true);
        var events = com.skateboard.podcast.domain.model.Category.createFromYoutube(
                "events", "PL2", "Events", null, null, false);
        when(getCategoriesUseCase.execute()).thenReturn(new GetCategoriesUseCase.Result(List.of(
                new GetCategoriesUseCase.CategoryWithCount(podcasts, 5),
                new GetCategoriesUseCase.CategoryWithCount(events, 2))));

        List<CategoryResponse> result = service.getCategories();

        assertThat(result).filteredOn(c -> c.getSlug().equals("podcasts")).singleElement()
                .satisfies(c -> assertThat(c.getDefault()).isTrue());
        assertThat(result).filteredOn(c -> c.getSlug().equals("events")).singleElement()
                .satisfies(c -> assertThat(c.getDefault()).isFalse());
    }

    @Test
    void getCategoriesFallsBackToPodcastsSlugWhenNoneIsFlaggedDefault() {
        var podcasts = com.skateboard.podcast.domain.model.Category.createFromYoutube(
                "podcasts", "PL1", "Podcasts", null, null, false);
        var events = com.skateboard.podcast.domain.model.Category.createFromYoutube(
                "events", "PL2", "Events", null, null, false);
        when(getCategoriesUseCase.execute()).thenReturn(new GetCategoriesUseCase.Result(List.of(
                new GetCategoriesUseCase.CategoryWithCount(events, 2),
                new GetCategoriesUseCase.CategoryWithCount(podcasts, 5))));

        List<CategoryResponse> result = service.getCategories();

        assertThat(result).filteredOn(c -> c.getSlug().equals("podcasts")).singleElement()
                .satisfies(c -> assertThat(c.getDefault()).isTrue());
        assertThat(result).filteredOn(c -> c.getSlug().equals("events")).singleElement()
                .satisfies(c -> assertThat(c.getDefault()).isFalse());
    }

    @Test
    void getPostsByCategoryMapsResultToFeedPage() {
        Post post = Post.create("Ep", "ep", PostStatus.PUBLISHED, null, null, "[]", "[]", null);
        when(getPostsByCategoryUseCase.execute("podcasts", 0, 10))
                .thenReturn(new GetPostsByCategoryUseCase.Result(List.of(post), 1));

        FeedPageResponse response = service.getPostsByCategory("podcasts", 0, 10);

        assertThat(response.getTotal()).isEqualTo(1);
        assertThat(response.getPosts()).hasSize(1);
    }

    @Test
    void getFeaturedEpisodeMapsUseCaseResultToDto() {
        Post post = Post.create("TOM YUKIO - Skateboard Podcast #124", "tom-yukio-skateboard-podcast-124",
                PostStatus.PUBLISHED, Instant.parse("2026-01-01T00:00:00Z"), "http://thumb.jpg", "[]", "[]", null);
        when(getFeaturedEpisodeUseCase.execute()).thenReturn(Optional.of(post));

        PostResponse dto = service.getFeaturedEpisode();

        assertThat(dto.getSlug()).isEqualTo("tom-yukio-skateboard-podcast-124");
        assertThat(dto.getTitle()).isEqualTo("TOM YUKIO - Skateboard Podcast #124");
    }

    @Test
    void getFeaturedEpisodeReturnsNullWhenNoneQualifies() {
        when(getFeaturedEpisodeUseCase.execute()).thenReturn(Optional.empty());

        assertThat(service.getFeaturedEpisode()).isNull();
    }

    @Test
    void triggerSyncMapsUseCaseResult() {
        when(synchronizeYoutubeChannelUseCase.execute())
                .thenReturn(new SynchronizeYoutubeChannelUseCase.Result(5, 2, 3, 1, true));

        SyncResultResponse response = service.triggerSync();

        assertThat(response.getReceived()).isEqualTo(5);
        assertThat(response.getCreated()).isEqualTo(2);
        assertThat(response.getExisting()).isEqualTo(3);
        assertThat(response.getCategoryChanges()).isEqualTo(1);
        assertThat(response.getSuccess()).isTrue();
    }

    @Test
    void createPostAppliesExplicitStatusAndPublishAtAndNullBlocks() {
        UUID createdBy = UUID.randomUUID();
        Post post = Post.create("Scheduled Ep", "scheduled-ep", PostStatus.SCHEDULED,
                Instant.parse("2026-03-01T00:00:00Z"), null, "[]", "[]", createdBy);
        when(createPostUseCase.execute(any())).thenReturn(post);

        CreatePostRequest req = new CreatePostRequest()
                .title("Scheduled Ep")
                .status(CreatePostRequest.StatusEnum.SCHEDULED)
                .publishAt(OffsetDateTime.of(2026, 3, 1, 0, 0, 0, 0, ZoneOffset.UTC))
                .blocks(null);

        service.createPost(req, createdBy);

        ArgumentCaptor<CreatePostUseCase.Input> captor =
                ArgumentCaptor.forClass(CreatePostUseCase.Input.class);
        verify(createPostUseCase).execute(captor.capture());
        assertThat(captor.getValue().status()).isEqualTo(PostStatus.SCHEDULED);
        assertThat(captor.getValue().publishAt()).isEqualTo(Instant.parse("2026-03-01T00:00:00Z"));
        // blocks(null) -> blocksToJson's null-list branch still yields "[]"
        assertThat(captor.getValue().blocksJson()).isEqualTo("[]");
    }

    @Test
    void importPostsHandlesNullStatusAndNullPublishAt() {
        UUID importedBy = UUID.randomUUID();
        when(importPostsUseCase.execute(any()))
                .thenReturn(new ImportPostsUseCase.Result(1, 0, List.of()));

        service.importPosts(new ImportPostsRequest()
                .posts(List.of(new ImportPostItem().title("No status").status(null))), importedBy);

        ArgumentCaptor<ImportPostsUseCase.Input> captor =
                ArgumentCaptor.forClass(ImportPostsUseCase.Input.class);
        verify(importPostsUseCase).execute(captor.capture());
        ImportPostsUseCase.PostImportItem item = captor.getValue().items().get(0);
        assertThat(item.status()).isNull();
        assertThat(item.publishAt()).isNull();
    }

    @Test
    void updatePostAppliesExplicitStatusAndPublishAt() {
        UUID id = UUID.randomUUID();
        Post updated = Post.create("New Title", "new-title", PostStatus.SCHEDULED,
                Instant.parse("2026-02-01T00:00:00Z"), "http://cover.png", "[]", "[]", null);
        when(updatePostUseCase.execute(any())).thenReturn(updated);

        UpdatePostRequest req = new UpdatePostRequest()
                .title("New Title")
                .coverUrl("http://cover.png")
                .status(UpdatePostRequest.StatusEnum.SCHEDULED)
                .publishAt(OffsetDateTime.of(2026, 2, 1, 0, 0, 0, 0, ZoneOffset.UTC));

        PostResponse dto = service.updatePost(id, req);

        ArgumentCaptor<UpdatePostUseCase.Input> captor =
                ArgumentCaptor.forClass(UpdatePostUseCase.Input.class);
        verify(updatePostUseCase).execute(captor.capture());
        UpdatePostUseCase.Input input = captor.getValue();
        assertThat(input.id()).isEqualTo(id.toString());
        assertThat(input.slug()).isEqualTo("new-title");
        assertThat(input.status()).isEqualTo(PostStatus.SCHEDULED);
        assertThat(input.publishAt()).isEqualTo(Instant.parse("2026-02-01T00:00:00Z"));
        assertThat(dto.getSlug()).isEqualTo("new-title");
    }

    @Test
    void updatePostLeavesStatusAndPublishAtNullWhenOmitted() {
        UUID id = UUID.randomUUID();
        Post updated = Post.create("Title", "title", PostStatus.DRAFT, null, null, "[]", "[]", null);
        when(updatePostUseCase.execute(any())).thenReturn(updated);

        UpdatePostRequest req = new UpdatePostRequest().title("Title").coverUrl("http://cover.png");

        service.updatePost(id, req);

        ArgumentCaptor<UpdatePostUseCase.Input> captor =
                ArgumentCaptor.forClass(UpdatePostUseCase.Input.class);
        verify(updatePostUseCase).execute(captor.capture());
        // Unlike create, omitted status/publishAt on update means "leave as-is" (null),
        // not a default value -- UpdatePostService is the one that falls back.
        assertThat(captor.getValue().status()).isNull();
        assertThat(captor.getValue().publishAt()).isNull();
    }

    @Test
    void getAdminCategoriesMapsAllFieldsIncludingCustomNameAndDisabled() {
        Category category = Category.reconstitute(UUID.randomUUID(), "podcasts", "Podcasts",
                "My Custom Name", null, "http://cover.png", "YOUTUBE", "PL1", false, 2, true, true,
                Instant.now(), Instant.now());
        when(getAdminCategoriesUseCase.execute())
                .thenReturn(new GetAdminCategoriesUseCase.Result(List.of(
                        new GetAdminCategoriesUseCase.CategoryWithCount(category, 7))));

        List<AdminCategoryResponse> result = service.getAdminCategories();

        assertThat(result).singleElement().satisfies(dto -> {
            assertThat(dto.getSlug()).isEqualTo("podcasts");
            assertThat(dto.getName()).isEqualTo("My Custom Name");
            assertThat(dto.getYoutubeName()).isEqualTo("Podcasts");
            assertThat(dto.getCustomName()).isEqualTo("My Custom Name");
            assertThat(dto.getCoverUrl()).isEqualTo("http://cover.png");
            assertThat(dto.getEnabled()).isFalse();
            assertThat(dto.getDefault()).isTrue();
            assertThat(dto.getDisplayOrder()).isEqualTo(2);
            assertThat(dto.getPostCount()).isEqualTo(7L);
        });
    }

    @Test
    void updateCategoryDelegatesRenameAndMapsPostCountFromAdminList() {
        UUID id = UUID.randomUUID();
        Category renamed = Category.reconstitute(id, "podcasts", "Podcasts", "Renamed",
                null, null, "YOUTUBE", "PL1", true, 0, false, false, Instant.now(), Instant.now());
        when(updateCategoryUseCase.execute(new UpdateCategoryUseCase.Input(id, "Renamed")))
                .thenReturn(renamed);
        when(getAdminCategoriesUseCase.execute())
                .thenReturn(new GetAdminCategoriesUseCase.Result(List.of(
                        new GetAdminCategoriesUseCase.CategoryWithCount(renamed, 9))));

        AdminCategoryResponse dto = service.updateCategory(id, new UpdateCategoryRequest().name("Renamed"));

        assertThat(dto.getName()).isEqualTo("Renamed");
        assertThat(dto.getPostCount()).isEqualTo(9L);
    }

    @Test
    void updateCategoryDefaultsPostCountToZeroWhenCategoryMissingFromAdminList() {
        UUID id = UUID.randomUUID();
        Category renamed = Category.reconstitute(id, "podcasts", "Podcasts", null,
                null, null, "YOUTUBE", "PL1", true, 0, false, false, Instant.now(), Instant.now());
        when(updateCategoryUseCase.execute(any())).thenReturn(renamed);
        when(getAdminCategoriesUseCase.execute())
                .thenReturn(new GetAdminCategoriesUseCase.Result(List.of()));

        AdminCategoryResponse dto = service.updateCategory(id, new UpdateCategoryRequest());

        assertThat(dto.getPostCount()).isEqualTo(0L);
    }

    @Test
    void reorderCategoriesDelegatesAndMapsPostCountsPerCategory() {
        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();
        Category cat1 = Category.reconstitute(id1, "podcasts", "Podcasts", null, null, null,
                "YOUTUBE", "PL1", true, 0, true, true, Instant.now(), Instant.now());
        Category cat2 = Category.reconstitute(id2, "events", "Events", null, null, null,
                "YOUTUBE", "PL2", true, 1, false, false, Instant.now(), Instant.now());
        when(getAdminCategoriesUseCase.execute())
                .thenReturn(new GetAdminCategoriesUseCase.Result(List.of(
                        new GetAdminCategoriesUseCase.CategoryWithCount(cat1, 3),
                        new GetAdminCategoriesUseCase.CategoryWithCount(cat2, 5))));
        when(reorderCategoriesUseCase.execute(new ReorderCategoriesUseCase.Input(List.of(id2, id1))))
                .thenReturn(new ReorderCategoriesUseCase.Result(List.of(cat2, cat1)));

        List<AdminCategoryResponse> result = service.reorderCategories(
                new ReorderCategoriesRequest(List.of(id2, id1)));

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getSlug()).isEqualTo("events");
        assertThat(result.get(0).getPostCount()).isEqualTo(5L);
        assertThat(result.get(1).getSlug()).isEqualTo("podcasts");
        assertThat(result.get(1).getPostCount()).isEqualTo(3L);
    }

    @Test
    void setDefaultCategoryDelegatesAndMapsPostCount() {
        UUID id = UUID.randomUUID();
        Category defaulted = Category.reconstitute(id, "podcasts", "Podcasts", null, null, null,
                "YOUTUBE", "PL1", true, 0, true, true, Instant.now(), Instant.now());
        when(setDefaultCategoryUseCase.execute(id)).thenReturn(defaulted);
        when(getAdminCategoriesUseCase.execute())
                .thenReturn(new GetAdminCategoriesUseCase.Result(List.of(
                        new GetAdminCategoriesUseCase.CategoryWithCount(defaulted, 4))));

        AdminCategoryResponse dto = service.setDefaultCategory(id);

        assertThat(dto.getDefault()).isTrue();
        assertThat(dto.getPostCount()).isEqualTo(4L);
    }

    /** A block value Jackson cannot serialize (getter always throws), to exercise blocksToJson's catch branch. */
    public static class ThrowingBean {
        public String getValue() {
            throw new IllegalStateException("boom");
        }
    }

    /** A SocialMediaLink whose getter always throws, to exercise socialLinksToJson's catch branch. */
    private static final class ThrowingSocialMediaLink extends SocialMediaLink {
        @Override
        public String getUrl() {
            throw new IllegalStateException("boom");
        }
    }

    @Test
    void blocksToJsonFallsBackToEmptyArrayWhenSerializationFails() {
        UUID createdBy = UUID.randomUUID();
        when(createPostUseCase.execute(any()))
                .thenReturn(Post.create("Ep", "ep", PostStatus.PUBLISHED, null, null, "[]", "[]", createdBy));

        CreatePostRequest req = new CreatePostRequest()
                .title("Ep")
                .blocks(List.of(Map.of("bad", new ThrowingBean())));

        service.createPost(req, createdBy);

        ArgumentCaptor<CreatePostUseCase.Input> captor =
                ArgumentCaptor.forClass(CreatePostUseCase.Input.class);
        verify(createPostUseCase).execute(captor.capture());
        assertThat(captor.getValue().blocksJson()).isEqualTo("[]");
    }

    @Test
    void socialLinksToJsonFallsBackToEmptyArrayWhenSerializationFails() {
        UUID createdBy = UUID.randomUUID();
        when(createPostUseCase.execute(any()))
                .thenReturn(Post.create("Ep", "ep", PostStatus.PUBLISHED, null, null, "[]", "[]", createdBy));

        CreatePostRequest req = new CreatePostRequest()
                .title("Ep")
                .socialMediaLinks(List.of(new ThrowingSocialMediaLink().platform("youtube")));

        service.createPost(req, createdBy);

        ArgumentCaptor<CreatePostUseCase.Input> captor =
                ArgumentCaptor.forClass(CreatePostUseCase.Input.class);
        verify(createPostUseCase).execute(captor.capture());
        assertThat(captor.getValue().socialMediaLinksJson()).isEqualTo("[]");
    }

    @Test
    void parseBlocksFallsBackToEmptyListWhenStoredJsonIsMalformed() {
        Post post = Post.create("Ep", "ep", PostStatus.PUBLISHED, null, null,
                "{not-valid-json", "[]", null);
        when(getPostBySlugUseCase.execute("ep")).thenReturn(Optional.of(post));

        PostResponse dto = service.getPostBySlug("ep");

        assertThat(dto.getBlocks()).isEmpty();
    }

    @Test
    void parseSocialLinksFallsBackToEmptyListWhenStoredJsonIsMalformed() {
        Post post = Post.create("Ep", "ep", PostStatus.PUBLISHED, null, null,
                "[]", "{not-valid-json", null);
        when(getPostBySlugUseCase.execute("ep")).thenReturn(Optional.of(post));

        PostResponse dto = service.getPostBySlug("ep");

        assertThat(dto.getSocialMediaLinks()).isEmpty();
    }
}
