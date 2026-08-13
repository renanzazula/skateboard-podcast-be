package com.skateboard.podcast.infrastructure.cache;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.skateboard.application.dto.FeedPageResponse;
import com.skateboard.application.dto.PostResponse;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.cache.CacheProperties;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.serializer.RedisSerializationContext.SerializationPair;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The generated response DTOs aren't Serializable, so Redis caching only works if
 * CacheConfig's value serializer is JSON-based rather than Spring's JDK default.
 * This round-trips real DTOs through it without needing a live Redis server.
 */
class CacheConfigTest {

    private final CacheConfig cacheConfig = new CacheConfig();

    @Test
    void appliesConfiguredTtl() {
        CacheProperties cacheProperties = new CacheProperties();
        cacheProperties.getRedis().setTimeToLive(Duration.ofHours(24));

        RedisCacheConfiguration configuration = cacheConfig.redisCacheConfiguration(cacheProperties, new ObjectMapper());

        assertThat(configuration.getTtl()).isEqualTo(Duration.ofHours(24));
    }

    @Test
    void valueSerializerRoundTripsFeedPageResponse() {
        FeedPageResponse original = new FeedPageResponse()
                .posts(List.of())
                .total(3L)
                .page(0)
                .size(10);

        FeedPageResponse roundTripped = (FeedPageResponse) roundTrip(original);

        assertThat(roundTripped.getTotal()).isEqualTo(3L);
        assertThat(roundTripped.getPage()).isEqualTo(0);
        assertThat(roundTripped.getSize()).isEqualTo(10);
    }

    @Test
    void valueSerializerRoundTripsPostResponse() {
        UUID id = UUID.randomUUID();
        PostResponse original = new PostResponse()
                .id(id)
                .slug("ep-1")
                .title("Episode 1")
                .status(PostResponse.StatusEnum.PUBLISHED)
                .publishAt(OffsetDateTime.parse("2026-01-01T00:00:00Z"))
                .createdAt(OffsetDateTime.parse("2026-01-01T00:00:00Z"))
                .updatedAt(OffsetDateTime.parse("2026-01-01T00:00:00Z"));

        PostResponse roundTripped = (PostResponse) roundTrip(original);

        assertThat(roundTripped.getId()).isEqualTo(id);
        assertThat(roundTripped.getSlug()).isEqualTo("ep-1");
        assertThat(roundTripped.getTitle()).isEqualTo("Episode 1");
        assertThat(roundTripped.getStatus()).isEqualTo(PostResponse.StatusEnum.PUBLISHED);
    }

    private Object roundTrip(Object value) {
        CacheProperties cacheProperties = new CacheProperties();
        // findAndRegisterModules() mirrors what Spring Boot's autoconfigured ObjectMapper
        // bean does (registers JavaTimeModule etc.) so this matches production behavior.
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        RedisCacheConfiguration configuration = cacheConfig.redisCacheConfiguration(cacheProperties, objectMapper);
        SerializationPair<Object> serializer = configuration.getValueSerializationPair();
        return serializer.read(serializer.write(value));
    }
}
