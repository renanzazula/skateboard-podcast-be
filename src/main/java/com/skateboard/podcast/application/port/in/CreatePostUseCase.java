package com.skateboard.podcast.application.port.in;

import com.skateboard.podcast.domain.model.Post;
import com.skateboard.podcast.domain.model.PostStatus;

import java.time.Instant;
import java.util.UUID;

public interface CreatePostUseCase {

    record Input(String title, String slug, PostStatus status, Instant publishAt,
                 String coverUrl, Integer coverWidth, Integer coverHeight,
                 String blocksJson, String socialMediaLinksJson, UUID createdBy,
                 String youtubeVideoId, String description, Integer durationSeconds, Integer episodeNumber) {

        /** Manual-authoring convenience constructor — youtube/cover-size fields stay null. */
        public Input(String title, String slug, PostStatus status, Instant publishAt,
                      String coverUrl, String blocksJson, String socialMediaLinksJson, UUID createdBy) {
            this(title, slug, status, publishAt, coverUrl, null, null, blocksJson, socialMediaLinksJson, createdBy,
                    null, null, null, null);
        }

        /** Sync convenience constructor for callers without cover dimensions. */
        public Input(String title, String slug, PostStatus status, Instant publishAt,
                      String coverUrl, String blocksJson, String socialMediaLinksJson, UUID createdBy,
                      String youtubeVideoId, String description, Integer durationSeconds, Integer episodeNumber) {
            this(title, slug, status, publishAt, coverUrl, null, null, blocksJson, socialMediaLinksJson, createdBy,
                    youtubeVideoId, description, durationSeconds, episodeNumber);
        }
    }

    Post execute(Input input);
}
