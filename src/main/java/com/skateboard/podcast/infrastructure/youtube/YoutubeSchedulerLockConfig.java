package com.skateboard.podcast.infrastructure.youtube;

import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.provider.redis.spring.RedisLockProvider;
import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;

/**
 * Only active where Redis is actually configured (the "railway"/production
 * profile — see application-railway.yml). The default/local profile runs a
 * single instance with no Redis (CacheConfig's own comment says the same),
 * so {@code @SchedulerLock} on YoutubeSyncJob is a harmless no-op there
 * rather than a hard startup dependency on Redis being reachable.
 */
@Configuration
@EnableSchedulerLock(defaultLockAtMostFor = "10m")
@ConditionalOnProperty(prefix = "spring.data.redis", name = "url")
public class YoutubeSchedulerLockConfig {

    @Bean
    public LockProvider lockProvider(RedisConnectionFactory connectionFactory) {
        return new RedisLockProvider(connectionFactory, "skateboard-podcast");
    }
}
