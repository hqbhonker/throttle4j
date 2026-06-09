package com.throttle4j.algorithm;

import com.throttle4j.core.RateLimiterConfig;
import com.throttle4j.store.RateLimitStore;

/**
 * Leaky bucket rate limiter — enforces a steady output rate of
 * {@code limit / windowMillis} requests per millisecond.
 */
public class LeakyBucketRateLimiter extends AbstractStoreBackedRateLimiter {

    public LeakyBucketRateLimiter(RateLimiterConfig config, RateLimitStore store) {
        super(config, store);
    }
}
