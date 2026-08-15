package com.skateboard.podcast.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

import java.util.UUID;

@Entity
@Table(name = "post_category")
@IdClass(PostCategoryId.class)
public class PostCategoryJpaEntity {

    @Id
    @Column(name = "post_id")
    private UUID postId;

    @Id
    @Column(name = "category_id")
    private UUID categoryId;

    public PostCategoryJpaEntity() {}

    public PostCategoryJpaEntity(UUID postId, UUID categoryId) {
        this.postId = postId;
        this.categoryId = categoryId;
    }

    public UUID getPostId()     { return postId; }
    public UUID getCategoryId() { return categoryId; }
}
