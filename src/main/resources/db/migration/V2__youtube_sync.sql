ALTER TABLE posts ADD COLUMN youtube_video_id VARCHAR(20);
ALTER TABLE posts ADD COLUMN description       TEXT;
ALTER TABLE posts ADD COLUMN duration_seconds  INTEGER;
ALTER TABLE posts ADD COLUMN episode_number    INTEGER;

-- Partial index: manually-authored posts leave this NULL and must not collide.
CREATE UNIQUE INDEX uq_posts_youtube_video_id ON posts (youtube_video_id) WHERE youtube_video_id IS NOT NULL;
