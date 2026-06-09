package com.throttle4j.store;

import com.throttle4j.core.RateLimitResult;
import com.throttle4j.core.RateLimiterConfig;

/**
 * Storage abstraction for rate limiting state.
 *
 * <p>An implementation owns the algorithmic logic (windowing, token bucket,
 * leaky bucket) — the {@code RateLimiter} layer simply delegates to a store
 * with the configured algorithm.</p>
 *
 * <p>All implementations must be thread-safe.</p>
 */
public interface RateLimitStore {

    /**
     * Try to acquire {@code permits} permits for {@code key} under the given
     * configuration.
     *
     * @param key     resource key
     * @param permits number of permits to consume
     * @param config  limiter configuration
     * @return the result of the attempt
     */
    RateLimitResult tryAcquire(String key, int permits, RateLimiterConfig config);

    /**
     * Reset state for the given key.
     *
     * @param key resource key
     */
    void reset(String key);
}
