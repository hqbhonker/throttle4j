package com.throttle4j.core;

/**
 * Core rate limiter contract.
 *
 * <p>Implementations are required to be thread-safe.</p>
 */
public interface RateLimiter {

    /**
     * Try to acquire a single permit for the given key.
     *
     * @param key resource identifier (e.g. user id, api path)
     * @return result describing whether the request is allowed
     */
    RateLimitResult tryAcquire(String key);

    /**
     * Try to acquire {@code permits} permits for the given key.
     *
     * @param key     resource identifier
     * @param permits number of permits to acquire (must be {@code >= 1})
     * @return result describing whether the request is allowed
     */
    RateLimitResult tryAcquire(String key, int permits);

    /**
     * @return the immutable configuration backing this limiter
     */
    RateLimiterConfig getConfig();
}
