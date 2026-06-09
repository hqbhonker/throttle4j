package com.throttle4j.algorithm;

import com.throttle4j.core.RateLimitResult;
import com.throttle4j.core.RateLimiter;
import com.throttle4j.core.RateLimiterConfig;
import com.throttle4j.store.RateLimitStore;

import java.util.Objects;

/**
 * Base class for {@link RateLimiter} implementations that delegate state
 * management to a {@link RateLimitStore}.
 */
abstract class AbstractStoreBackedRateLimiter implements RateLimiter {

    protected final RateLimiterConfig config;
    protected final RateLimitStore store;

    protected AbstractStoreBackedRateLimiter(RateLimiterConfig config, RateLimitStore store) {
        this.config = Objects.requireNonNull(config, "config must not be null");
        this.store = Objects.requireNonNull(store, "store must not be null");
    }

    @Override
    public RateLimitResult tryAcquire(String key) {
        return tryAcquire(key, 1);
    }

    @Override
    public RateLimitResult tryAcquire(String key, int permits) {
        if (permits < 1) {
            throw new IllegalArgumentException("permits must be >= 1");
        }
        return store.tryAcquire(key, permits, config);
    }

    @Override
    public RateLimiterConfig getConfig() {
        return config;
    }
}
