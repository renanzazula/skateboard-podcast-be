package com.skateboard.podcast.adapter.out.persistence;

import com.skateboard.podcast.domain.model.Post;
import com.skateboard.podcast.domain.model.PostPlatform;
import com.skateboard.podcast.domain.model.PostPlatformLink;
import com.skateboard.podcast.domain.model.PostStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Plain Mockito unit test for PostPersistenceAdapter, mocking SpringPostRepository
 * and SpringPostPlatformLinkRepository. Complements PostPlatformLinkPersistenceIntegrationTest
 * (real Postgres round-trip) by covering branches that don't need a real database:
 * absent/present Optionals, empty vs. batched platform-link lookups, multiple links per
 * post, and the blocksJson/socialMediaLinksJson null-defaulting on save.
 */
class PostPersistenceAdapterTest {

    @Mock private SpringPostRepository jpaRepository;
    @Mock private SpringPostPlatformLinkRepository platformLinkRepository;

    private PostPersistenceAdapter adapter;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        adapter = new PostPersistenceAdapter(jpaRepository, platformLinkRepository);
    }

    private PostJpaEntity postEntity(UUID id, String slug, String status) {
        PostJpaEntity e = new PostJpaEntity();
        e.setId(id);
        e.setSlug(slug);
        e.setTitle("Title " + slug);
        e.setStatus(status);
        e.setBlocksJson("[]");
        e.setSocialMediaLinksJson("[]");
        e.setCreatedAt(Instant.parse("2024-01-01T00:00:00Z"));
        e.setUpdatedAt(Instant.parse("2024-01-02T00:00:00Z"));
        return e;
    }

    private PostPlatformLinkJpaEntity linkEntity(UUID postId, String platform, String externalId) {
        PostPlatformLinkJpaEntity e = new PostPlatformLinkJpaEntity();
        e.setId(UUID.randomUUID());
        e.setPostId(postId);
        e.setPlatform(platform);
        e.setExternalId(externalId);
        e.setExternalUrl("https://example.com/" + externalId);
        e.setCreatedAt(Instant.now());
        e.setUpdatedAt(Instant.now());
        return e;
    }

    // ── save ─────────────────────────────────────────────────────────────────

    @Test
    void saveDeletesOldLinksAndPersistsNewOnesWhenPostHasPlatformLinks() {
        Post post = Post.create("EP 1", "ep-1", PostStatus.PUBLISHED, null, null, "[]", "[]", null);
        post.attachPlatformLink(new PostPlatformLink(PostPlatform.YOUTUBE, "yt-1", "https://youtube.com/yt-1"));
        post.attachPlatformLink(new PostPlatformLink(PostPlatform.SPOTIFY, "sp-1", "https://spotify.com/sp-1"));

        PostJpaEntity saved = postEntity(post.getId(), post.getSlug(), post.getStatus().name());
        when(jpaRepository.save(any(PostJpaEntity.class))).thenReturn(saved);

        Post result = adapter.save(post);

        verify(platformLinkRepository).deleteByPostId(post.getId());
        ArgumentCaptor<List<PostPlatformLinkJpaEntity>> captor = ArgumentCaptor.forClass(List.class);
        verify(platformLinkRepository).saveAll(captor.capture());
        List<PostPlatformLinkJpaEntity> savedLinks = captor.getValue();
        assertThat(savedLinks).hasSize(2);
        assertThat(savedLinks).allSatisfy(l -> assertThat(l.getPostId()).isEqualTo(post.getId()));
        assertThat(savedLinks).extracting(PostPlatformLinkJpaEntity::getPlatform)
                .containsExactlyInAnyOrder("YOUTUBE", "SPOTIFY");
        assertThat(savedLinks).allSatisfy(l -> {
            assertThat(l.getId()).isNotNull();
            assertThat(l.getCreatedAt()).isNotNull();
            assertThat(l.getUpdatedAt()).isNotNull();
        });

        // save() returns the domain post built from the *submitted* links, not a reload.
        assertThat(result.getPlatformLinks()).hasSize(2);
    }

    @Test
    void saveDoesNotCallSaveAllWhenPostHasNoPlatformLinks() {
        Post post = Post.create("EP 2", "ep-2", PostStatus.DRAFT, null, null, "[]", "[]", null);
        PostJpaEntity saved = postEntity(post.getId(), post.getSlug(), post.getStatus().name());
        when(jpaRepository.save(any(PostJpaEntity.class))).thenReturn(saved);

        Post result = adapter.save(post);

        verify(platformLinkRepository).deleteByPostId(post.getId());
        verify(platformLinkRepository, never()).saveAll(any());
        assertThat(result.getPlatformLinks()).isEmpty();
    }

    @Test
    void saveDefaultsNullBlocksJsonAndSocialMediaLinksJsonToEmptyArray() {
        Post post = Post.create("EP 3", "ep-3", PostStatus.DRAFT, null, null, null, null, null);
        PostJpaEntity saved = postEntity(post.getId(), post.getSlug(), post.getStatus().name());
        when(jpaRepository.save(any(PostJpaEntity.class))).thenReturn(saved);

        adapter.save(post);

        ArgumentCaptor<PostJpaEntity> captor = ArgumentCaptor.forClass(PostJpaEntity.class);
        verify(jpaRepository).save(captor.capture());
        // Post.create already defaults socialMediaLinksJson to "[]" when null; blocksJson stays
        // whatever was passed (null here), and toEntity is what defaults it at the persistence boundary.
        assertThat(captor.getValue().getBlocksJson()).isEqualTo("[]");
        assertThat(captor.getValue().getSocialMediaLinksJson()).isEqualTo("[]");
    }

    @Test
    void savePassesThroughNonNullBlocksJsonAndSocialMediaLinksJsonUnchanged() {
        Post post = Post.create("EP 4", "ep-4", PostStatus.DRAFT, null, null,
                "[{\"type\":\"text\"}]", "[{\"platform\":\"instagram\"}]", null);
        PostJpaEntity saved = postEntity(post.getId(), post.getSlug(), post.getStatus().name());
        when(jpaRepository.save(any(PostJpaEntity.class))).thenReturn(saved);

        adapter.save(post);

        ArgumentCaptor<PostJpaEntity> captor = ArgumentCaptor.forClass(PostJpaEntity.class);
        verify(jpaRepository).save(captor.capture());
        assertThat(captor.getValue().getBlocksJson()).isEqualTo("[{\"type\":\"text\"}]");
        assertThat(captor.getValue().getSocialMediaLinksJson()).isEqualTo("[{\"platform\":\"instagram\"}]");
    }

    // ── findById / findBySlug / findByYoutubeVideoId ────────────────────────

    @Test
    void findByIdMapsPresentEntityWithItsLinks() {
        UUID id = UUID.randomUUID();
        PostJpaEntity entity = postEntity(id, "ep-5", PostStatus.PUBLISHED.name());
        when(jpaRepository.findById(id)).thenReturn(Optional.of(entity));
        when(platformLinkRepository.findByPostId(id)).thenReturn(List.of(linkEntity(id, "YOUTUBE", "yt-5")));

        Optional<Post> result = adapter.findById(id.toString());

        assertThat(result).isPresent();
        assertThat(result.get().getPlatformLinks()).hasSize(1);
        assertThat(result.get().getPlatformLinks().get(0).platform()).isEqualTo(PostPlatform.YOUTUBE);
    }

    @Test
    void findByIdReturnsEmptyWhenNotFound() {
        UUID id = UUID.randomUUID();
        when(jpaRepository.findById(id)).thenReturn(Optional.empty());

        assertThat(adapter.findById(id.toString())).isEmpty();
        verify(platformLinkRepository, never()).findByPostId(any());
    }

    @Test
    void findBySlugMapsPresentEntityWithNoLinksWhenNoneExist() {
        UUID id = UUID.randomUUID();
        PostJpaEntity entity = postEntity(id, "ep-6", PostStatus.DRAFT.name());
        when(jpaRepository.findBySlug("ep-6")).thenReturn(Optional.of(entity));
        when(platformLinkRepository.findByPostId(id)).thenReturn(List.of());

        Optional<Post> result = adapter.findBySlug("ep-6");

        assertThat(result).isPresent();
        assertThat(result.get().getPlatformLinks()).isEmpty();
    }

    @Test
    void findBySlugReturnsEmptyWhenNotFound() {
        when(jpaRepository.findBySlug("missing")).thenReturn(Optional.empty());

        assertThat(adapter.findBySlug("missing")).isEmpty();
    }

    @Test
    void findByYoutubeVideoIdMapsPresentEntity() {
        UUID id = UUID.randomUUID();
        PostJpaEntity entity = postEntity(id, "ep-7", PostStatus.PUBLISHED.name());
        entity.setYoutubeVideoId("yt-video-7");
        when(jpaRepository.findByYoutubeVideoId("yt-video-7")).thenReturn(Optional.of(entity));
        when(platformLinkRepository.findByPostId(id)).thenReturn(List.of());

        Optional<Post> result = adapter.findByYoutubeVideoId("yt-video-7");

        assertThat(result).isPresent();
        assertThat(result.get().getYoutubeVideoId()).isEqualTo("yt-video-7");
    }

    @Test
    void findByYoutubeVideoIdReturnsEmptyWhenNotFound() {
        when(jpaRepository.findByYoutubeVideoId("missing")).thenReturn(Optional.empty());

        assertThat(adapter.findByYoutubeVideoId("missing")).isEmpty();
    }

    // ── findPublished / countPublished ──────────────────────────────────────

    @Test
    void findPublishedReturnsEmptyListWithoutQueryingLinksWhenPageIsEmpty() {
        when(jpaRepository.findByStatusOrderByEffectivePublishDate(eq(PostStatus.PUBLISHED.name()), any()))
                .thenReturn(new PageImpl<>(List.of()));

        List<Post> result = adapter.findPublished(0, 20);

        assertThat(result).isEmpty();
        verify(platformLinkRepository, never()).findByPostIdIn(any());
    }

    @Test
    void findPublishedBatchesLinkLookupAndDefaultsMissingLinksToEmptyList() {
        UUID postWithLinks = UUID.randomUUID();
        UUID postWithoutLinks = UUID.randomUUID();
        Page<PostJpaEntity> page = new PageImpl<>(List.of(
                postEntity(postWithLinks, "ep-8", PostStatus.PUBLISHED.name()),
                postEntity(postWithoutLinks, "ep-9", PostStatus.PUBLISHED.name())));
        when(jpaRepository.findByStatusOrderByEffectivePublishDate(eq(PostStatus.PUBLISHED.name()), any()))
                .thenReturn(page);
        when(platformLinkRepository.findByPostIdIn(List.of(postWithLinks, postWithoutLinks)))
                .thenReturn(List.of(
                        linkEntity(postWithLinks, "YOUTUBE", "yt-8"),
                        linkEntity(postWithLinks, "SPOTIFY", "sp-8")));

        List<Post> result = adapter.findPublished(0, 20);

        assertThat(result).hasSize(2);
        Post withLinks = result.stream().filter(p -> p.getId().equals(postWithLinks)).findFirst().orElseThrow();
        Post withoutLinks = result.stream().filter(p -> p.getId().equals(postWithoutLinks)).findFirst().orElseThrow();
        assertThat(withLinks.getPlatformLinks()).hasSize(2);
        assertThat(withLinks.getPlatformLinks()).extracting(PostPlatformLink::platform)
                .containsExactlyInAnyOrder(PostPlatform.YOUTUBE, PostPlatform.SPOTIFY);
        assertThat(withoutLinks.getPlatformLinks()).isEmpty();
    }

    @Test
    void findPublishedUsesRequestedPageAndSize() {
        when(jpaRepository.findByStatusOrderByEffectivePublishDate(eq(PostStatus.PUBLISHED.name()), eq(PageRequest.of(1, 5))))
                .thenReturn(new PageImpl<>(List.of()));

        adapter.findPublished(1, 5);

        verify(jpaRepository).findByStatusOrderByEffectivePublishDate(PostStatus.PUBLISHED.name(), PageRequest.of(1, 5));
    }

    @Test
    void countPublishedDelegatesToRepository() {
        when(jpaRepository.countByStatus(PostStatus.PUBLISHED.name())).thenReturn(42L);

        assertThat(adapter.countPublished()).isEqualTo(42L);
    }

    // ── searchPublished / countSearchPublished ──────────────────────────────

    @Test
    void searchPublishedMapsEntitiesWithLinks() {
        UUID id = UUID.randomUUID();
        when(jpaRepository.searchByStatusAndTitle(eq(PostStatus.PUBLISHED.name()), eq("skate"), any()))
                .thenReturn(new PageImpl<>(List.of(postEntity(id, "ep-10", PostStatus.PUBLISHED.name()))));
        when(platformLinkRepository.findByPostIdIn(List.of(id))).thenReturn(List.of());

        List<Post> result = adapter.searchPublished("skate", 0, 10);

        assertThat(result).singleElement().satisfies(p -> assertThat(p.getSlug()).isEqualTo("ep-10"));
    }

    @Test
    void countSearchPublishedUsesASinglePagePageableAndReturnsTotalElementsNotContentSize() {
        Page<PostJpaEntity> page = new PageImpl<>(List.of(postEntity(UUID.randomUUID(), "ep-11", PostStatus.PUBLISHED.name())),
                PageRequest.of(0, 1), 37);
        when(jpaRepository.searchByStatusAndTitle(PostStatus.PUBLISHED.name(), "skate", PageRequest.of(0, 1)))
                .thenReturn(page);

        long count = adapter.countSearchPublished("skate");

        assertThat(count).isEqualTo(37L);
        verify(jpaRepository).searchByStatusAndTitle(PostStatus.PUBLISHED.name(), "skate", PageRequest.of(0, 1));
    }

    // ── findAll / countAll ───────────────────────────────────────────────────

    @Test
    void findAllOrdersByCreatedAtDescendingAndMapsLinks() {
        UUID id = UUID.randomUUID();
        Page<PostJpaEntity> page = new PageImpl<>(List.of(postEntity(id, "ep-12", PostStatus.DRAFT.name())));
        when(jpaRepository.findAll(eq(PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "createdAt")))))
                .thenReturn(page);
        when(platformLinkRepository.findByPostIdIn(List.of(id))).thenReturn(List.of());

        List<Post> result = adapter.findAll(0, 20);

        assertThat(result).singleElement().satisfies(p -> assertThat(p.getId()).isEqualTo(id));
        verify(jpaRepository).findAll(PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "createdAt")));
    }

    @Test
    void countAllDelegatesToRepository() {
        when(jpaRepository.count()).thenReturn(99L);

        assertThat(adapter.countAll()).isEqualTo(99L);
    }

    // ── deleteById ───────────────────────────────────────────────────────────

    @Test
    void deleteByIdRemovesPlatformLinksBeforeDeletingThePost() {
        UUID id = UUID.randomUUID();

        adapter.deleteById(id.toString());

        var inOrder = org.mockito.Mockito.inOrder(platformLinkRepository, jpaRepository);
        inOrder.verify(platformLinkRepository).deleteByPostId(id);
        inOrder.verify(jpaRepository).deleteById(id);
    }

    // ── existsBySlug ─────────────────────────────────────────────────────────

    @Test
    void existsBySlugDelegatesToRepositoryTrue() {
        when(jpaRepository.existsBySlug("ep-13")).thenReturn(true);

        assertThat(adapter.existsBySlug("ep-13")).isTrue();
    }

    @Test
    void existsBySlugDelegatesToRepositoryFalse() {
        when(jpaRepository.existsBySlug("missing")).thenReturn(false);

        assertThat(adapter.existsBySlug("missing")).isFalse();
    }

    // ── findPublishedAwaitingNotification ────────────────────────────────────

    @Test
    void findPublishedAwaitingNotificationPassesStatusCutoffAndLimitThrough() {
        Instant cutoff = Instant.parse("2024-06-01T00:00:00Z");
        UUID id = UUID.randomUUID();
        when(jpaRepository.findAwaitingNotification(PostStatus.PUBLISHED.name(), cutoff, PageRequest.of(0, 20)))
                .thenReturn(new PageImpl<>(List.of(postEntity(id, "ep-14", PostStatus.PUBLISHED.name()))));
        when(platformLinkRepository.findByPostIdIn(List.of(id))).thenReturn(List.of());

        List<Post> result = adapter.findPublishedAwaitingNotification(cutoff, 20);

        assertThat(result).singleElement().satisfies(p -> assertThat(p.getId()).isEqualTo(id));
        verify(jpaRepository).findAwaitingNotification(PostStatus.PUBLISHED.name(), cutoff, PageRequest.of(0, 20));
    }

    @Test
    void findPublishedAwaitingNotificationReturnsEmptyWhenNoneAreOwed() {
        Instant cutoff = Instant.parse("2024-06-01T00:00:00Z");
        when(jpaRepository.findAwaitingNotification(PostStatus.PUBLISHED.name(), cutoff, PageRequest.of(0, 20)))
                .thenReturn(new PageImpl<>(List.of()));

        assertThat(adapter.findPublishedAwaitingNotification(cutoff, 20)).isEmpty();
    }

    // ── findLatestPublishedYoutubePosts ──────────────────────────────────────

    @Test
    void findLatestPublishedYoutubePostsPassesLimitThroughAndMapsLinks() {
        UUID id = UUID.randomUUID();
        PostJpaEntity entity = postEntity(id, "ep-15", PostStatus.PUBLISHED.name());
        entity.setYoutubeVideoId("yt-15");
        when(jpaRepository.findLatestByStatusAndYoutubeVideoIdNotNull(PostStatus.PUBLISHED.name(), PageRequest.of(0, 5)))
                .thenReturn(new PageImpl<>(List.of(entity)));
        when(platformLinkRepository.findByPostIdIn(List.of(id))).thenReturn(List.of());

        List<Post> result = adapter.findLatestPublishedYoutubePosts(5);

        assertThat(result).singleElement().satisfies(p -> assertThat(p.getYoutubeVideoId()).isEqualTo("yt-15"));
        verify(jpaRepository).findLatestByStatusAndYoutubeVideoIdNotNull(PostStatus.PUBLISHED.name(), PageRequest.of(0, 5));
    }

    // ── loadLinksByPostId (package-private helper, exercised directly too) ──

    @Test
    void loadLinksByPostIdReturnsEmptyMapWithoutQueryingWhenIdListIsEmpty() {
        Map<UUID, List<PostPlatformLink>> result = adapter.loadLinksByPostId(List.of());

        assertThat(result).isEmpty();
        verify(platformLinkRepository, never()).findByPostIdIn(any());
    }

    @Test
    void loadLinksByPostIdGroupsMultipleLinksUnderTheSamePost() {
        UUID postId = UUID.randomUUID();
        when(platformLinkRepository.findByPostIdIn(List.of(postId))).thenReturn(List.of(
                linkEntity(postId, "YOUTUBE", "yt-16"),
                linkEntity(postId, "SPOTIFY", "sp-16")));

        Map<UUID, List<PostPlatformLink>> result = adapter.loadLinksByPostId(List.of(postId));

        assertThat(result).hasSize(1);
        assertThat(result.get(postId)).hasSize(2);
        assertThat(result.get(postId)).extracting(PostPlatformLink::platform)
                .containsExactlyInAnyOrder(PostPlatform.YOUTUBE, PostPlatform.SPOTIFY);
    }
}
