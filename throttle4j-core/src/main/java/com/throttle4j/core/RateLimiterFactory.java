package com.throttle4j.core;

/**
 * Factory abstraction that produces {@link RateLimiter} instances from a config.
 */
public interface RateLimiterFactory {

    /**
     * Create a new {@link RateLimiter} based on the given configuration.
     *
     * @param config configuration describing the algorithm, limit, window, etc.
     * @return a new {@link RateLimiter} instance
     */
    RateLimiter create(RateLimiterConfig config);
}
