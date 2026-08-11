package com.skateboard.podcast.application.port.out;

import com.skateboard.podcast.domain.model.Post;

public interface SavePostPort {
    Post save(Post post);
    void deleteById(String id);
}
