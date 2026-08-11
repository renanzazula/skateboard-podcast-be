CREATE TABLE posts (
    id                       UUID        PRIMARY KEY,
    slug                     VARCHAR(500) NOT NULL UNIQUE,
    title                    VARCHAR(1000) NOT NULL,
    status                   VARCHAR(20)  NOT NULL,
    publish_at               TIMESTAMPTZ,
    cover_url                TEXT,
    blocks_json              TEXT         NOT NULL DEFAULT '[]',
    social_media_links_json  TEXT         NOT NULL DEFAULT '[]',
    created_at               TIMESTAMPTZ  NOT NULL,
    updated_at               TIMESTAMPTZ  NOT NULL,
    created_by               UUID
);

CREATE INDEX idx_posts_status     ON posts (status);
CREATE INDEX idx_posts_created_at ON posts (created_at DESC);
