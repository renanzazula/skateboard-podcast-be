package com.skateboard.podcast.application.port.in;

import com.skateboard.podcast.domain.model.Post;

import java.util.List;

public interface GetPostsByCategoryUseCase {

    record Result(List<Post> posts, long total) {}

    /** @throws com.skateboard.podcast.domain.exception.CategoryNotFoundException if the slug doesn't match an enabled category. */
    Result execute(String slug, int page, int size);
}
