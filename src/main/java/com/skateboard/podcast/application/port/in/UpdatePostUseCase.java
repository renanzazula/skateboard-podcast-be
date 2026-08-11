package com.skateboard.podcast.application.port.in;

import com.skateboard.podcast.domain.model.Post;
import com.skateboard.podcast.domain.model.PostStatus;

import java.time.Instant;

public interface UpdatePostUseCase {

    record Input(String id, String title, String slug, PostStatus status,
                 Instant publishAt, String coverUrl, String blocksJson, String socialMediaLinksJson) {}

    Post execute(Input input);
}
