package com.skateboard.podcast.adapter.out.persistence;

import com.skateboard.podcast.application.port.out.LoadPostPort;
import com.skateboard.podcast.application.port.out.SavePostPort;
import com.skateboard.podcast.domain.model.Post;
import com.skateboard.podcast.domain.model.PostPlatform;
import com.skateboard.podcast.domain.model.PostPlatformLink;
import com.skateboard.podcast.domain.model.PostStatus;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
public class PostPersistenceAdapter implements LoadPostPort, SavePostPort {

    private final SpringPostRepository jpaRepository;
    private final SpringPostPlatformLinkRepository platformLinkRepository;

    public PostPersistenceAdapter(SpringPostRepository jpaRepository,
                                  SpringPostPlatformLinkRepository platformLinkRepository) {
        this.jpaRepository = jpaRepository;
        this.platformLinkRepository = platformLinkRepository;
    }

    @Override
    @Transactional
    public Post save(Post post) {
        PostJpaEntity entity = toEntity(post);
        PostJpaEntity saved = jpaRepository.save(entity);
        platformLinkRepository.deleteByPostId(saved.getId());
        List<PostPlatformLinkJpaEntity> links = post.getPlatformLinks().stream()
                .map(link -> toLinkEntity(saved.getId(), link))
                .toList();
        if (!links.isEmpty()) {
            platformLinkRepository.saveAll(links);
        }
        return toDomain(saved, post.getPlatformLinks());
    }

    @Override
    public Optional<Post> findById(String id) {
        return jpaRepository.findById(UUID.fromString(id)).map(this::toDomainWithLinks);
    }

    @Override
    public Optional<Post> findBySlug(String slug) {
        return jpaRepository.findBySlug(slug).map(this::toDomainWithLinks);
    }

    @Override
    public Optional<Post> findByYoutubeVideoId(String youtubeVideoId) {
        return jpaRepository.findByYoutubeVideoId(youtubeVideoId).map(this::toDomainWithLinks);
    }

    @Override
    public List<Post> findPublished(int page, int size) {
        // Ordering (COALESCE of publishAt/createdAt DESC) lives in the @Query,
        // so episodes sort by their real publish date, not import time.
        PageRequest pageable = PageRequest.of(page, size);
        List<PostJpaEntity> entities = jpaRepository
                .findByStatusOrderByEffectivePublishDate(PostStatus.PUBLISHED.name(), pageable)
                .getContent();
        return toDomainWithLinks(entities);
    }

    @Override
    public long countPublished() {
        return jpaRepository.countByStatus(PostStatus.PUBLISHED.name());
    }

    @Override
    public List<Post> searchPublished(String query, int page, int size) {
        PageRequest pageable = PageRequest.of(page, size);
        List<PostJpaEntity> entities = jpaRepository
                .searchByStatusAndTitle(PostStatus.PUBLISHED.name(), query, pageable)
                .getContent();
        return toDomainWithLinks(entities);
    }

    @Override
    public long countSearchPublished(String query) {
        // @Query-backed Page methods derive their count from a separate
        // COUNT query, so this doesn't load the matching rows themselves —
        // the page size here only bounds the (unused) content list.
        return jpaRepository.searchByStatusAndTitle(PostStatus.PUBLISHED.name(), query, PageRequest.of(0, 1)).getTotalElements();
    }

    @Override
    public List<Post> findAll(int page, int size) {
        PageRequest pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        List<PostJpaEntity> entities = jpaRepository.findAll(pageable).getContent();
        return toDomainWithLinks(entities);
    }

    @Override
    public long countAll() {
        return jpaRepository.count();
    }

    @Override
    public void deleteById(String id) {
        jpaRepository.deleteById(UUID.fromString(id));
    }

    @Override
    public boolean existsBySlug(String slug) {
        return jpaRepository.existsBySlug(slug);
    }

    @Override
    public List<Post> findPublishedAwaitingNotification(Instant publishedAfter, int limit) {
        return toDomainWithLinks(jpaRepository
                .findAwaitingNotification(PostStatus.PUBLISHED.name(), publishedAfter,
                        PageRequest.of(0, limit))
                .getContent());
    }

    // Batches the platform-link lookup into one query per page instead of one per post.
    private List<Post> toDomainWithLinks(List<PostJpaEntity> entities) {
        List<UUID> ids = entities.stream().map(PostJpaEntity::getId).toList();
        Map<UUID, List<PostPlatformLink>> linksByPostId = loadLinksByPostId(ids);
        return entities.stream()
                .map(e -> toDomain(e, linksByPostId.getOrDefault(e.getId(), List.of())))
                .collect(Collectors.toList());
    }

    private Post toDomainWithLinks(PostJpaEntity e) {
        return toDomain(e, platformLinkRepository.findByPostId(e.getId()).stream()
                .map(this::toLink)
                .toList());
    }

    Map<UUID, List<PostPlatformLink>> loadLinksByPostId(List<UUID> postIds) {
        if (postIds.isEmpty()) return Map.of();
        return platformLinkRepository.findByPostIdIn(postIds).stream()
                .collect(Collectors.groupingBy(PostPlatformLinkJpaEntity::getPostId,
                        Collectors.mapping(this::toLink, Collectors.toList())));
    }

    private PostPlatformLink toLink(PostPlatformLinkJpaEntity e) {
        return new PostPlatformLink(PostPlatform.valueOf(e.getPlatform()), e.getExternalId(), e.getExternalUrl());
    }

    private PostPlatformLinkJpaEntity toLinkEntity(UUID postId, PostPlatformLink link) {
        PostPlatformLinkJpaEntity e = new PostPlatformLinkJpaEntity();
        e.setId(UUID.randomUUID());
        e.setPostId(postId);
        e.setPlatform(link.platform().name());
        e.setExternalId(link.externalId());
        e.setExternalUrl(link.externalUrl());
        Instant now = Instant.now();
        e.setCreatedAt(now);
        e.setUpdatedAt(now);
        return e;
    }

    private Post toDomain(PostJpaEntity e, List<PostPlatformLink> platformLinks) {
        return Post.reconstitute(
                e.getId(), e.getSlug(), e.getTitle(),
                PostStatus.valueOf(e.getStatus()),
                e.getPublishAt(), e.getCoverUrl(), e.getCoverWidth(), e.getCoverHeight(),
                e.getBlocksJson(),
                e.getSocialMediaLinksJson(),
                e.getCreatedAt(), e.getUpdatedAt(), e.getCreatedBy(),
                e.getYoutubeVideoId(), e.getDescription(), e.getDurationSeconds(), e.getEpisodeNumber(),
                e.getNotifiedAt(), platformLinks
        );
    }

    private PostJpaEntity toEntity(Post post) {
        PostJpaEntity e = new PostJpaEntity();
        e.setId(post.getId());
        e.setSlug(post.getSlug());
        e.setTitle(post.getTitle());
        e.setStatus(post.getStatus().name());
        e.setPublishAt(post.getPublishAt());
        e.setCoverUrl(post.getCoverUrl());
        e.setCoverWidth(post.getCoverWidth());
        e.setCoverHeight(post.getCoverHeight());
        e.setBlocksJson(post.getBlocksJson() != null ? post.getBlocksJson() : "[]");
        e.setSocialMediaLinksJson(post.getSocialMediaLinksJson() != null ? post.getSocialMediaLinksJson() : "[]");
        e.setCreatedAt(post.getCreatedAt());
        e.setUpdatedAt(post.getUpdatedAt());
        e.setCreatedBy(post.getCreatedBy());
        e.setYoutubeVideoId(post.getYoutubeVideoId());
        e.setDescription(post.getDescription());
        e.setDurationSeconds(post.getDurationSeconds());
        e.setEpisodeNumber(post.getEpisodeNumber());
        e.setNotifiedAt(post.getNotifiedAt());
        return e;
    }
}
