CREATE TABLE category (
    id             UUID PRIMARY KEY,
    slug           VARCHAR(150)  NOT NULL UNIQUE,
    name           VARCHAR(255)  NOT NULL,
    description    TEXT,
    cover_url      TEXT,
    source         VARCHAR(50)   NOT NULL DEFAULT 'YOUTUBE',
    external_id    VARCHAR(255),
    enabled        BOOLEAN       NOT NULL DEFAULT TRUE,
    display_order  INTEGER,
    is_default     BOOLEAN       NOT NULL DEFAULT FALSE,
    created_at     TIMESTAMPTZ   NOT NULL,
    updated_at     TIMESTAMPTZ   NOT NULL,
    CONSTRAINT uk_category_source_external_id UNIQUE (source, external_id)
);

CREATE INDEX idx_category_enabled ON category (enabled);

CREATE TABLE post_category (
    post_id     UUID NOT NULL,
    category_id UUID NOT NULL,

    PRIMARY KEY (post_id, category_id),

    CONSTRAINT fk_post_category_post
        FOREIGN KEY (post_id)
        REFERENCES posts(id)
        ON DELETE CASCADE,

    CONSTRAINT fk_post_category_category
        FOREIGN KEY (category_id)
        REFERENCES category(id)
        ON DELETE CASCADE
);

CREATE INDEX idx_post_category_category ON post_category (category_id);
