package com.skateboard.podcast.adapter.out.persistence;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link PostCategoryJpaEntity} is the join-row entity for post_category,
 * keyed by the embeddable {@link PostCategoryId} — CategoryPersistenceAdapter
 * only ever builds it via the all-args constructor (addAssociation), so that
 * is the path that matters most here.
 */
class PostCategoryJpaEntityTest {

    @Test
    void allArgsConstructorExposesBothIdsViaGetters() {
        UUID postId = UUID.randomUUID();
        UUID categoryId = UUID.randomUUID();

        PostCategoryJpaEntity entity = new PostCategoryJpaEntity(postId, categoryId);

        assertThat(entity.getPostId()).isEqualTo(postId);
        assertThat(entity.getCategoryId()).isEqualTo(categoryId);
    }

    @Test
    void noArgsConstructorLeavesFieldsNullForJpa() {
        PostCategoryJpaEntity entity = new PostCategoryJpaEntity();

        assertThat(entity.getPostId()).isNull();
        assertThat(entity.getCategoryId()).isNull();
    }
}
