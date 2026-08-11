package com.skateboard.podcast.adapter.in.rest;


import com.skateboard.application.dto.*;
import com.skateboard.infrastructure.web.api.PodcastApi;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

@RestController
public class PodcastController implements PodcastApi {

    private final PodcastService podcastService;

    public PodcastController(PodcastService podcastService) {
        this.podcastService = podcastService;
    }

    @Override
    @PreAuthorize("hasAuthority('FUNC_TAB_PODCAST')")
    public ResponseEntity<FeedPageResponse> getPodcastFeed(Integer page, Integer size) {
        // Clamp before the service call so the cache-key space is bounded.
        int p = page != null ? page : 0;
        int s = size != null ? Math.min(size, 50) : 10;
        return ResponseEntity.ok(podcastService.getPost(p, s));
    }

    @Override
    @PreAuthorize("hasAuthority('FUNC_TAB_PODCAST')")
    public ResponseEntity<PostResponse> getPodcastPostBySlug(String slug) {
        PostResponse response = podcastService.getPostBySlug(slug);
        if (response == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Post not found");
        }
        return ResponseEntity.ok(response);
    }

    @Override
    @PreAuthorize("hasAuthority('FUNC_PODCAST_CREATE_POST')")
    public ResponseEntity<PostResponse> createPodcastPost(CreatePostRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(podcastService.createPost(req, resolveCurrentUserId()));
    }

    @Override
    @PreAuthorize("hasAuthority('FUNC_PODCAST_EDIT_POST')")
    public ResponseEntity<PostResponse> updatePodcastPost(UUID id, UpdatePostRequest req) {
        return ResponseEntity.ok(podcastService.updatePost(id, req));
    }

    @Override
    @PreAuthorize("hasAuthority('FUNC_PODCAST_DELETE_POST')")
    public ResponseEntity<Void> deletePodcastPost(UUID id) {
        podcastService.deletePost(id);
        return ResponseEntity.noContent().build();
    }

    @Override
    @PreAuthorize("hasAuthority('FUNC_PODCAST_IMPORT_JSON')")
    public ResponseEntity<ImportResult> importPodcastPosts(ImportPostsRequest req) {
        return ResponseEntity.ok(podcastService.importPosts(req, resolveCurrentUserId()));
    }

    private UUID resolveCurrentUserId() {
        try {
            String name = SecurityContextHolder.getContext().getAuthentication().getName();
            return UUID.fromString(name);
        } catch (Exception e) {
            return null;
        }
    }
}
