package com.throttle4j.algorithm;

import com.throttle4j.core.RateLimiterConfig;
import com.throttle4j.store.RateLimitStore;

/**
 * Sliding window counter rate limiter using sub-slots (default 10) for
 * approximation of the precise sliding window.
 */
public class SlidingWindowRateLimiter extends AbstractStoreBackedRateLimiter {

    public SlidingWindowRateLimiter(RateLimiterConfig config, RateLimitStore store) {
        super(config, store);
    }
}
