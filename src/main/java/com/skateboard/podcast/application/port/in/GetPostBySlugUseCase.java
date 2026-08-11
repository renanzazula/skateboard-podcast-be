package com.skateboard.podcast.application.port.in;

import com.skateboard.podcast.domain.model.Post;

import java.util.Optional;

public interface GetPostBySlugUseCase {
    Optional<Post> execute(String slug);
}
