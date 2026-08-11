package com.skateboard.podcast.adapter.out.persistence;

import com.skateboard.podcast.application.port.out.LoadPostPort;
import com.skateboard.podcast.application.port.out.SavePostPort;
import com.skateboard.podcast.domain.model.Post;
import com.skateboard.podcast.domain.model.PostStatus;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
public class PostPersistenceAdapter implements LoadPostPort, SavePostPort {

    private final SpringPostRepository jpaRepository;

    public PostPersistenceAdapter(SpringPostRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public Post save(Post post) {
        PostJpaEntity entity = toEntity(post);
        return toDomain(jpaRepository.save(entity));
    }

    @Override
    public Optional<Post> findById(String id) {
        return jpaRepository.findById(UUID.fromString(id)).map(this::toDomain);
    }

    @Override
    public Optional<Post> findBySlug(String slug) {
        return jpaRepository.findBySlug(slug).map(this::toDomain);
    }

    @Override
    public List<Post> findPublished(int page, int size) {
        // Ordering (COALESCE of publishAt/createdAt DESC) lives in the @Query,
        // so episodes sort by their real publish date, not import time.
        PageRequest pageable = PageRequest.of(page, size);
        return jpaRepository.findByStatusOrderByEffectivePublishDate(PostStatus.PUBLISHED.name(), pageable)
                .stream().map(this::toDomain).collect(Collectors.toList());
    }

    @Override
    public long countPublished() {
        return jpaRepository.countByStatus(PostStatus.PUBLISHED.name());
    }

    @Override
    public List<Post> findAll(int page, int size) {
        PageRequest pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        return jpaRepository.findAll(pageable).stream().map(this::toDomain).collect(Collectors.toList());
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

    private Post toDomain(PostJpaEntity e) {
        return Post.reconstitute(
                e.getId(), e.getSlug(), e.getTitle(),
                PostStatus.valueOf(e.getStatus()),
                e.getPublishAt(), e.getCoverUrl(), e.getBlocksJson(),
                e.getSocialMediaLinksJson(),
                e.getCreatedAt(), e.getUpdatedAt(), e.getCreatedBy()
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
        e.setBlocksJson(post.getBlocksJson() != null ? post.getBlocksJson() : "[]");
        e.setSocialMediaLinksJson(post.getSocialMediaLinksJson() != null ? post.getSocialMediaLinksJson() : "[]");
        e.setCreatedAt(post.getCreatedAt());
        e.setUpdatedAt(post.getUpdatedAt());
        e.setCreatedBy(post.getCreatedBy());
        return e;
    }
}
