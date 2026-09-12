package com.skateboard.podcast.adapter.out.persistence;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link PostCategoryId} is the {@code @IdClass} for {@link PostCategoryJpaEntity}
 * (composite key over post_category) — JPA relies on its equals/hashCode contract
 * to look up and cache entities by composite id, so these are the behaviors that
 * actually matter here, not just executing the lines.
 */
class PostCategoryIdTest {

    @Test
    void noArgsConstructorProducesInstancesEqualToEachOther() {
        PostCategoryId a = new PostCategoryId();
        PostCategoryId b = new PostCategoryId();

        assertThat(a).isEqualTo(b);
        assertThat(a.hashCode()).isEqualTo(b.hashCode());
    }

    @Test
    void allArgsConstructorWithSameIdsAreEqualAndHaveSameHashCode() {
        UUID postId = UUID.randomUUID();
        UUID categoryId = UUID.randomUUID();

        PostCategoryId a = new PostCategoryId(postId, categoryId);
        PostCategoryId b = new PostCategoryId(postId, categoryId);

        assertThat(a).isEqualTo(b);
        assertThat(a.hashCode()).isEqualTo(b.hashCode());
    }

    @Test
    void isReflexive() {
        PostCategoryId a = new PostCategoryId(UUID.randomUUID(), UUID.randomUUID());

        assertThat(a).isEqualTo(a);
    }

    @Test
    void differsWhenPostIdDiffers() {
        UUID categoryId = UUID.randomUUID();
        PostCategoryId a = new PostCategoryId(UUID.randomUUID(), categoryId);
        PostCategoryId b = new PostCategoryId(UUID.randomUUID(), categoryId);

        assertThat(a).isNotEqualTo(b);
    }

    @Test
    void differsWhenCategoryIdDiffers() {
        UUID postId = UUID.randomUUID();
        PostCategoryId a = new PostCategoryId(postId, UUID.randomUUID());
        PostCategoryId b = new PostCategoryId(postId, UUID.randomUUID());

        assertThat(a).isNotEqualTo(b);
    }

    @Test
    void isNotEqualToNullOrADifferentType() {
        PostCategoryId a = new PostCategoryId(UUID.randomUUID(), UUID.randomUUID());

        assertThat(a).isNotEqualTo(null);
        assertThat(a).isNotEqualTo("not-a-post-category-id");
    }
}
