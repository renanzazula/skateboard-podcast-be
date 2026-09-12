package com.skateboard.podcast.infrastructure.youtube;

import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.provider.redis.spring.RedisLockProvider;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisConnectionFactory;

import java.lang.reflect.Field;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Only active where Redis is actually configured (the "railway"/production
 * profile). This is a plain unit test of the @Bean method itself, not a
 * Spring context test — the @ConditionalOnProperty gate is Spring wiring
 * behaviour, not logic in this class.
 */
class YoutubeSchedulerLockConfigTest {

    private final YoutubeSchedulerLockConfig config = new YoutubeSchedulerLockConfig();

    @Test
    void buildsARedisBackedLockProvider() {
        RedisConnectionFactory connectionFactory = mock(RedisConnectionFactory.class);

        LockProvider lockProvider = config.lockProvider(connectionFactory);

        assertThat(lockProvider).isInstanceOf(RedisLockProvider.class);
    }

    /**
     * The (RedisConnectionFactory, String) constructor threads its string
     * argument through as ShedLock's "environment", namespacing lock keys so
     * this service can share a Redis instance with other services' locks
     * without collisions.
     */
    @Test
    void namespacesLockKeysToThisService() throws NoSuchFieldException, IllegalAccessException {
        RedisConnectionFactory connectionFactory = mock(RedisConnectionFactory.class);

        LockProvider lockProvider = config.lockProvider(connectionFactory);

        Field environmentField = RedisLockProvider.class.getDeclaredField("environment");
        environmentField.setAccessible(true);
        assertThat(environmentField.get(lockProvider)).isEqualTo("skateboard-podcast");
    }

    @Test
    void buildsAFreshProviderInstanceEachCall() {
        RedisConnectionFactory connectionFactory = mock(RedisConnectionFactory.class);

        LockProvider first = config.lockProvider(connectionFactory);
        LockProvider second = config.lockProvider(connectionFactory);

        assertThat(first).isNotSameAs(second);
    }
}
