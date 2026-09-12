package com.skateboard.podcast.adapter.out.persistence;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Plain getter/setter coverage for the {@code category} table entity —
 * CategoryPersistenceAdapter's toDomain/toEntity mapping is what actually
 * exercises these in production, but this pins down each field individually
 * (including the {@code isDefault}/{@code setDefault} naming mismatch) so a
 * future field rename or forgotten setter shows up here directly.
 */
class CategoryJpaEntityTest {

    @Test
    void gettersReturnValuesSetBySetters() {
        CategoryJpaEntity entity = new CategoryJpaEntity();
        UUID id = UUID.randomUUID();
        Instant createdAt = Instant.parse("2024-01-01T00:00:00Z");
        Instant updatedAt = Instant.parse("2024-01-02T00:00:00Z");

        entity.setId(id);
        entity.setSlug("skate-talk");
        entity.setName("Skate Talk");
        entity.setCustomName("Custom Skate Talk");
        entity.setDescription("A podcast about skating");
        entity.setCoverUrl("https://example.com/cover.png");
        entity.setSource("YOUTUBE");
        entity.setExternalId("PL123");
        entity.setEnabled(true);
        entity.setDisplayOrder(3);
        entity.setDefault(true);
        entity.setDefaultLocked(true);
        entity.setCreatedAt(createdAt);
        entity.setUpdatedAt(updatedAt);

        assertThat(entity.getId()).isEqualTo(id);
        assertThat(entity.getSlug()).isEqualTo("skate-talk");
        assertThat(entity.getName()).isEqualTo("Skate Talk");
        assertThat(entity.getCustomName()).isEqualTo("Custom Skate Talk");
        assertThat(entity.getDescription()).isEqualTo("A podcast about skating");
        assertThat(entity.getCoverUrl()).isEqualTo("https://example.com/cover.png");
        assertThat(entity.getSource()).isEqualTo("YOUTUBE");
        assertThat(entity.getExternalId()).isEqualTo("PL123");
        assertThat(entity.isEnabled()).isTrue();
        assertThat(entity.getDisplayOrder()).isEqualTo(3);
        assertThat(entity.isDefault()).isTrue();
        assertThat(entity.isDefaultLocked()).isTrue();
        assertThat(entity.getCreatedAt()).isEqualTo(createdAt);
        assertThat(entity.getUpdatedAt()).isEqualTo(updatedAt);
    }

    @Test
    void booleanAndNullableFieldsDefaultBeforeAnySetterIsCalled() {
        CategoryJpaEntity entity = new CategoryJpaEntity();

        assertThat(entity.getId()).isNull();
        assertThat(entity.getCustomName()).isNull();
        assertThat(entity.getDisplayOrder()).isNull();
        assertThat(entity.isEnabled()).isFalse();
        assertThat(entity.isDefault()).isFalse();
        assertThat(entity.isDefaultLocked()).isFalse();
    }

    @Test
    void enabledAndDefaultFlagsCanBeToggledIndependently() {
        CategoryJpaEntity entity = new CategoryJpaEntity();

        entity.setEnabled(true);
        entity.setDefault(false);
        entity.setDefaultLocked(false);

        assertThat(entity.isEnabled()).isTrue();
        assertThat(entity.isDefault()).isFalse();
        assertThat(entity.isDefaultLocked()).isFalse();

        entity.setEnabled(false);
        entity.setDefault(true);

        assertThat(entity.isEnabled()).isFalse();
        assertThat(entity.isDefault()).isTrue();
    }
}
