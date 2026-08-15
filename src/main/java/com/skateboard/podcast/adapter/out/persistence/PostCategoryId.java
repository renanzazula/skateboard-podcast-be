package com.skateboard.podcast.adapter.out.persistence;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

public class PostCategoryId implements Serializable {

    private UUID postId;
    private UUID categoryId;

    public PostCategoryId() {}

    public PostCategoryId(UUID postId, UUID categoryId) {
        this.postId = postId;
        this.categoryId = categoryId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof PostCategoryId that)) return false;
        return Objects.equals(postId, that.postId) && Objects.equals(categoryId, that.categoryId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(postId, categoryId);
    }
}
