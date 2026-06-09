package com.throttle4j.spring.autoconfigure;

import com.throttle4j.algorithm.DefaultRateLimiterFactory;
import com.throttle4j.core.RateLimiterFactory;
import com.throttle4j.core.RateLimiterRegistry;
import com.throttle4j.spring.aop.RateLimitAspect;
import com.throttle4j.store.InMemoryStore;
import com.throttle4j.store.RateLimitStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.lang.reflect.Constructor;

/**
 * Core auto-configuration for throttle4j.
 *
 * <p>Registers the default {@link RateLimitStore}, {@link RateLimiterFactory},
 * {@link RateLimiterRegistry} and the {@link RateLimitAspect}. All beans are
 * guarded by {@link ConditionalOnMissingBean} so applications can override any
 * piece individually.</p>
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "throttle4j", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(Throttle4jProperties.class)
public class Throttle4jAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(Throttle4jAutoConfiguration.class);

    private static final String REDIS_STORE_CLASS = "com.throttle4j.redis.RedisRateLimitStore";

    /**
     * Create the default {@link RateLimitStore}. When
     * {@link Throttle4jProperties#getStoreType()} is {@code REDIS} but the
     * Redis module is not on the classpath, this falls back to in-memory and
     * logs a warning.
     *
     * @param properties throttle4j properties
     * @return store implementation
     */
    @Bean
    @ConditionalOnMissingBean
    public RateLimitStore rateLimitStore(Throttle4jProperties properties) {
        if (properties.getStoreType() == Throttle4jProperties.StoreType.REDIS) {
            RateLimitStore redisStore = tryCreateRedisStore(properties);
            if (redisStore != null) {
                return redisStore;
            }
            log.warn("throttle4j: storeType=REDIS but '{}' is not on the classpath; "
                    + "falling back to InMemoryStore.", REDIS_STORE_CLASS);
        }
        return new InMemoryStore();
    }

    /**
     * Reflectively instantiate the Redis-backed store so that this module does
     * not have a hard compile-time dependency on {@code throttle4j-redis}.
     *
     * @param properties throttle4j properties
     * @return a Redis-backed store, or {@code null} if unavailable
     */
    private RateLimitStore tryCreateRedisStore(Throttle4jProperties properties) {
        try {
            Class<?> clazz = Class.forName(REDIS_STORE_CLASS);
            Throttle4jProperties.RedisProperties redis = properties.getRedis();
            // Try (host, port, password, database, keyPrefix) first.
            try {
                Constructor<?> ctor = clazz.getConstructor(String.class, int.class, String.class,
                        int.class, String.class);
                return (RateLimitStore) ctor.newInstance(redis.getHost(), redis.getPort(),
                        redis.getPassword(), redis.getDatabase(), redis.getKeyPrefix());
            } catch (NoSuchMethodException ignored) {
                // try (host, port)
            }
            try {
                Constructor<?> ctor = clazz.getConstructor(String.class, int.class);
                return (RateLimitStore) ctor.newInstance(redis.getHost(), redis.getPort());
            } catch (NoSuchMethodException ignored) {
                // try no-arg
            }
            try {
                Constructor<?> ctor = clazz.getConstructor();
                return (RateLimitStore) ctor.newInstance();
            } catch (NoSuchMethodException ignored) {
                log.warn("throttle4j: {} found but no compatible constructor; falling back.",
                        REDIS_STORE_CLASS);
            }
            return null;
        } catch (ClassNotFoundException e) {
            return null;
        } catch (ReflectiveOperationException e) {
            log.warn("throttle4j: failed to instantiate {}: {}", REDIS_STORE_CLASS, e.getMessage());
            return null;
        }
    }

    /**
     * @param store backing store
     * @return default rate-limiter factory
     */
    @Bean
    @ConditionalOnMissingBean
    public RateLimiterFactory rateLimiterFactory(RateLimitStore store) {
        return new DefaultRateLimiterFactory(store);
    }

    /**
     * @param factory rate limiter factory
     * @return shared rate-limiter registry
     */
    @Bean
    @ConditionalOnMissingBean
    public RateLimiterRegistry rateLimiterRegistry(RateLimiterFactory factory) {
        return new RateLimiterRegistry(factory);
    }

    /**
     * @param registry   shared registry
     * @param properties throttle4j properties
     * @return AOP aspect that wires the {@code @RateLimit} annotation
     */
    @Bean
    @ConditionalOnMissingBean
    public RateLimitAspect rateLimitAspect(RateLimiterRegistry registry, Throttle4jProperties properties) {
        return new RateLimitAspect(registry, properties);
    }
}
