package com.skateboard.podcast.application.port.in;

import com.skateboard.podcast.domain.model.Post;

import java.util.List;

public interface GetPostUseCase {

    record Result(List<Post> posts, long total) {}

    Result execute(String search, int page, int size);
}
