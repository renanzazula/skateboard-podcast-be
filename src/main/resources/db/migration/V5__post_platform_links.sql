-- One row per (episode, distribution platform) link (see .docs/README_SPOTIFY_YOUTUBE_PODCAST_INTEGRATION.md).
-- An episode has at most one link per platform (uk_post_platform); a given
-- external id on a platform maps to at most one episode (uk_post_platform_link).
CREATE TABLE post_platform_link (
    id UUID PRIMARY KEY,
    post_id UUID NOT NULL REFERENCES posts(id),
    platform VARCHAR(20) NOT NULL,
    external_id VARCHAR(255) NOT NULL,
    external_url TEXT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,

    CONSTRAINT uk_post_platform_link UNIQUE (platform, external_id),
    CONSTRAINT uk_post_platform UNIQUE (post_id, platform)
);

CREATE INDEX idx_post_platform_link_post_id ON post_platform_link (post_id);

-- Backfill YOUTUBE links from the existing posts.youtube_video_id column.
-- That column stays in place (still the sync's dedup key); this table becomes
-- the source PodcastService reads platform links from.
INSERT INTO post_platform_link (id, post_id, platform, external_id, external_url, created_at, updated_at)
SELECT gen_random_uuid(), id, 'YOUTUBE', youtube_video_id,
       'https://www.youtube.com/watch?v=' || youtube_video_id, created_at, updated_at
FROM posts
WHERE youtube_video_id IS NOT NULL;
