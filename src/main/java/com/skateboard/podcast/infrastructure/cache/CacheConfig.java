package com.skateboard.podcast.infrastructure.cache;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
import org.springframework.boot.autoconfigure.cache.CacheProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext.SerializationPair;

import java.util.Collection;
import java.util.Map;

/**
 * The generated response DTOs (FeedPageResponse, PostResponse) don't implement
 * Serializable, so Spring Boot's default RedisCacheConfiguration (JDK value
 * serialization) would throw on every cache write once spring.cache.type=redis is
 * active. Defining this bean makes Spring Boot's own Redis cache autoconfiguration
 * use it in place of its default (see RedisCacheConfiguration#determineConfiguration
 * in spring-boot-autoconfigure), swapping in the app's own Jackson ObjectMapper for
 * value serialization while still honoring the TTL/prefix settings from
 * spring.cache.redis.* (application-railway.yml).
 * <p>
 * Cache reads go through {@code GenericJackson2JsonRedisSerializer.deserialize(byte[])}
 * with a target type of {@code Object.class} — Spring Cache is type-erased, so without
 * default typing on the mapper this comes back as a raw LinkedHashMap and the
 * {@code @Cacheable} proxy throws a ClassCastException trying to return it as
 * FeedPageResponse/PostResponse. Default typing is activated on a *copy* of the shared
 * ObjectMapper (never the injected bean itself) so the "@class" hints it embeds don't
 * leak into the public REST JSON responses, and scoped to our own DTOs plus
 * Collection/Map rather than left wide open.
 */
@Configuration
public class CacheConfig {

    @Bean
    RedisCacheConfiguration redisCacheConfiguration(CacheProperties cacheProperties, ObjectMapper objectMapper) {
        CacheProperties.Redis redisProperties = cacheProperties.getRedis();

        ObjectMapper cacheObjectMapper = objectMapper.copy();
        cacheObjectMapper.activateDefaultTyping(
                BasicPolymorphicTypeValidator.builder()
                        .allowIfSubType("com.skateboard.application.dto.")
                        .allowIfSubType(Collection.class)
                        .allowIfSubType(Map.class)
                        .build(),
                ObjectMapper.DefaultTyping.NON_FINAL,
                JsonTypeInfo.As.PROPERTY);

        RedisCacheConfiguration configuration = RedisCacheConfiguration.defaultCacheConfig()
                .serializeValuesWith(SerializationPair.fromSerializer(new GenericJackson2JsonRedisSerializer(cacheObjectMapper)));

        if (redisProperties.getTimeToLive() != null) {
            configuration = configuration.entryTtl(redisProperties.getTimeToLive());
        }
        if (redisProperties.getKeyPrefix() != null) {
            configuration = configuration.prefixCacheNameWith(redisProperties.getKeyPrefix());
        }
        if (!redisProperties.isCacheNullValues()) {
            configuration = configuration.disableCachingNullValues();
        }
        if (!redisProperties.isUseKeyPrefix()) {
            configuration = configuration.disableKeyPrefix();
        }
        return configuration;
    }
}
