package com.throttle4j.core;

/**
 * Enumeration of supported rate limiting algorithms.
 */
public enum Algorithm {
    /** Fixed window counter algorithm. */
    FIXED_WINDOW,
    /** Sliding window counter algorithm. */
    SLIDING_WINDOW,
    /** Token bucket algorithm. */
    TOKEN_BUCKET,
    /** Leaky bucket algorithm. */
    LEAKY_BUCKET
}
