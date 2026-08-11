package com.skateboard.podcast.adapter.in.rest;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.skateboard.application.dto.*;
import com.skateboard.podcast.application.port.in.*;
import com.skateboard.podcast.domain.model.Post;
import com.skateboard.podcast.domain.model.PostStatus;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.stereotype.Service;

import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Cached facade over the podcast use cases. Podcast content is global
 * (no tenant/user/language scoping), so cache entries are safe to share
 * across authenticated users; authorization stays on the controller via
 * {@code @PreAuthorize} and runs on every request.
 */
@Service
public class PodcastService {

    public static final String POST_CACHE = "podcast-post";

    private final CreatePostUseCase createPostUseCase;
    private final GetPostUseCase getPostUseCase;
    private final GetPostBySlugUseCase getPostBySlugUseCase;
    private final UpdatePostUseCase updatePostUseCase;
    private final DeletePostUseCase deletePostUseCase;
    private final ImportPostsUseCase importPostsUseCase;
    private final ObjectMapper objectMapper;

    public PodcastService(CreatePostUseCase createPostUseCase,
                          GetPostUseCase getPostUseCase,
                          GetPostBySlugUseCase getPostBySlugUseCase,
                          UpdatePostUseCase updatePostUseCase,
                          DeletePostUseCase deletePostUseCase,
                          ImportPostsUseCase importPostsUseCase,
                          ObjectMapper objectMapper) {
        this.createPostUseCase = createPostUseCase;
        this.getPostUseCase = getPostUseCase;
        this.getPostBySlugUseCase = getPostBySlugUseCase;
        this.updatePostUseCase = updatePostUseCase;
        this.deletePostUseCase = deletePostUseCase;
        this.importPostsUseCase = importPostsUseCase;
        this.objectMapper = objectMapper;
    }

    // ── Reads (cached) ──────────────────────────────────────────────────────

    @Cacheable(cacheNames = POST_CACHE, key = "#page + ':' + #size", sync = true)
    public FeedPageResponse getPost(int page, int size) {
        GetPostUseCase.Result result = getPostUseCase.execute(page, size);
        return new FeedPageResponse()
                .posts(result.posts().stream()
                        .map(this::toDto)
                        .toList())
                .total(result.total())
                .page(page)
                .size(size);
    }

    /** Returns {@code null} when no post matches; the controller maps null to 404. */
    @Cacheable(cacheNames = POST_CACHE, key = "#slug", unless = "#result == null")
    public PostResponse getPostBySlug(String slug) {
        return getPostBySlugUseCase.execute(slug)
                .map(this::toDto)
                .orElse(null);
    }

    // ── Mutations (evict both caches; update may change the slug, so
    //    key-targeted eviction is not enough) ────────────────────────────────

    @Caching(evict = {@CacheEvict(cacheNames = POST_CACHE, allEntries = true)})
    public PostResponse createPost(CreatePostRequest req, UUID createdBy) {
        PostStatus status = req.getStatus() != null
                ? PostStatus.valueOf(req.getStatus().getValue().toUpperCase())
                : PostStatus.PUBLISHED;
        Post post = createPostUseCase.execute(new CreatePostUseCase.Input(
                req.getTitle(), generateSlug(req.getTitle()), status,
                req.getPublishAt() != null ? req.getPublishAt().toInstant() : null,
                req.getCoverUrl(), blocksToJson(req.getBlocks()),
                socialLinksToJson(req.getSocialMediaLinks()), createdBy));
        return toDto(post);
    }

    @Caching(evict = {
            @CacheEvict(cacheNames = POST_CACHE, allEntries = true)})
    public PostResponse updatePost(UUID id, UpdatePostRequest req) {
        // Unlike creation, omitted status on update means "leave it as-is" —
        // UpdatePostService falls back to the post's current status when null.
        PostStatus status = req.getStatus() != null
                ? PostStatus.valueOf(req.getStatus().getValue().toUpperCase())
                : null;
        Post post = updatePostUseCase.execute(new UpdatePostUseCase.Input(
                id.toString(), req.getTitle(), generateSlug(req.getTitle()), status,
                req.getPublishAt() != null ? req.getPublishAt().toInstant() : null,
                req.getCoverUrl(), blocksToJson(req.getBlocks()),
                socialLinksToJson(req.getSocialMediaLinks())));
        return toDto(post);
    }

    @Caching(evict = {@CacheEvict(cacheNames = POST_CACHE, allEntries = true)})
    public void deletePost(UUID id) {
        deletePostUseCase.execute(id.toString());
    }

    @Caching(evict = {@CacheEvict(cacheNames = POST_CACHE, allEntries = true)})
    public ImportResult importPosts(ImportPostsRequest req, UUID importedBy) {
        List<ImportPostsUseCase.PostImportItem> items = req.getPosts().stream()
                .map(p -> new ImportPostsUseCase.PostImportItem(
                        p.getTitle(),
                        p.getCoverUrl(),
                        p.getStatus() != null ? p.getStatus().getValue() : null,
                        p.getPublishAt() != null ? p.getPublishAt().toInstant().toString() : null,
                        blocksToJson(p.getBlocks()),
                        socialLinksToJson(p.getSocialMediaLinks())))
                .toList();
        ImportPostsUseCase.Result result = importPostsUseCase.execute(
                new ImportPostsUseCase.Input(items, importedBy));
        return new ImportResult()
                .imported(result.imported())
                .failed(result.failed())
                .errors(result.errors());
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private PostResponse toDto(Post post) {
        return new PostResponse()
                .id(post.getId())
                .slug(post.getSlug())
                .title(post.getTitle())
                .status(PostResponse.StatusEnum.fromValue(post.getStatus().name().toLowerCase()))
                .publishAt(post.getPublishAt() != null ? post.getPublishAt().atOffset(ZoneOffset.UTC) : null)
                .coverUrl(post.getCoverUrl())
                .blocks(parseBlocks(post.getBlocksJson()))
                .socialMediaLinks(parseSocialLinks(post.getSocialMediaLinksJson()))
                .createdAt(post.getCreatedAt().atOffset(ZoneOffset.UTC))
                .updatedAt(post.getUpdatedAt().atOffset(ZoneOffset.UTC))
                .createdBy(post.getCreatedBy());
    }

    private String blocksToJson(List<Map<String, Object>> blocks) {
        if (blocks == null || blocks.isEmpty()) return "[]";
        try {
            return objectMapper.writeValueAsString(blocks);
        } catch (Exception e) {
            return "[]";
        }
    }

    private List<Map<String, Object>> parseBlocks(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (Exception e) {
            return List.of();
        }
    }

    private String socialLinksToJson(List<SocialMediaLink> links) {
        if (links == null || links.isEmpty()) return "[]";
        try {
            return objectMapper.writeValueAsString(links);
        } catch (Exception e) {
            return "[]";
        }
    }

    private List<SocialMediaLink> parseSocialLinks(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            return objectMapper.readValue(json, new TypeReference<List<SocialMediaLink>>() {});
        } catch (Exception e) {
            return List.of();
        }
    }

    private String generateSlug(String title) {
        return title.toLowerCase()
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-|-$", "");
    }
}
