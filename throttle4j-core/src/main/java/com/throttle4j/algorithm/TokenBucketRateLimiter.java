package com.throttle4j.algorithm;

import com.throttle4j.core.RateLimiterConfig;
import com.throttle4j.store.RateLimitStore;

/**
 * Token bucket rate limiter using lazy refill: tokens are computed from elapsed
 * time on each {@code tryAcquire} call rather than via a background scheduler.
 */
public class TokenBucketRateLimiter extends AbstractStoreBackedRateLimiter {

    public TokenBucketRateLimiter(RateLimiterConfig config, RateLimitStore store) {
        super(config, store);
    }
}
