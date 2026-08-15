package com.skateboard.podcast.application.port.out;

import com.skateboard.podcast.domain.model.Post;

import java.util.List;
import java.util.Optional;

public interface LoadPostPort {
    Optional<Post> findById(String id);
    Optional<Post> findBySlug(String slug);
    Optional<Post> findByYoutubeVideoId(String youtubeVideoId);
    List<Post> findPublished(int page, int size);
    long countPublished();
    List<Post> findAll(int page, int size);
    long countAll();
    boolean existsBySlug(String slug);
}
