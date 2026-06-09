package com.throttle4j.algorithm;

import com.throttle4j.core.RateLimiter;
import com.throttle4j.core.RateLimiterConfig;
import com.throttle4j.core.RateLimiterFactory;
import com.throttle4j.store.RateLimitStore;

import java.util.Objects;

/**
 * Default {@link RateLimiterFactory} that creates the algorithm-specific
 * limiter wrapper around a shared {@link RateLimitStore}.
 */
public class DefaultRateLimiterFactory implements RateLimiterFactory {

    private final RateLimitStore store;

    public DefaultRateLimiterFactory(RateLimitStore store) {
        this.store = Objects.requireNonNull(store, "store must not be null");
    }

    @Override
    public RateLimiter create(RateLimiterConfig config) {
        Objects.requireNonNull(config, "config must not be null");
        switch (config.getAlgorithm()) {
            case FIXED_WINDOW:
                return new FixedWindowRateLimiter(config, store);
            case SLIDING_WINDOW:
                return new SlidingWindowRateLimiter(config, store);
            case TOKEN_BUCKET:
                return new TokenBucketRateLimiter(config, store);
            case LEAKY_BUCKET:
                return new LeakyBucketRateLimiter(config, store);
            default:
                throw new IllegalArgumentException("Unsupported algorithm: " + config.getAlgorithm());
        }
    }
}
