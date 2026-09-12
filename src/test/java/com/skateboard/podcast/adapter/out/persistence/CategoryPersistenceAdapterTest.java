package com.skateboard.podcast.adapter.out.persistence;

import com.skateboard.podcast.domain.model.Category;
import com.skateboard.podcast.domain.model.Post;
import com.skateboard.podcast.domain.model.PostStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Plain Mockito unit test — mocks the three Spring Data repos this adapter
 * fans out to, and verifies domain&lt;-&gt;entity mapping in both directions plus
 * the Optional/collection edge cases described in CategoryRepositoryPort and
 * PostCategoryPort.
 */
class CategoryPersistenceAdapterTest {

    @Mock private SpringCategoryRepository categoryRepository;
    @Mock private SpringPostCategoryRepository postCategoryRepository;
    @Mock private SpringPostPlatformLinkRepository platformLinkRepository;

    private CategoryPersistenceAdapter adapter;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        adapter = new CategoryPersistenceAdapter(categoryRepository, postCategoryRepository, platformLinkRepository);
    }

    private CategoryJpaEntity categoryEntity(UUID id, String slug, String name, boolean isDefault, boolean defaultLocked) {
        CategoryJpaEntity e = new CategoryJpaEntity();
        e.setId(id);
        e.setSlug(slug);
        e.setName(name);
        e.setCustomName(null);
        e.setDescription("desc-" + slug);
        e.setCoverUrl("https://example.com/" + slug + ".png");
        e.setSource("YOUTUBE");
        e.setExternalId("PL-" + slug);
        e.setEnabled(true);
        e.setDisplayOrder(1);
        e.setDefault(isDefault);
        e.setDefaultLocked(defaultLocked);
        e.setCreatedAt(Instant.parse("2024-01-01T00:00:00Z"));
        e.setUpdatedAt(Instant.parse("2024-01-02T00:00:00Z"));
        return e;
    }

    private PostJpaEntity postEntity(UUID id, String slug) {
        PostJpaEntity e = new PostJpaEntity();
        e.setId(id);
        e.setSlug(slug);
        e.setTitle("Title " + slug);
        e.setStatus(PostStatus.PUBLISHED.name());
        e.setBlocksJson("[]");
        e.setSocialMediaLinksJson("[]");
        e.setCreatedAt(Instant.parse("2024-01-01T00:00:00Z"));
        e.setUpdatedAt(Instant.parse("2024-01-02T00:00:00Z"));
        return e;
    }

    // ── findByExternalId ─────────────────────────────────────────────────────

    @Test
    void findByExternalIdMapsPresentEntityToDomain() {
        UUID id = UUID.randomUUID();
        when(categoryRepository.findBySourceAndExternalId("YOUTUBE", "PL1"))
                .thenReturn(Optional.of(categoryEntity(id, "podcasts", "Podcasts", false, false)));

        Optional<Category> result = adapter.findByExternalId("YOUTUBE", "PL1");

        assertThat(result).isPresent();
        assertThat(result.get().getId()).isEqualTo(id);
        assertThat(result.get().getSlug()).isEqualTo("podcasts");
        assertThat(result.get().getName()).isEqualTo("Podcasts");
        assertThat(result.get().getSource()).isEqualTo("YOUTUBE");
        assertThat(result.get().getExternalId()).isEqualTo("PL-podcasts");
    }

    @Test
    void findByExternalIdReturnsEmptyWhenNoMatch() {
        when(categoryRepository.findBySourceAndExternalId(anyString(), anyString())).thenReturn(Optional.empty());

        assertThat(adapter.findByExternalId("YOUTUBE", "missing")).isEmpty();
    }

    // ── findBySlug ───────────────────────────────────────────────────────────

    @Test
    void findBySlugMapsPresentEntityToDomain() {
        UUID id = UUID.randomUUID();
        when(categoryRepository.findBySlug("events")).thenReturn(Optional.of(categoryEntity(id, "events", "Events", true, true)));

        Optional<Category> result = adapter.findBySlug("events");

        assertThat(result).isPresent();
        assertThat(result.get().isDefault()).isTrue();
        assertThat(result.get().isDefaultLocked()).isTrue();
    }

    @Test
    void findBySlugReturnsEmptyWhenNoMatch() {
        when(categoryRepository.findBySlug("missing")).thenReturn(Optional.empty());

        assertThat(adapter.findBySlug("missing")).isEmpty();
    }

    // ── findById ─────────────────────────────────────────────────────────────

    @Test
    void findByIdMapsPresentEntityToDomain() {
        UUID id = UUID.randomUUID();
        when(categoryRepository.findById(id)).thenReturn(Optional.of(categoryEntity(id, "events", "Events", false, false)));

        Optional<Category> result = adapter.findById(id);

        assertThat(result).isPresent();
        assertThat(result.get().getId()).isEqualTo(id);
    }

    @Test
    void findByIdReturnsEmptyWhenNoMatch() {
        UUID id = UUID.randomUUID();
        when(categoryRepository.findById(id)).thenReturn(Optional.empty());

        assertThat(adapter.findById(id)).isEmpty();
    }

    // ── save ─────────────────────────────────────────────────────────────────

    @Test
    void saveMapsDomainToEntityAndBackWithAllFieldsRoundTripped() {
        Category category = Category.createFromYoutube("podcasts", "PL1", "Podcasts", "desc", "cover.png", true);
        category.rename("Custom Name");
        category.setDisplayOrder(5);

        // The repository "saves" and hands back a fresh entity, mirroring persistence.
        when(categoryRepository.save(any(CategoryJpaEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        Category saved = adapter.save(category);

        var captor = org.mockito.ArgumentCaptor.forClass(CategoryJpaEntity.class);
        verify(categoryRepository).save(captor.capture());
        CategoryJpaEntity passedEntity = captor.getValue();

        assertThat(passedEntity.getId()).isEqualTo(category.getId());
        assertThat(passedEntity.getSlug()).isEqualTo("podcasts");
        assertThat(passedEntity.getCustomName()).isEqualTo("Custom Name");
        assertThat(passedEntity.getDisplayOrder()).isEqualTo(5);
        assertThat(passedEntity.isDefault()).isTrue();

        assertThat(saved.getId()).isEqualTo(category.getId());
        assertThat(saved.getEffectiveName()).isEqualTo("Custom Name");
        assertThat(saved.getDisplayOrder()).isEqualTo(5);
    }

    // ── findAllEnabled / findAll ─────────────────────────────────────────────

    @Test
    void findAllEnabledMapsEachEntityAndPreservesOrder() {
        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();
        when(categoryRepository.findAllEnabledOrdered()).thenReturn(List.of(
                categoryEntity(id1, "a", "A", true, false),
                categoryEntity(id2, "b", "B", false, false)));

        List<Category> result = adapter.findAllEnabled();

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getSlug()).isEqualTo("a");
        assertThat(result.get(1).getSlug()).isEqualTo("b");
    }

    @Test
    void findAllEnabledReturnsEmptyListWhenNoneEnabled() {
        when(categoryRepository.findAllEnabledOrdered()).thenReturn(List.of());

        assertThat(adapter.findAllEnabled()).isEmpty();
    }

    @Test
    void findAllMapsEveryPersistedCategoryRegardlessOfEnabledFlag() {
        UUID id = UUID.randomUUID();
        CategoryJpaEntity disabled = categoryEntity(id, "old", "Old", false, false);
        disabled.setEnabled(false);
        when(categoryRepository.findAll()).thenReturn(List.of(disabled));

        List<Category> result = adapter.findAll();

        assertThat(result).singleElement().satisfies(c -> assertThat(c.isEnabled()).isFalse());
    }

    // ── findVideoIdsByCategory ───────────────────────────────────────────────

    @Test
    void findVideoIdsByCategoryDelegatesToRepository() {
        UUID categoryId = UUID.randomUUID();
        when(postCategoryRepository.findYoutubeVideoIdsByCategoryId(categoryId)).thenReturn(Set.of("yt-1", "yt-2"));

        assertThat(adapter.findVideoIdsByCategory(categoryId)).containsExactlyInAnyOrder("yt-1", "yt-2");
    }

    @Test
    void findVideoIdsByCategoryReturnsEmptySetWhenNoVideosLinked() {
        UUID categoryId = UUID.randomUUID();
        when(postCategoryRepository.findYoutubeVideoIdsByCategoryId(categoryId)).thenReturn(Set.of());

        assertThat(adapter.findVideoIdsByCategory(categoryId)).isEmpty();
    }

    // ── addAssociation / removeAssociation ───────────────────────────────────

    @Test
    void addAssociationSavesAPostCategoryEntityWithBothIds() {
        UUID postId = UUID.randomUUID();
        UUID categoryId = UUID.randomUUID();

        adapter.addAssociation(postId, categoryId);

        var captor = org.mockito.ArgumentCaptor.forClass(PostCategoryJpaEntity.class);
        verify(postCategoryRepository).save(captor.capture());
        assertThat(captor.getValue().getPostId()).isEqualTo(postId);
        assertThat(captor.getValue().getCategoryId()).isEqualTo(categoryId);
    }

    @Test
    void removeAssociationDelegatesToRepositoryDeleteWithBothIds() {
        UUID postId = UUID.randomUUID();
        UUID categoryId = UUID.randomUUID();

        adapter.removeAssociation(postId, categoryId);

        verify(postCategoryRepository).deleteAssociation(postId, categoryId);
    }

    // ── findPublishedByCategorySlug ──────────────────────────────────────────

    @Test
    void findPublishedByCategorySlugReturnsEmptyListWithoutQueryingLinksWhenNoPosts() {
        Page<PostJpaEntity> emptyPage = new PageImpl<>(List.of());
        when(postCategoryRepository.findByCategorySlugAndStatus(eq("empty-cat"), eq(PostStatus.PUBLISHED.name()), any()))
                .thenReturn(emptyPage);

        List<Post> result = adapter.findPublishedByCategorySlug("empty-cat", 0, 20);

        assertThat(result).isEmpty();
        verify(platformLinkRepository, never()).findByPostIdIn(any());
    }

    @Test
    void findPublishedByCategorySlugAttachesLinksOnlyToMatchingPostsAndDefaultsToEmptyForOthers() {
        UUID postWithLink = UUID.randomUUID();
        UUID postWithoutLink = UUID.randomUUID();
        Page<PostJpaEntity> page = new PageImpl<>(List.of(
                postEntity(postWithLink, "ep-1"),
                postEntity(postWithoutLink, "ep-2")));
        when(postCategoryRepository.findByCategorySlugAndStatus(eq("podcasts"), eq(PostStatus.PUBLISHED.name()), any()))
                .thenReturn(page);

        PostPlatformLinkJpaEntity link = new PostPlatformLinkJpaEntity();
        link.setId(UUID.randomUUID());
        link.setPostId(postWithLink);
        link.setPlatform("YOUTUBE");
        link.setExternalId("yt-1");
        link.setExternalUrl("https://youtube.com/watch?v=yt-1");
        when(platformLinkRepository.findByPostIdIn(List.of(postWithLink, postWithoutLink)))
                .thenReturn(List.of(link));

        List<Post> result = adapter.findPublishedByCategorySlug("podcasts", 0, 20);

        assertThat(result).hasSize(2);
        Post withLink = result.stream().filter(p -> p.getId().equals(postWithLink)).findFirst().orElseThrow();
        Post withoutLink = result.stream().filter(p -> p.getId().equals(postWithoutLink)).findFirst().orElseThrow();
        assertThat(withLink.getPlatformLinks()).hasSize(1);
        assertThat(withLink.getPlatformLinks().get(0).externalId()).isEqualTo("yt-1");
        assertThat(withoutLink.getPlatformLinks()).isEmpty();
    }

    @Test
    void findPublishedByCategorySlugUsesRequestedPageAndSize() {
        Page<PostJpaEntity> page = new PageImpl<>(List.of());
        when(postCategoryRepository.findByCategorySlugAndStatus(eq("podcasts"), eq(PostStatus.PUBLISHED.name()), eq(PageRequest.of(2, 10))))
                .thenReturn(page);

        adapter.findPublishedByCategorySlug("podcasts", 2, 10);

        verify(postCategoryRepository).findByCategorySlugAndStatus("podcasts", PostStatus.PUBLISHED.name(), PageRequest.of(2, 10));
    }

    // ── countPublishedByCategorySlug ─────────────────────────────────────────

    @Test
    void countPublishedByCategorySlugDelegatesToRepository() {
        when(postCategoryRepository.countByCategorySlugAndStatus("podcasts", PostStatus.PUBLISHED.name())).thenReturn(7L);

        assertThat(adapter.countPublishedByCategorySlug("podcasts")).isEqualTo(7L);
    }

    // ── countPublishedByCategory ─────────────────────────────────────────────

    @Test
    void countPublishedByCategoryBuildsMapFromProjectionRows() {
        UUID cat1 = UUID.randomUUID();
        UUID cat2 = UUID.randomUUID();
        SpringPostCategoryRepository.CategoryPostCount row1 = mock(SpringPostCategoryRepository.CategoryPostCount.class);
        when(row1.getCategoryId()).thenReturn(cat1);
        when(row1.getPostCount()).thenReturn(3L);
        SpringPostCategoryRepository.CategoryPostCount row2 = mock(SpringPostCategoryRepository.CategoryPostCount.class);
        when(row2.getCategoryId()).thenReturn(cat2);
        when(row2.getPostCount()).thenReturn(9L);
        when(postCategoryRepository.countByCategoryIdAndStatus(PostStatus.PUBLISHED.name())).thenReturn(List.of(row1, row2));

        Map<UUID, Long> result = adapter.countPublishedByCategory();

        assertThat(result).containsEntry(cat1, 3L).containsEntry(cat2, 9L).hasSize(2);
    }

    @Test
    void countPublishedByCategoryReturnsEmptyMapWhenNoPublishedPosts() {
        when(postCategoryRepository.countByCategoryIdAndStatus(PostStatus.PUBLISHED.name())).thenReturn(List.of());

        assertThat(adapter.countPublishedByCategory()).isEmpty();
    }
}
