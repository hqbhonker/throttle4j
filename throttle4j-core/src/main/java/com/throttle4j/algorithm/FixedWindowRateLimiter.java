package com.throttle4j.algorithm;

import com.throttle4j.core.RateLimiterConfig;
import com.throttle4j.store.RateLimitStore;

/**
 * Fixed window counter rate limiter.
 *
 * <p>Within each {@code windowMillis} window the request count must not exceed
 * {@code limit}. When the window ends the counter resets to zero.</p>
 */
public class FixedWindowRateLimiter extends AbstractStoreBackedRateLimiter {

    public FixedWindowRateLimiter(RateLimiterConfig config, RateLimitStore store) {
        super(config, store);
    }
}
