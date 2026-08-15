package com.skateboard.podcast.adapter.out.persistence;

import com.skateboard.podcast.application.port.out.CategoryRepositoryPort;
import com.skateboard.podcast.application.port.out.PostCategoryPort;
import com.skateboard.podcast.domain.model.Category;
import com.skateboard.podcast.domain.model.Post;
import com.skateboard.podcast.domain.model.PostStatus;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Component
public class CategoryPersistenceAdapter implements CategoryRepositoryPort, PostCategoryPort {

    private final SpringCategoryRepository categoryRepository;
    private final SpringPostCategoryRepository postCategoryRepository;

    public CategoryPersistenceAdapter(SpringCategoryRepository categoryRepository,
                                      SpringPostCategoryRepository postCategoryRepository) {
        this.categoryRepository = categoryRepository;
        this.postCategoryRepository = postCategoryRepository;
    }

    // ── CategoryRepositoryPort ──────────────────────────────────────────────

    @Override
    public Optional<Category> findByExternalId(String source, String externalId) {
        return categoryRepository.findBySourceAndExternalId(source, externalId).map(this::toDomain);
    }

    @Override
    public Optional<Category> findBySlug(String slug) {
        return categoryRepository.findBySlug(slug).map(this::toDomain);
    }

    @Override
    public Category save(Category category) {
        return toDomain(categoryRepository.save(toEntity(category)));
    }

    @Override
    public List<Category> findAllEnabled() {
        return categoryRepository.findAllEnabledOrdered().stream().map(this::toDomain).toList();
    }

    @Override
    public List<Category> findAll() {
        return categoryRepository.findAll().stream().map(this::toDomain).toList();
    }

    // ── PostCategoryPort ─────────────────────────────────────────────────────

    @Override
    public Set<String> findVideoIdsByCategory(UUID categoryId) {
        return postCategoryRepository.findYoutubeVideoIdsByCategoryId(categoryId);
    }

    @Override
    public void addAssociation(UUID postId, UUID categoryId) {
        postCategoryRepository.save(new PostCategoryJpaEntity(postId, categoryId));
    }

    @Override
    public void removeAssociation(UUID postId, UUID categoryId) {
        postCategoryRepository.deleteAssociation(postId, categoryId);
    }

    @Override
    public List<Post> findPublishedByCategorySlug(String slug, int page, int size) {
        return postCategoryRepository
                .findByCategorySlugAndStatus(slug, PostStatus.PUBLISHED.name(), PageRequest.of(page, size))
                .stream().map(this::toDomainPost).toList();
    }

    @Override
    public long countPublishedByCategorySlug(String slug) {
        return postCategoryRepository.countByCategorySlugAndStatus(slug, PostStatus.PUBLISHED.name());
    }

    @Override
    public Map<UUID, Long> countPublishedByCategory() {
        Map<UUID, Long> counts = new HashMap<>();
        for (SpringPostCategoryRepository.CategoryPostCount row
                : postCategoryRepository.countByCategoryIdAndStatus(PostStatus.PUBLISHED.name())) {
            counts.put(row.getCategoryId(), row.getPostCount());
        }
        return counts;
    }

    // ── Mapping ──────────────────────────────────────────────────────────────

    private Category toDomain(CategoryJpaEntity e) {
        return Category.reconstitute(e.getId(), e.getSlug(), e.getName(), e.getDescription(), e.getCoverUrl(),
                e.getSource(), e.getExternalId(), e.isEnabled(), e.getDisplayOrder(), e.isDefault(),
                e.getCreatedAt(), e.getUpdatedAt());
    }

    private CategoryJpaEntity toEntity(Category category) {
        CategoryJpaEntity e = new CategoryJpaEntity();
        e.setId(category.getId());
        e.setSlug(category.getSlug());
        e.setName(category.getName());
        e.setDescription(category.getDescription());
        e.setCoverUrl(category.getCoverUrl());
        e.setSource(category.getSource());
        e.setExternalId(category.getExternalId());
        e.setEnabled(category.isEnabled());
        e.setDisplayOrder(category.getDisplayOrder());
        e.setDefault(category.isDefault());
        e.setCreatedAt(category.getCreatedAt());
        e.setUpdatedAt(category.getUpdatedAt());
        return e;
    }

    // Duplicated from PostPersistenceAdapter (which keeps this mapping
    // private) — same convention CLAUDE.md already documents for slug
    // generation across services.
    private Post toDomainPost(PostJpaEntity e) {
        return Post.reconstitute(
                e.getId(), e.getSlug(), e.getTitle(),
                PostStatus.valueOf(e.getStatus()),
                e.getPublishAt(), e.getCoverUrl(), e.getBlocksJson(),
                e.getSocialMediaLinksJson(),
                e.getCreatedAt(), e.getUpdatedAt(), e.getCreatedBy(),
                e.getYoutubeVideoId(), e.getDescription(), e.getDurationSeconds(), e.getEpisodeNumber()
        );
    }
}
